package com.example.joywithsollor

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.widget.ImageButton
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainFragment : Fragment() {

    private var geckoView: GeckoView? = null
    private var urlInput: EditText? = null
    private var urlBar: View? = null
    private var navBar: View? = null
    private var controlBar: View? = null
    private var backButton: ImageButton? = null
    private var tabsButton: ImageButton? = null
    private var addTabButton: ImageButton? = null
    private var scrubberButton: ImageButton? = null
    private var reloadButton: ImageButton? = null
    private var barsToggleButton: ImageButton? = null
    private var barsRestoreButton: ImageButton? = null

    private var controlPlay: ImageButton? = null
    private var controlRewind: ImageButton? = null
    private var controlForward: ImageButton? = null
    private var controlMute: ImageButton? = null
    private var controlFullscreen: ImageButton? = null
    private var controlFit: ImageButton? = null
    private var scrubberView: ScrubberView? = null
    private var videoProgress: VideoProgressView? = null
    private var progressOverlay: View? = null
    private var scrubberOverlay: View? = null
    private var fsOverlay: View? = null
    private var fsScrim: View? = null
    private var fsControlBar: View? = null

    private lateinit var viewModel: TabsViewModel
    private var boundSession: GeckoSession? = null
    private var backCallback: OnBackPressedCallback? = null

    private var isPageFullscreen = false
    private var fsEnteredAt: Long = 0L
    private var isBarsHidden = false
    private var videoPaused: Boolean? = null
    private var videoMuted: Boolean? = null

    // Оптимистичное состояние: иконка меняется сразу, а подтверждение
    // от плеера ждём до 1.2 с, чтобы не мигала на рассинхроне.
    private var pendingPaused: Boolean? = null
    private var pendingMuted: Boolean? = null
    private var pendingUntil: Long = 0L

    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    companion object {
        private const val TAG = "Sollor"
        private const val KEY_HIDDEN = "sollor_bars_hidden"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isBarsHidden = savedInstanceState?.getBoolean(KEY_HIDDEN, false) ?: false
        (activity as? MainActivity)?.onVideoError = { msg ->
            SLog.w(TAG, "видео: " + msg)   // без системных уведомлений
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HIDDEN, isBarsHidden)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(TabsViewModel::class.java)
        inflateContent(view as FrameLayout)

        view?.post(scrubberWatchdog)
        viewModel.activeIndex.observe(viewLifecycleOwner) { bind(it, false) }
        viewModel.closeTick.observe(viewLifecycleOwner) { bind(viewModel.activeIndex.value ?: 0, true) }
    }

    override fun onDestroyView() {
        view?.removeCallbacks(scrubberWatchdog)
        (activity as? MainActivity)?.onVideoError = null
        backCallback?.remove()
        backCallback = null
        boundSession = null
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val root = view as? FrameLayout ?: return
        inflateContent(root)
        bind(viewModel.activeIndex.value ?: 0, true)
    }

    private fun inflateContent(root: FrameLayout) {
        root.removeAllViews()
        val content = layoutInflater.inflate(R.layout.fragment_main, root, false)
        root.addView(content)

        geckoView = content.findViewById(R.id.geckoView)
        // Скругления веб-контейнера: без тени и elevation
        geckoView?.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, 24f)
            }
        }
        geckoView?.clipToOutline = true
        geckoView?.elevation = 0f
        urlInput = content.findViewById(R.id.urlInput)
        urlBar = content.findViewById(R.id.urlBar)
        navBar = content.findViewById(R.id.navBar)
        controlBar = content.findViewById(R.id.controlBar)
        backButton = content.findViewById(R.id.backButton)
        tabsButton = content.findViewById(R.id.tabsButton)
        addTabButton = content.findViewById(R.id.addTabButton)
        scrubberButton = content.findViewById(R.id.scrubberSideButton)
        reloadButton = content.findViewById(R.id.reloadScriptsButton)
        barsToggleButton = content.findViewById(R.id.barsToggleButton)
        barsRestoreButton = content.findViewById(R.id.barsRestoreButton)

        controlPlay = content.findViewById(R.id.controlPlay)
        controlRewind = content.findViewById(R.id.controlRewind)
        controlForward = content.findViewById(R.id.controlForward)
        controlMute = content.findViewById(R.id.controlMute)
        controlFullscreen = content.findViewById(R.id.controlFullscreen)
        controlFit = content.findViewById(R.id.controlFit)
        scrubberView = content.findViewById(R.id.scrubberView)
        videoProgress = content.findViewById(R.id.videoProgress)
        progressOverlay = content.findViewById(R.id.progressOverlay)
        (content as? android.view.ViewGroup)?.clipChildren = false
        scrubberOverlay = content.findViewById(R.id.scrubberOverlay)
        (scrubberOverlay as? android.view.ViewGroup)?.clipChildren = false
        fsOverlay = content.findViewById(R.id.fsOverlay)
        fsScrim = content.findViewById(R.id.fsScrim)
        fsControlBar = content.findViewById(R.id.fsControlBar)
        content.findViewById<ImageButton>(R.id.fsPlay)?.setOnClickListener { sendVideo("play-pause") }
        content.findViewById<ImageButton>(R.id.fsForward)?.setOnClickListener { sendVideo("forward") }
        content.findViewById<ImageButton>(R.id.fsRewind)?.setOnClickListener { sendVideo("rewind") }
        content.findViewById<ImageButton>(R.id.fsMute)?.setOnClickListener { sendVideo("mute") }
        content.findViewById<ImageButton>(R.id.fsFit)?.setOnClickListener { sendVideo("cover") }
        content.findViewById<ImageButton>(R.id.fsFullscreen)?.setOnClickListener { sendVideo("fullscreen") }

        backButton?.setOnClickListener {
            (requireActivity() as? MainActivity)?.goBackInActiveTab()
            view?.postDelayed({ updateBack() }, 150)
        }
        addTabButton?.setOnClickListener {
            forcePortraitIfFsLandscape()
            (requireActivity() as? MainActivity)?.createNewTab()
        }
        tabsButton?.setOnClickListener {
            forcePortraitIfFsLandscape()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, TabsFragment())
                .addToBackStack(null)
                .commit()
        }
        scrubberButton?.setOnClickListener {
            val sv = scrubberView ?: return@setOnClickListener
            val newSideLeft = !sv.sideLeft
            sv.sideLeft = newSideLeft
            prefs.edit().putBoolean("scrubber_left", newSideLeft).apply()
            applyBars()
            applyFsOverlay()
        applyControlTint()
            // сообщаем странице, чтобы её скраббер (если включён) тоже переехал
            (requireActivity() as? MainActivity)?.toggleScrubberSide()
        }
        reloadButton?.setOnLongClickListener {
            val activity = requireActivity() as? MainActivity
            val idx = viewModel.activeIndex.value ?: 0
            if (activity != null && viewModel.recreateTab(idx, activity.runtime)) {
                boundSession = null
                bind(idx, true)
                SLog.d(TAG, "вкладка пересоздана принудительно")
            }
            true
        }
        reloadButton?.setOnClickListener {
            if ((requireActivity() as? MainActivity)?.reloadScripts() != true)
                SLog.w(TAG, "расширение не готово")
        }
        barsToggleButton?.setOnClickListener { setBarsHidden(true) }
        barsRestoreButton?.setOnClickListener { setBarsHidden(false) }

        controlPlay?.setOnClickListener { sendVideo("play-pause") }
        controlRewind?.setOnClickListener { sendVideo("rewind") }
        controlForward?.setOnClickListener { sendVideo("forward") }
        controlMute?.setOnClickListener { sendVideo("mute") }
        controlFullscreen?.setOnClickListener { sendVideo("fullscreen") }
        controlFit?.setOnClickListener {
            val now = !prefs.getBoolean("cover", false)
            prefs.edit().putBoolean("cover", now).apply()
            coverApplied = now
            sendVideo(if (now) "cover_on" else "cover_off")
            renderIcons()
            pulseGeckoResize()
        }

        scrubberView?.sideLeft = prefs.getBoolean("scrubber_left", false)

        scrubberView?.onSeekStart = {
            scrubberOverlay?.visibility = View.VISIBLE
        }
        scrubberView?.onSeek = { time ->
            val dur = scrubberView?.duration ?: 0.0
            if (dur > 0.0) {
                val frac = (time / dur).coerceIn(0.0, 1.0)
                SLog.d(TAG, "seek_frac=" + frac + " time=" + time + " dur=" + dur)
                (requireActivity() as? MainActivity)
                    ?.sendTabCommand("seek_frac", frac.toFloat(), ownerTabId)
            } else {
                SLog.w(TAG, "seek пропущен: duration=0")
            }
        }
        scrubberView?.onSeekEnd = { }

        urlInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { loadUrl(); true } else false
        }

        renderIcons()
        applyBars()
        viewModel.getActiveTab()?.let { geckoView?.setSession(it.session) }
    }

    private val prefs by lazy {
        requireContext().getSharedPreferences("sollor", android.content.Context.MODE_PRIVATE)
    }
    private var coverApplied = false
    private var coverBeforeHide = false

    /** Данные о видео из страницы: позиция, длительность, прямоугольник. */

    /** Скраббер в фулскрине: учитываем ширину видимых колонок (бар/навбар). */

    private val scrubberWatchdog = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (lastInfoAt > 0 && now - lastInfoAt > 2500) {
                SLog.d(TAG, "scrubber STALE: нет данных " + (now - lastInfoAt) + " мс — скрываю")
                scrubberOverlay?.visibility = View.GONE
                progressOverlay?.visibility = View.GONE
                scrubberView?.duration = 0.0
                lastInfoAt = 0L
            }
            view?.postDelayed(this, 1000)
        }
    }

    private fun placeScrubberInFullscreen() {
        val sv = scrubberView ?: return
        val overlay = scrubberOverlay ?: return
        val d = resources.displayMetrics.density
        val containerW = if (overlay.width > 0) overlay.width else resources.displayMetrics.widthPixels
        val containerH = if (overlay.height > 0) overlay.height else resources.displayMetrics.heightPixels

        // Ширина колонок, которые реально видны справа
        // Берём фактические ширины: колонки могут отличаться (64 и ~56dp)
        // Колонки справа есть только в ландскейпе; в портрете бары
        // горизонтальные и занимают всю ширину — их вычитать нельзя.
        var barsRight = 0
        if (isLandscape) {
            if (controlBar?.visibility == View.VISIBLE) {
                barsRight += (controlBar?.width ?: 0).takeIf { it > 0 } ?: (64 * d).toInt()
            }
            if (navBar?.visibility == View.VISIBLE) {
                barsRight += (navBar?.width ?: 0).takeIf { it > 0 } ?: (56 * d).toInt()
            }
        }

        val fsBarOnLeft = isLandscape && isBarsHidden && sv.sideLeft
        val fsBarOnRight = isLandscape && isBarsHidden && !sv.sideLeft
        if (fsBarOnRight) {
            barsRight += (fsControlBar?.width ?: 0).takeIf { it > 0 } ?: (64 * d).toInt()
        }

        // Область видео — это сам GeckoView, а не весь экран:
        // иначе скраббер заезжает на нижние бары.
        val gv = geckoView
        val videoTop = gv?.top ?: 0
        val videoH = (gv?.height ?: 0).takeIf { it > 0 } ?: (containerH - (2 * (64 * d).toInt()))

        val p = sv.layoutParams as? FrameLayout.LayoutParams ?: return
        p.width = (160 * d).toInt()
        p.height = (videoH - (8 * d).toInt() - (72 * d).toInt())
            .coerceAtLeast((120 * d).toInt())
        p.topMargin = videoTop + (8 * d).toInt()
        p.gravity = Gravity.START
        p.leftMargin = if (sv.sideLeft) {
            // слева: 0, либо отступ за оверлейным баром, если он тоже слева
            if (fsBarOnLeft) (64 * d).toInt() else 0
        } else {
            (containerW - p.width - barsRight).coerceAtLeast(0)
        }
        sv.layoutParams = p

        // индикатор прогресса — отдельный оверлей, всегда справа, на всю высоту
        placeProgressFullscreen(containerW, videoH, barsRight, videoTop)

        overlay.visibility = View.VISIBLE
        sv.resume()
    }



    /** Прогресс в фулскрине: справа, на всю высоту контейнера. */
    private fun placeProgressFullscreen(containerW: Int, containerH: Int, barsRight: Int, top: Int = 0) {
        val pv = videoProgress ?: return
        val overlay = progressOverlay ?: return
        val d = resources.displayMetrics.density
        val sv = scrubberView

        pv.progress = if ((sv?.duration ?: 0.0) > 0) sv!!.position / sv.duration else 0.0

        val lp = pv.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = (4 * d).toInt()
        lp.height = containerH
        lp.topMargin = top
        lp.gravity = Gravity.START
        lp.leftMargin = if (isLandscape && isBarsHidden) {
            containerW - lp.width                       // скрыто всё: у самого края
        } else {
            // левее контрол-бара, вплотную к нему
            (containerW - barsRight - lp.width).coerceAtLeast(0)
        }
        pv.layoutParams = lp
        overlay.visibility = View.VISIBLE
    }

    /** Прогресс в портрете: справа от видео, на всю высоту видео. */
    private fun placeProgressPortrait(videoRight: Int, top: Int, height: Int) {
        val pv = videoProgress ?: return
        val overlay = progressOverlay ?: return
        val d = resources.displayMetrics.density
        val sv = scrubberView
        pv.progress = if ((sv?.duration ?: 0.0) > 0) sv!!.position / sv.duration else 0.0

        val lp = pv.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = (4 * d).toInt()
        lp.height = height
        lp.topMargin = top
        lp.gravity = Gravity.START
        val containerW = if ((progressOverlay?.width ?: 0) > 0) progressOverlay!!.width
                         else resources.displayMetrics.widthPixels
        lp.leftMargin = (containerW - lp.width).coerceAtLeast(0)
        pv.layoutParams = lp
        overlay.visibility = View.VISIBLE
    }

    fun onVideoInfo(json: org.json.JSONObject, tabId: Long = -1L, frameId: Int = -1) {
        val sv = scrubberView ?: return
        val overlay = scrubberOverlay ?: return

        val now = System.currentTimeMillis()
        val area = json.optDouble("areaRatio", 0.0)
        val topFrame = json.optBoolean("topFrame", false)
        val dur = json.optDouble("duration", 0.0)

        // выбор владельца: приоритет верхнему фрейму с большой площадью видео
        val ownerStale = now - ownerSeenAt > 2500
        if (ownerStale || (topFrame && !(ownerArea > 0.25)) || area > ownerArea + 0.05) {
            if (tabId >= 0 && (topFrame || area > 0.25 || ownerTabId < 0)) {
                ownerTabId = tabId
                ownerFrameId = frameId
                ownerArea = area
            }
        }
        if (tabId >= 0 && ownerTabId >= 0 && tabId != ownerTabId) return
        if (frameId >= 0 && ownerFrameId >= 0 && frameId != ownerFrameId) return

        // Внутри одного фрейма тоже может быть несколько <video>:
        // держимся одного элемента, иначе позиция и флаги скачут.
        val videoId = json.optString("videoId", "")
        if (videoId.isNotEmpty()) {
            val incomingPaused = json.optBoolean("paused", true)
            if (ownerVideoId.isEmpty() || ownerStale) {
                ownerVideoId = videoId
                ownerPaused = incomingPaused
            } else if (videoId != ownerVideoId) {
                val incomingDur = json.optDouble("duration", 0.0)
                // Переключаемся, если наш элемент пустой (без длительности),
                // а этот — настоящее видео, либо если этот играет, а наш стоит
                if ((incomingDur > 1.0 && ownerDuration <= 1.0) || (!incomingPaused && ownerPaused)) {
                    ownerVideoId = videoId
                    ownerPaused = incomingPaused
                } else {
                    return
                }
            } else {
                ownerPaused = incomingPaused
                ownerDuration = json.optDouble("duration", 0.0)
            }
        }
        ownerSeenAt = now
        lastInfoAt = now

        // преролл и превью: нулевая/микроскопическая длительность не должна
        // обнулять шкалу, если у нас уже есть нормальная
        if (dur <= 1.0 && (scrubberView?.duration ?: 0.0) > 1.0) return

        val duration = json.optDouble("duration", 0.0)
        SLog.d(TAG, "VIDEO_INFO tab=" + tabId + " frame=" + frameId + " dur=" + duration + " pos=" + json.optDouble("position", 0.0) +
                " paused=" + json.optBoolean("paused", false) + " scroll=" + json.optBoolean("scrolling", false))
        if (duration > 1.0 && !coverApplied && prefs.getBoolean("cover", false)) {
            coverApplied = true
            (requireActivity() as? MainActivity)?.sendVideoCommand("cover")
        }
        val position = json.optDouble("position", 0.0)
        sv.duration = if (duration.isFinite()) duration else 0.0
        if (position.isFinite()) sv.onPlayerPosition(position)

        val nowMs = System.currentTimeMillis()
        val paused = if (json.has("paused")) json.optBoolean("paused") else null
        if (paused != null) {
            val pend = pendingPaused
            if (pend != null && paused == pend) {
                pendingPaused = null
                videoPaused = paused
                renderIcons()
            } else if (pend == null || nowMs > pendingUntil) {
                pendingPaused = null
                videoPaused = paused
                renderIcons()
            }
        }
        val muted = if (json.has("muted")) json.optBoolean("muted") else null
        if (muted != null) {
            val pend = pendingMuted
            if (pend != null && muted == pend) {
                pendingMuted = null
                videoMuted = muted
                renderIcons()
            } else if (pend == null || nowMs > pendingUntil) {
                pendingMuted = null
                videoMuted = muted
                renderIcons()
            }
        }
        val rate = json.optDouble("rate", 1.0).toFloat()
        val buffering = json.optBoolean("buffering", false)
        val seekingFlag = json.optBoolean("seeking", false)
        sv.onPlayerFlags(paused == false, buffering, rate, seekingFlag)

        if (isPageFullscreen) {
            placeScrubberInFullscreen()
            return
        }

        val vpW = json.optDouble("vpWidth", 0.0)
        val vpH = json.optDouble("vpHeight", 0.0)
        val rect = json.optJSONObject("rect")
        val gv = geckoView
        if (rect == null || gv == null || vpW <= 0.0 || vpH <= 0.0) {
            overlay.visibility = View.GONE
            return
        }

        val sx = gv.width / vpW
        val sy = gv.height / vpH
        val d = resources.displayMetrics.density
        val topInset = (8 * d).toInt()
        val bottomInset = (72 * d).toInt()
        val top = (rect.optDouble("y", 0.0) * sy).toInt() + topInset
        val height = (rect.optDouble("h", 0.0) * sy).toInt() - topInset - bottomInset
        if (height < 80) { overlay.visibility = View.GONE; return }

        val params = sv.layoutParams as? FrameLayout.LayoutParams ?: return
        params.height = height
        params.topMargin = top
        params.gravity = android.view.Gravity.START
        val scrubberWidth = (160 * resources.displayMetrics.density).toInt()
        params.width = scrubberWidth
        val left = (rect.optDouble("x", 0.0) * sx).toInt()
        val right = left + (rect.optDouble("w", 0.0) * sx).toInt()
        val floatBar = if (isLandscape && isPageFullscreen && isBarsHidden) (64 * d).toInt() else 0
        // 160-вью: полоса 56dp должна стоять у края видео
        params.leftMargin = if (sv.sideLeft)
            (left + floatBar).coerceAtLeast(0)
        else
            (right - scrubberWidth - floatBar).coerceAtLeast(0)
        sv.layoutParams = params

        placeProgressPortrait(right, top, height)
        val vis = if (duration > 1.0) View.VISIBLE else View.GONE
        overlay.visibility = vis
        SLog.d(TAG, "scrubber overlay=" + if (vis == View.VISIBLE) "VISIBLE" else "GONE" +
                " dur=" + duration + " rectH=" + height + " tab=" + tabId + " frame=" + frameId)
    }

    /** Скролл страницы: скраббер временно убираем, данные не трогаем. */
    fun onPageScroll(scrolling: Boolean) {
        if (scrolling) {
            scrubberOverlay?.visibility = View.GONE
            progressOverlay?.visibility = View.GONE
        }
        else if ((scrubberView?.duration ?: 0.0) > 1.0) scrubberOverlay?.visibility = View.VISIBLE
    }


    /** Полноэкранное состояние пришло со страницы (или от Gecko). */
    fun setPageFullscreen(fs: Boolean) {
        if (isPageFullscreen == fs) return
        isPageFullscreen = fs
        fsEnteredAt = if (fs) System.currentTimeMillis() else 0L
        onFullscreenChanged(fs)
        applyBars()
        // После смены баров размер вью меняется — просим пересчитать разметку,
        // иначе на первом входе контейнер остаётся смещённым.
        // Размер контейнера не меняем, но даём странице событие resize,
        // иначе она не пересчитывает фулскрин-элемент.
        view?.post { pulseGeckoResize() }
    }

    private fun setBarsHidden(hidden: Boolean) {
        if (isBarsHidden == hidden) return
        if (hidden) {
            coverBeforeHide = prefs.getBoolean("cover", false)
            isBarsHidden = true
            prefs.edit().putBoolean("cover", true).apply()
            coverApplied = true
            sendVideo("cover_on")
        } else {
            isBarsHidden = false
            prefs.edit().putBoolean("cover", coverBeforeHide).apply()
            coverApplied = coverBeforeHide
            sendVideo(if (coverBeforeHide) "cover_on" else "cover_off")
        }
        applyBars()
        renderIcons()
    }

    /**
     * Матрица (зафиксирована):
     *  Портрет обычный   : urlBar + navBar
     *  Портрет fullscreen: controlBar + navBar
     *  Ландскейп обычный : urlBar сверху + navBar
     *  Ландскейп fullscr : controlBar + navBar, статус-бара нет
     *  Скрытые бары      : всё скрыто, остаётся только круглая кнопка
     */
    private var coverBeforeFullscreen: Boolean? = null


    /** Толчок размеру GeckoView: страница пересчитывает фулскрин-элемент. */
    private fun pulseGeckoResize() {
        val gv = geckoView ?: return
        gv.setPadding(0, 0, 0, 1)
        gv.requestLayout()
        gv.post {
            gv.setPadding(0, 0, 0, 0)
            gv.requestLayout()
            placeScrubberInFullscreen()
            SLog.d(TAG, "geo h=" + gv.height + " top=" + gv.top +
                    " root=" + (view?.height ?: 0) + " fs=" + isPageFullscreen)
        }
    }

    /** Вход/выход из фулскрина: включаем cover, на выходе возвращаем как было. */
    private fun onFullscreenChanged(fs: Boolean) {
        if (fs) {
            if (coverBeforeFullscreen == null) {
                coverBeforeFullscreen = prefs.getBoolean("cover", false)
                prefs.edit().putBoolean("cover", true).apply()
                coverApplied = true
                sendVideo("cover_on")
            }
        } else {
            val prev = coverBeforeFullscreen
            coverBeforeFullscreen = null
            if (prev != null) {
                prefs.edit().putBoolean("cover", prev).apply()
                coverApplied = prev
                sendVideo(if (prev) "cover_on" else "cover_off")
            }
        }
        renderIcons()
        pulseGeckoResize()
    }

    private fun applyBars() {
        val hidden = isBarsHidden
        val fs = isPageFullscreen
        val land = isLandscape

        // В ландскейпе urlBar — контейнер GeckoView, его не прячем
        if (land) {
            urlBar?.visibility = View.VISIBLE
            urlInput?.visibility = if (hidden || fs) View.GONE else View.VISIBLE
        } else {
            urlInput?.visibility = View.VISIBLE
            urlBar?.visibility = if (hidden || fs) View.GONE else View.VISIBLE
        }

        navBar?.visibility = if (hidden) View.GONE else View.VISIBLE
        controlBar?.visibility = if (!hidden && fs) View.VISIBLE else View.GONE

        barsToggleButton?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        barsRestoreButton?.let { btn ->
            btn.visibility = if (hidden) View.VISIBLE else View.GONE
            (btn.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                val d = resources.displayMetrics.density
                val sideLeft = scrubberView?.sideLeft == true
                if (land) {
                    lp.gravity = if (sideLeft) Gravity.BOTTOM or Gravity.START
                                 else Gravity.BOTTOM or Gravity.END
                    lp.bottomMargin = (8 * d).toInt()
                    lp.leftMargin = (4 * d).toInt()
                    lp.rightMargin = (4 * d).toInt()
                    lp.topMargin = 0
                } else {
                    // портрет: внизу по центру
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = 0
                    lp.leftMargin = 0
                    lp.rightMargin = 0
                    lp.bottomMargin = (16 * d).toInt()
                }
                btn.layoutParams = lp
            }
            btn.bringToFront()
        }

        applyStatusBar()
        applyFsOverlay()
        applyControlTint()
        renderIcons()
        if (fs) placeScrubberInFullscreen()
        SLog.d(TAG, "bars hidden=$hidden fs=$fs land=$land")
    }

    /**
     * Статус-бар:
     *  - ландскейп-фулскрин: скрыт полностью;
     *  - портрет-фулскрин: чёрный, иконки убраны;
     *  - иначе: чёрный с иконками.
     * decorFitsSystemWindows не трогаем — GeckoView не растягивается.
     */
    /** Оверлей контрол-бара в фулскрине-ландскейпе при скрытых барах. */
    private fun applyFsOverlay() {
        val show = isLandscape && isPageFullscreen && isBarsHidden
        fsOverlay?.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val d = resources.displayMetrics.density
        val barWidth = (64 * d).toInt()
        val bar = fsControlBar ?: return
        val scrim = fsScrim ?: return

        val leftSide = scrubberView?.sideLeft == true
        val lp = bar.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = if (leftSide) Gravity.START else Gravity.END
        lp.leftMargin = 0
        lp.rightMargin = 0
        bar.layoutParams = lp

        val scrimLp = scrim.layoutParams as? FrameLayout.LayoutParams ?: return
        scrimLp.gravity = if (leftSide) Gravity.START else Gravity.END
        val measuredBar = bar.width.takeIf { it > 0 } ?: (56 * d).toInt()
        scrimLp.width = measuredBar + (4 * d).toInt()
        scrim.layoutParams = scrimLp

        // Подложка: полная прозрачность внутрь, 50% чёрного у края
        val colors = if (leftSide)
            intArrayOf(0x80000000.toInt(), 0x00000000)
        else
            intArrayOf(0x00000000, 0x80000000.toInt())
        scrim.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, colors
        )
    }

    private fun applyStatusBar() {
        val window = activity?.window ?: return
        window.statusBarColor = "#000000".toColorInt()
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        val insets = window.insetsController ?: return

        // Ландскейп-фулскрин: убираем и статус-бар, и системный nav-бар.
        // Портрет-фулскрин: только статус-бар (нижний бар нужен для жестов).
        // Скрытые бары в фулскрине — те же правила.
        val hideStatus = isPageFullscreen || isBarsHidden
        val hideNav = isPageFullscreen && isLandscape

        if (hideStatus) insets.hide(WindowInsets.Type.statusBars())
        else insets.show(WindowInsets.Type.statusBars())

        if (hideNav) insets.hide(WindowInsets.Type.navigationBars())
        else insets.show(WindowInsets.Type.navigationBars())
    }

    private var lastActiveTabId: Long = -1L

    // Владелец состояния: одна пара (вкладка, фрейм)
    private var ownerTabId: Long = -1L
    private var ownerFrameId: Int = -1
    private var ownerArea: Double = 0.0
    private var ownerSeenAt: Long = 0L
    private var ownerVideoId: String = ""
    private var ownerPaused: Boolean = true
    private var ownerDuration: Double = 0.0
    private var lastInfoAt: Long = 0L

    private fun pausePreviousTab() {
        if (lastActiveTabId >= 0) sendTabCommandToOwner("pause")
    }

    /** Команда той вкладке, чьё видео мы сейчас показываем (по id Gecko). */
    private fun sendTabCommandToOwner(action: String) {
        val activity = requireActivity() as? MainActivity ?: return
        if (ownerTabId >= 0) activity.sendTabCommand(action, null, ownerTabId)
        else activity.sendVideoCommand(action)
    }

    private fun bind(index: Int, force: Boolean) {
        val tab = viewModel.tabs.value?.getOrNull(index) ?: return
        // Активна ровно одна сессия: текущая. Остальные спят, чтобы не
        // декодировать видео и не слать состояние. Делаем это на каждом
        // bind, иначе после смены вкладки можно остаться с «белой» сессией.
        viewModel.tabs.value.orEmpty().forEach { t ->
            t.session.setActive(t.session === tab.session)
        }
        if (tab.id != lastActiveTabId) {
            // Выводим страницу из фулскрина ДО переключения, иначе при
            // возврате плеер будет в фулскрине, а интерфейс — с адресной строкой.
            if (isPageFullscreen) {
                // Просим страницу выйти из фулскрина; флаг обновит она сама
                sendTabCommandToOwner("fullscreen")
            }
            pausePreviousTab()
            lastActiveTabId = tab.id
            // Скреббер не должен показывать данные прошлой вкладки
            scrubberOverlay?.visibility = View.INVISIBLE
            scrubberView?.duration = 0.0
            ownerTabId = -1L
            ownerFrameId = -1
            ownerArea = 0.0
            ownerVideoId = ""
        }
        if (force || boundSession !== tab.session) {
            geckoView?.setSession(tab.session)
            attachDelegates(tab.session, tab.id)
            boundSession = tab.session
            installBack()
        }
        val shown = if (tab.url == "about:blank") "" else tab.url
        if (urlInput?.hasFocus() != true) urlInput?.setText(shown)
        updateBack()
    }

    private fun installBack() {
        backCallback?.remove()
        val cb = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // В фулскрине первый «назад» возвращает интерфейс с адресной строкой
                if (isPageFullscreen) { sendVideo("fullscreen"); return }
                val s = viewModel.getActiveTab()?.session
                if (s != null && GeckoSessionHelper.canGoBack(s)) s.goBack()
                else { isEnabled = false; activity?.onBackPressedDispatcher?.onBackPressed() }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, cb)
        backCallback = cb
        updateBack()
    }

    private fun updateBack() {
        val can = viewModel.getActiveTab()?.session?.let { GeckoSessionHelper.canGoBack(it) } == true
        backButton?.isEnabled = can
        backCallback?.isEnabled = can
    }

    private fun hideKeyboard() {
        try {
            val imm = requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            val token = urlInput?.windowToken ?: requireView().windowToken
            imm.hideSoftInputFromWindow(token, 0)
        } catch (e: Exception) { }
    }

    private fun loadUrl() {
        val text = urlInput?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val full = if (text.startsWith("http") || text.startsWith("about:")) text else "https://$text"
        SLog.d(TAG, "LOAD url=" + full + " tab=" + (viewModel.getActiveTab()?.id ?: -1))
        val session = viewModel.getActiveTab()?.session
        session?.setActive(true)
        session?.loadUri(full)
        viewModel.updateTabInfo(viewModel.activeIndex.value ?: 0, null, full)
        urlInput?.clearFocus()
        hideKeyboard()
    }

    private fun attachDelegates(session: GeckoSession, tabId: Long) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                SLog.d(TAG, "PAGE_START tab=" + tabId + " url=" + url)
                if (isActive(tabId)) {
                    scrubberOverlay?.visibility = View.GONE
                    progressOverlay?.visibility = View.GONE
                    scrubberView?.duration = 0.0
                    lastInfoAt = 0L
                    // новая страница — новый видеоэлемент
                    ownerDuration = 0.0
                    ownerVideoId = ""
                    ownerPaused = true
                    ownerSeenAt = 0L
                }
                if (url == "about:blank") return
                if (isActive(tabId)) { setUrl(tabId, url); updateField(url) }
            }
            override fun onPageStop(s: GeckoSession, success: Boolean) { updateBack() }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(s: GeckoSession) {
                SLog.w(TAG, "контент-процесс упал, перезагружаю вкладку")
                s.reload()
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                SLog.d(TAG, "NEW_SESSION uri=" + uri + " fromTab=" + tabId)
                val activity = requireActivity() as? MainActivity
                return if (activity == null) {
                    GeckoResult.fromValue(null)
                } else {
                    // Возвращаем НОВУЮ сессию из onNewSession: если вернуть null,
                    // Gecko загружает адрес ещё и в исходной вкладке — отсюда две копии.
                    // Важно: сессию НЕ открываем — Gecko откроет её сам,
                    // иначе падает с «Must use an unopened GeckoSession instance».
                    val session = GeckoSession()
                    activity.viewModel.addTabWithSession(session, uri)
                    GeckoResult.fromValue(session)
                }
            }

            override fun onLocationChange(
                s: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserInteraction: Boolean
            ) {
                if (url != null && url != "about:blank" && isActive(tabId)) {
                    setUrl(tabId, url); updateField(url)
                }
            }

            override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
                GeckoSessionHelper.onCanGoBackChanged(s, canGoBack)
                if (isActive(tabId)) updateBack()
            }

            override fun onCanGoForward(s: GeckoSession, canGoForward: Boolean) { }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                val i = viewModel.tabs.value?.indexOfFirst { it.id == tabId } ?: -1
                if (i >= 0 && !title.isNullOrBlank()) viewModel.updateTabInfo(i, title, null)
            }
        }
    }

    private fun isActive(tabId: Long) = viewModel.getActiveTab()?.id == tabId

    private fun setUrl(tabId: Long, url: String) {
        val i = viewModel.tabs.value?.indexOfFirst { it.id == tabId } ?: -1
        if (i >= 0) viewModel.updateTabInfo(i, null, url)
    }

    private fun updateField(url: String) {
        if (urlInput?.hasFocus() != true) urlInput?.setText(url)
    }

    /** В фулскрин-ландскейпе новые вкладки открываем уже в портрете. */
    private fun forcePortraitIfFsLandscape() {
        if (isLandscape && isPageFullscreen) {
            requireActivity().requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun sendVideo(action: String) {
        when (action) {
            "play-pause" -> {
                val next = (videoPaused ?: false).not()
                videoPaused = next
                pendingPaused = next
                pendingUntil = System.currentTimeMillis() + 1200L
                if (next) scrubberView?.onPlayerFlags(false, false, 1f, false)  // пауза: стоп сразу
                renderIcons()
            }
            "mute" -> {
                val next = (videoMuted ?: false).not()
                videoMuted = next
                pendingMuted = next
                pendingUntil = System.currentTimeMillis() + 1200L
                renderIcons()
            }
        }
        val ok = (requireActivity() as? MainActivity)
            ?.sendTabCommand(action, null, ownerTabId, ownerFrameId) ?: false
        if (!ok) SLog.w(TAG, "расширение не готово: " + action)
        when (action) {
            "play-pause" -> { videoPaused = (videoPaused ?: false).not(); renderIcons() }
            "mute" -> { videoMuted = (videoMuted ?: false).not(); renderIcons() }
        }
    }

    fun onVideoState(paused: Boolean?, muted: Boolean?) {
        paused?.let { videoPaused = it }
        muted?.let { videoMuted = it }
        renderIcons()
    }

    private fun applyControlTint() {
        val tint = if (isBarsHidden) 0xBFFFFFFF.toInt()
                   else requireContext().getColor(R.color.icon_tint)
        val list = android.content.res.ColorStateList.valueOf(tint)
        val content = view ?: return
        listOf(
            R.id.controlPlay, R.id.controlRewind, R.id.controlForward,
            R.id.controlMute, R.id.controlFullscreen, R.id.controlFit,
            R.id.fsPlay, R.id.fsRewind, R.id.fsForward,
            R.id.fsMute, R.id.fsFullscreen, R.id.fsFit
        ).forEach { id -> (content.findViewById<ImageButton>(id))?.imageTintList = list }
    }

    private data class IconState(
        val paused: Boolean?, val muted: Boolean?, val cover: Boolean, val fullscreen: Boolean
    )
    private var lastIconState: IconState? = null

    private fun renderIcons() {
        val coverOn = prefs.getBoolean("cover", false)
        val state = IconState(videoPaused, videoMuted, coverOn, isPageFullscreen)
        if (state == lastIconState) return          // ничего не изменилось — не трогаем вью
        lastIconState = state

        val playIcon = if (state.paused == false) R.drawable.ic_pause_24px else R.drawable.ic_play_arrow_24px
        val muteIcon = if (state.muted == true) R.drawable.ic_volume_off_24px else R.drawable.ic_volume_up_24px
        val fsIcon = if (state.fullscreen) R.drawable.ic_fullscreen_exit_24px else R.drawable.ic_fullscreen_24px
        val fitIcon = if (state.cover) R.drawable.ic_cover_on_24px else R.drawable.ic_cover_off_24px

        controlPlay?.setImageResource(playIcon)
        controlMute?.setImageResource(muteIcon)
        controlFullscreen?.setImageResource(fsIcon)
        controlFit?.setImageResource(fitIcon)

        val v = view
        if (v != null) {
            v.findViewById<ImageButton>(R.id.fsPlay)?.setImageResource(playIcon)
            v.findViewById<ImageButton>(R.id.fsMute)?.setImageResource(muteIcon)
            v.findViewById<ImageButton>(R.id.fsFullscreen)?.setImageResource(fsIcon)
            v.findViewById<ImageButton>(R.id.fsFit)?.setImageResource(fitIcon)
        }
    }

}
