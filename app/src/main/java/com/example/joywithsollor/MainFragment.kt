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
import android.widget.Toast
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
    private var scrubberOverlay: View? = null
    private var fsOverlay: View? = null
    private var fsScrim: View? = null
    private var fsControlBar: View? = null
    private var hideScrubberRunnable: Runnable? = null

    private lateinit var viewModel: TabsViewModel
    private var boundSession: GeckoSession? = null
    private var backCallback: OnBackPressedCallback? = null

    private var isPageFullscreen = false
    private var isBarsHidden = false
    private var videoPaused: Boolean? = null
    private var videoMuted: Boolean? = null

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
            view?.post { Toast.makeText(requireContext(), "Видео: $msg", Toast.LENGTH_SHORT).show() }
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
        (content as? android.view.ViewGroup)?.clipChildren = false
        (scrubberOverlay as? android.view.ViewGroup)?.clipChildren = false
        scrubberOverlay = content.findViewById(R.id.scrubberOverlay)
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
        reloadButton?.setOnClickListener {
            if ((requireActivity() as? MainActivity)?.reloadScripts() != true)
                Toast.makeText(requireContext(), "Расширение не готово", Toast.LENGTH_SHORT).show()
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
        }

        scrubberView?.sideLeft = prefs.getBoolean("scrubber_left", false)

        scrubberView?.onSeekStart = {
            scrubberOverlay?.visibility = View.VISIBLE
        }
        scrubberView?.onSeek = { time ->
            val dur = scrubberView?.duration ?: 0.0
            if (dur > 0.0) {
                val frac = (time / dur).coerceIn(0.0, 1.0)
                Log.d(TAG, "seek_frac=" + frac + " time=" + time + " dur=" + dur)
                (requireActivity() as? MainActivity)
                    ?.sendTabCommand("seek_frac", frac.toFloat(), ownerTabId)
            } else {
                Log.w(TAG, "seek пропущен: duration=0")
            }
        }
        scrubberView?.onSeekEnd = { scheduleScrubberHide(600) }

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
                Log.d(TAG, "scrubber STALE: нет данных " + (now - lastInfoAt) + " мс — скрываю")
                scrubberOverlay?.visibility = View.GONE
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

        var barsWidth = 0
        if (controlBar?.visibility == View.VISIBLE) barsWidth += (56 * d).toInt()
        if (navBar?.visibility == View.VISIBLE) barsWidth += (56 * d).toInt()
        if (isLandscape && isBarsHidden) barsWidth += (56 * d).toInt()   // оверлейный бар

        val p = sv.layoutParams as? FrameLayout.LayoutParams ?: return
        p.width = (160 * d).toInt()
        p.height = (containerH - (8 * d).toInt() - (72 * d).toInt())
            .coerceAtLeast((120 * d).toInt())
        p.topMargin = (8 * d).toInt()
        p.gravity = Gravity.START
        p.leftMargin = if (sv.sideLeft) barsWidth
            else (containerW - p.width - barsWidth).coerceAtLeast(0)
        sv.layoutParams = p
        overlay.visibility = View.VISIBLE
        sv.resume()
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
        ownerSeenAt = now
        lastInfoAt = now

        // преролл и превью: нулевая/микроскопическая длительность не должна
        // обнулять шкалу, если у нас уже есть нормальная
        if (dur <= 1.0 && (scrubberView?.duration ?: 0.0) > 1.0) return

        val duration = json.optDouble("duration", 0.0)
        Log.d(TAG, "VIDEO_INFO tab=" + tabId + " frame=" + frameId + " dur=" + duration + " pos=" + json.optDouble("position", 0.0) +
                " paused=" + json.optBoolean("paused", false) + " scroll=" + json.optBoolean("scrolling", false))
        if (duration > 1.0 && !coverApplied && prefs.getBoolean("cover", false)) {
            coverApplied = true
            (requireActivity() as? MainActivity)?.sendVideoCommand("cover")
        }
        val position = json.optDouble("position", 0.0)
        sv.duration = if (duration.isFinite()) duration else 0.0
        if (position.isFinite()) sv.onPlayerPosition(position)

        val paused = if (json.has("paused")) json.optBoolean("paused") else null
        if (paused != null) { videoPaused = paused; renderIcons() }
        val muted = if (json.has("muted")) json.optBoolean("muted") else null
        if (muted != null) { videoMuted = muted; renderIcons() }
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
        val strip = (56 * resources.displayMetrics.density).toInt()
        val floatBar = if (isLandscape && isPageFullscreen && isBarsHidden) (56 * d).toInt() else 0
        // 160-вью: полоса 56dp должна стоять у края видео
        params.leftMargin = if (sv.sideLeft)
            (left + floatBar).coerceAtLeast(0)
        else
            (right - scrubberWidth - floatBar).coerceAtLeast(0)
        sv.layoutParams = params

        val vis = if (duration > 1.0) View.VISIBLE else View.GONE
        overlay.visibility = vis
        Log.d(TAG, "scrubber overlay=" + if (vis == View.VISIBLE) "VISIBLE" else "GONE" +
                " dur=" + duration + " rectH=" + height + " tab=" + tabId + " frame=" + frameId)
    }

    /** Скролл страницы: скраббер временно убираем, данные не трогаем. */
    fun onPageScroll(scrolling: Boolean) {
        if (scrolling) scrubberOverlay?.visibility = View.GONE
        else if ((scrubberView?.duration ?: 0.0) > 1.0) scrubberOverlay?.visibility = View.VISIBLE
    }

    private fun scheduleScrubberHide(delayMs: Long) {
        hideScrubberRunnable?.let { view?.removeCallbacks(it) }
        val r = Runnable {
            if (!isLandscape && !isPageFullscreen) scrubberOverlay?.visibility = View.GONE
        }
        hideScrubberRunnable = r
        view?.postDelayed(r, delayMs)
    }

    /** Полноэкранное состояние пришло со страницы (или от Gecko). */
    fun setPageFullscreen(fs: Boolean) {
        if (isPageFullscreen == fs) return
        isPageFullscreen = fs
        applyBars()
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
        if (fs) placeScrubberInFullscreen()
        Log.d(TAG, "bars hidden=$hidden fs=$fs land=$land")
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
        val barWidth = (56 * d).toInt()
        val bar = fsControlBar ?: return
        val scrim = fsScrim ?: return

        val leftSide = scrubberView?.sideLeft == true
        val lp = bar.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = if (leftSide) Gravity.START else Gravity.END
        bar.layoutParams = lp

        val scrimLp = scrim.layoutParams as? FrameLayout.LayoutParams ?: return
        scrimLp.gravity = if (leftSide) Gravity.START else Gravity.END
        scrimLp.width = barWidth + (56 * d).toInt()
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
    private var lastInfoAt: Long = 0L

    private fun pausePreviousTab() {
        val prev = lastActiveTabId
        if (prev >= 0) (requireActivity() as? MainActivity)?.sendTabCommand("pause", null, prev)
    }

    private fun bind(index: Int, force: Boolean) {
        val tab = viewModel.tabs.value?.getOrNull(index) ?: return
        if (tab.id != lastActiveTabId) {
            pausePreviousTab()
            lastActiveTabId = tab.id
            (requireActivity() as? MainActivity)?.activeMediaTabId = tab.id
            // Скреббер не должен показывать данные прошлой вкладки
            scrubberOverlay?.visibility = View.INVISIBLE
            scrubberView?.duration = 0.0
            ownerTabId = -1L
            ownerFrameId = -1
            ownerArea = 0.0
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
        Log.d(TAG, "LOAD url=" + full + " tab=" + (viewModel.getActiveTab()?.id ?: -1))
        viewModel.getActiveTab()?.session?.loadUri(full)
        viewModel.updateTabInfo(viewModel.activeIndex.value ?: 0, null, full)
        urlInput?.clearFocus()
        hideKeyboard()
    }

    private fun attachDelegates(session: GeckoSession, tabId: Long) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                Log.d(TAG, "PAGE_START tab=" + tabId + " url=" + url)
                if (isActive(tabId)) {
                    scrubberOverlay?.visibility = View.GONE
                    scrubberView?.duration = 0.0
                    lastInfoAt = 0L
                }
                if (url == "about:blank") return
                if (isActive(tabId)) { setUrl(tabId, url); updateField(url) }
            }
            override fun onPageStop(s: GeckoSession, success: Boolean) { updateBack() }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                Log.d(TAG, "NEW_SESSION uri=" + uri + " fromTab=" + tabId)
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
        val ok = (requireActivity() as? MainActivity)
            ?.sendTabCommand(action, null, ownerTabId, ownerFrameId) ?: false
        if (!ok) Toast.makeText(requireContext(), "Расширение не готово", Toast.LENGTH_SHORT).show()
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

    private fun renderIcons() {
        val coverOn = prefs.getBoolean("cover", false)
        controlFit?.setImageResource(
            if (coverOn) R.drawable.ic_cover_on_24px else R.drawable.ic_cover_off_24px
        )
        controlFullscreen?.setImageResource(
            if (isPageFullscreen) R.drawable.ic_fullscreen_exit_24px
            else R.drawable.ic_fullscreen_24px
        )
        val playIcon = if (videoPaused == false) R.drawable.ic_pause_24px else R.drawable.ic_play_arrow_24px
        val muteIcon = if (videoMuted == true) R.drawable.ic_volume_off_24px else R.drawable.ic_volume_up_24px
        val fsIcon = if (isPageFullscreen) R.drawable.ic_fullscreen_exit_24px else R.drawable.ic_fullscreen_24px
        val fitIcon = if (coverOn) R.drawable.ic_cover_on_24px else R.drawable.ic_cover_off_24px
        view?.let { v ->
            listOf(R.id.fsPlay).forEach { v.findViewById<ImageButton>(it)?.setImageResource(playIcon) }
            listOf(R.id.fsMute).forEach { v.findViewById<ImageButton>(it)?.setImageResource(muteIcon) }
            listOf(R.id.fsFullscreen).forEach { v.findViewById<ImageButton>(it)?.setImageResource(fsIcon) }
            listOf(R.id.fsFit).forEach { v.findViewById<ImageButton>(it)?.setImageResource(fitIcon) }
        }
        controlPlay?.setImageResource(
            if (videoPaused == false) R.drawable.ic_pause_24px else R.drawable.ic_play_arrow_24px
        )
        controlMute?.setImageResource(
            if (videoMuted == true) R.drawable.ic_volume_off_24px else R.drawable.ic_volume_up_24px
        )
    }
}
