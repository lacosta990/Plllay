package com.example.joywithsollor

import android.content.pm.ActivityInfo
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainFragment : Fragment() {

    private lateinit var geckoView: GeckoView
    private lateinit var urlInput: EditText
    private lateinit var tabsButton: Button
    private lateinit var backButton: Button
    private lateinit var addTabButton: Button
    private lateinit var urlBar: View
    private lateinit var navBar: View

    private lateinit var viewModel: TabsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        geckoView = view.findViewById(R.id.geckoView)
        urlInput = view.findViewById(R.id.urlInput)
        tabsButton = view.findViewById(R.id.tabsButton)
        backButton = view.findViewById(R.id.backButton)
        addTabButton = view.findViewById(R.id.addTabButton)
        urlBar = view.findViewById(R.id.urlBar)
        navBar = view.findViewById(R.id.navBar)

        geckoView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, 24f)
            }
        }
        geckoView.clipToOutline = true

        viewModel = ViewModelProvider(requireActivity()).get(TabsViewModel::class.java)

        viewModel.activeIndex.observe(viewLifecycleOwner) { index ->
            val tab = viewModel.tabs.value?.getOrNull(index)
            if (tab != null) {
                geckoView.setSession(tab.session)
                urlInput.setText(tab.url)
                attachDelegates(tab.session, index)
            }
        }

        // ============================================================
        // КНОПКА НАЗАД
        // ============================================================
        backButton.setOnClickListener {
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

        // ============================================================
        // КНОПКА + (НОВАЯ ВКЛАДКА)
        // ============================================================
        addTabButton.setOnClickListener {
            val runtime = (requireActivity() as? MainActivity)?.runtime
            if (runtime != null) {
                viewModel.createTab(null, runtime) // Создаст вкладку с домашней страницей (задано в TabsViewModel)
                // Если вы хотите, чтобы открывалась конкретная страница, передайте её вместо null
            }
        }

        // ============================================================
        // КНОПКА "ВКЛАДКИ"
        // ============================================================
        tabsButton.setOnClickListener {
            val tabsFragment = TabsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, tabsFragment)
                .addToBackStack(null)
                .commit()
        }

        // ============================================================
        // АДРЕСНАЯ СТРОКА
        // ============================================================
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl()
                true
            } else false
        }
    }

    private fun loadUrl() {
        val url = urlInput.text.toString().trim()
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
        urlInput.setText(fullUrl)
    }

    private fun attachDelegates(session: GeckoSession, index: Int) {
        // ProgressDelegate – обновление URL
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                urlInput.setText(url)
                viewModel.updateTabInfo(index, null, url)
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {}
        }

        // NavigationDelegate – перехват новых окон
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val runtime = (requireActivity() as? MainActivity)?.runtime
                if (runtime != null) {
                    viewModel.createTab(uri, runtime)
                }
                return GeckoResult.fromValue(null)
            }
        }

        // ContentDelegate – полноэкранный режим
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (fullScreen) {
                    hideSystemBars()
                    (requireActivity() as? AppCompatActivity)?.supportActionBar?.hide()
                    urlBar.visibility = View.GONE
                    navBar.visibility = View.GONE
                } else {
                    showSystemBars()
                    (requireActivity() as? AppCompatActivity)?.supportActionBar?.show()
                    urlBar.visibility = View.VISIBLE
                    navBar.visibility = View.VISIBLE
                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    private fun hideSystemBars() {
        val window = requireActivity().window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun showSystemBars() {
        val window = requireActivity().window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}