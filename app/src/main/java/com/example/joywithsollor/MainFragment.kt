package com.example.joywithsollor

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainFragment : Fragment() {

    private var geckoView: GeckoView? = null
    private var urlInput: EditText? = null
    private var tabsButton: Button? = null
    private var backButton: Button? = null
    private var addTabButton: Button? = null
    private var scrubberSideButton: MaterialButton? = null
    private var reloadScriptsButton: MaterialButton? = null
    private var urlBar: View? = null
    private var navBar: View? = null
    private var controlBar: View? = null

    private lateinit var viewModel: TabsViewModel

    private var isFullScreen: Boolean = false

    companion object {
        private const val TAG = "Sollor"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(TabsViewModel::class.java)

        inflateContent(view as FrameLayout)

        viewModel.activeIndex.observe(viewLifecycleOwner) { index ->
            val tab = viewModel.tabs.value?.getOrNull(index)
            if (tab != null) {
                geckoView?.setSession(tab.session)
                urlInput?.setText(tab.url)
                attachDelegates(tab.session, index)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val root = view as? FrameLayout ?: return
        inflateContent(root)
    }

    private fun inflateContent(root: FrameLayout) {
        root.removeAllViews()

        val content = layoutInflater.inflate(R.layout.fragment_main, root, false)
        root.addView(content)

        geckoView = content.findViewById(R.id.geckoView)
        urlInput = content.findViewById(R.id.urlInput)
        tabsButton = content.findViewById(R.id.tabsButton)
        backButton = content.findViewById(R.id.backButton)
        addTabButton = content.findViewById(R.id.addTabButton)
        scrubberSideButton = content.findViewById(R.id.scrubberSideButton)
        reloadScriptsButton = content.findViewById(R.id.reloadScriptsButton)
        urlBar = content.findViewById(R.id.urlBar)
        navBar = content.findViewById(R.id.navBar)
        controlBar = content.findViewById(R.id.controlBar)

        geckoView?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, 24f)
            }
        }
        geckoView?.clipToOutline = true

        backButton?.setOnClickListener {
            val session = viewModel.getActiveTab()?.session
            if (session != null) {
                try {
                    session.goBack()
                } catch (_: Exception) {
                    val tabs = viewModel.tabs.value
                    if (tabs != null && tabs.size > 1) {
                        val idx = viewModel.activeIndex.value ?: 0
                        viewModel.closeTab(idx)
                    } else {
                        activity?.finish()
                    }
                }
            }
        }

        addTabButton?.setOnClickListener {
            val runtime = (requireActivity() as? MainActivity)?.runtime
            if (runtime != null) {
                viewModel.createTab(null, runtime)
            }
        }

        tabsButton?.setOnClickListener {
            val tabsFragment = TabsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, tabsFragment)
                .addToBackStack(null)
                .commit()
        }

        scrubberSideButton?.setOnClickListener {
            val activity = requireActivity() as? MainActivity ?: return@setOnClickListener
            val ok = activity.toggleScrubberSide()
            if (!ok) {
                Toast.makeText(requireContext(), "Расширение ещё не готово", Toast.LENGTH_SHORT).show()
            }
        }

        reloadScriptsButton?.setOnClickListener {
            val activity = requireActivity() as? MainActivity ?: return@setOnClickListener
            val ok = activity.reloadScripts()
            if (!ok) {
                Toast.makeText(requireContext(), "Расширение ещё не готово", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Обновляю скрипты…", Toast.LENGTH_SHORT).show()
            }
        }

        urlInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl()
                true
            } else false
        }

        val session = viewModel.getActiveTab()?.session
        if (session != null) {
            geckoView?.setSession(session)
        }

        applyFullScreenState(isFullScreen)

        if (isFullScreen) {
            root.post {
                enterImmersive()
                root.requestApplyInsets()
            }
        }

        Log.d(TAG, "inflateContent: orientation=${resources.configuration.orientation}, " +
                "fullScreen=$isFullScreen")
    }

    /**
     * Применяет состояние фулскрина: прячет urlInput, показывает controlBar.
     * Учитывает, лежит ли controlBar внутри urlBar (портрет) или отдельно (ландскейп).
     */
    private fun applyFullScreenState(fullScreen: Boolean) {
        if (fullScreen) {
            urlInput?.visibility = View.GONE
            controlBar?.visibility = View.VISIBLE

            val controlBarInsideUrlBar = urlBar?.findViewById<View>(R.id.controlBar) != null
            if (!controlBarInsideUrlBar) {
                urlBar?.visibility = View.GONE
            }
        } else {
            urlInput?.visibility = View.VISIBLE
            controlBar?.visibility = View.GONE
            urlBar?.visibility = View.VISIBLE
        }
    }

    private fun loadUrl() {
        val url = urlInput?.text?.toString()?.trim() ?: return
        if (url.isEmpty()) {
            Toast.makeText(requireContext(), "Enter URL", Toast.LENGTH_SHORT).show()
            return
        }
        val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        val session = viewModel.getActiveTab()?.session
        session?.loadUri(fullUrl)
        val activeIndex = viewModel.activeIndex.value ?: 0
        viewModel.updateTabInfo(activeIndex, null, fullUrl)
        urlInput?.setText(fullUrl)
    }

    private fun attachDelegates(session: GeckoSession, index: Int) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                urlInput?.setText(url)
                viewModel.updateTabInfo(index, null, url)
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {}
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val runtime = (requireActivity() as? MainActivity)?.runtime
                if (runtime != null) {
                    viewModel.createTab(uri, runtime)
                }
                return GeckoResult.fromValue(null)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                isFullScreen = fullScreen
                if (fullScreen) {
                    enterImmersive()
                    applyFullScreenState(true)
                } else {
                    exitImmersive()
                    applyFullScreenState(false)
                    requireActivity().requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    /**
     * Полный иммерсив: прячет статус-бар и системный navigation bar,
     * разрешает окну рисовать под ними и в вырез камеры.
     * Дублируем через systemUiVisibility — Samsung иногда игнорирует
     * WindowInsetsController.hide().
     */
    private fun enterImmersive() {
        val window = requireActivity().window

        // Окно рисует под системными барами
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Разрешаем расширяться в область выреза камеры.
        // На API 30+ используем ALWAYS, иначе Samsung не уводит окно под cutout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            window.attributes = attrs
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        window.decorView.requestApplyInsets()

        Log.d(TAG, "enterImmersive: applied (ALWAYS cutout)")
    }

    /**
     * Выход из фулскрина: возвращаем обычный режим.
     */
    private fun exitImmersive() {
        val window = requireActivity().window

        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = attrs
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        window.decorView.requestApplyInsets()

        Log.d(TAG, "exitImmersive: applied")
    }
}