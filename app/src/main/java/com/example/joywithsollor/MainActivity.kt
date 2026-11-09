package com.example.joywithsollor

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class MainActivity : AppCompatActivity() {

    lateinit var runtime: GeckoRuntime
    lateinit var viewModel: TabsViewModel

    private var sollorPort: WebExtension.Port? = null
    var onVideoError: ((String) -> Unit)? = null

    /** Отправить команду конкретной вкладке (или активной, если tabId < 0). */
    fun sendTabCommand(action: String, value: Float?, tabId: Long, frameId: Int = -1): Boolean {
        val port = sollorPort ?: return false
        return try {
            port.postMessage(JSONObject().apply {
                put("type", "TOGGLE_VIDEO")
                put("action", action)
                if (value != null) put("value", value.toDouble())
                if (tabId >= 0) put("tabId", tabId)
                if (frameId >= 0) put("frameId", frameId)
            })
            true
        } catch (e: Throwable) { sollorPort = null; false }
    }

    companion object {
        private const val TAG = "Sollor"
        private const val EXTENSION_ID = "sollor@example.com"
        private const val EXTENSION_URI = "resource://android/assets/extensions/sollor/"
    }

    override fun onResume() {
        super.onResume()
        // После возврата из фона порт расширения мог отвалиться:
        // без него не работают ни кнопки, ни состояние видео.
        if (sollorPort == null) {
            SLog.d(TAG, "порт расширения потерян — переподключаю")
            registerSollorExtension()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SLog.d(TAG, "ACTIVITY_CREATE savedInstanceState=" + (savedInstanceState != null))
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF000000.toInt()))
        window.statusBarColor = "#000000".toColorInt()
        window.navigationBarColor = "#17171A".toColorInt()
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.isAppearanceLightStatusBars = false
        ic.isAppearanceLightNavigationBars = false

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = attrs
        }

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .consoleOutput(BuildConfig.DEBUG)
        if (BuildConfig.DEBUG) {
            // Только для отладки: иначе смешанный контент без предупреждений.
            settingsBuilder.allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
        }
        val settings = settingsBuilder
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .build()
        settings.arguments.toMutableList().apply {
            add("--setpref=network.dns.disableIPv6=true")
            add("--setpref=layout.css.prefers-color-scheme.content-override=0")
            add("--setpref=ui.systemUsesDarkTheme=1")
        }

        runtime = GeckoRuntime.create(this, settings)
        registerSollorExtension()

        viewModel = ViewModelProvider(this).get(TabsViewModel::class.java)
        viewModel.initFirstTab(runtime)
        viewModel.onTabsEmpty = { viewModel.createTab(TabsViewModel.HOME_URL, runtime) }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MainFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    val session = viewModel.getActiveTab()?.session
                    if (session != null && GeckoSessionHelper.canGoBack(session)) {
                        session.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun registerSollorExtension() {
        runtime.webExtensionController.ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept({ ext ->
                if (ext == null) {
                    Log.e(TAG, "Расширение не загрузилось")
                    return@accept
                }
                val portDelegate = object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        handlePortMessage(message)
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        if (port == sollorPort) sollorPort = null
                    }
                }
                val msgDelegate = object : WebExtension.MessageDelegate {
                    override fun onConnect(port: WebExtension.Port) {
                        sollorPort = port
                        port.setDelegate(portDelegate)
                        SLog.d(TAG, "Порт расширения подключён")
                    }
                    override fun onMessage(
                        nativeApp: String,
                        message: Any,
                        sender: WebExtension.MessageSender
                    ): GeckoResult<Any>? {
                        handlePortMessage(message)
                        return null
                    }
                }
                ext.setMessageDelegate(msgDelegate, "browser")
            }, { error -> Log.e(TAG, "Ошибка расширения: ${error?.message}") })
    }

    private fun handlePortMessage(message: Any) {
        val json = when (message) {
            is JSONObject -> message
            is Map<*, *> -> JSONObject().apply {
                message.forEach { (k, v) -> if (k != null) put(k.toString(), v) }
            }
            else -> return
        }

        when (json.optString("type")) {
            "SCREEN_ORIENTATION_CONTROL" -> {
                val action = json.optString("action")
                SLog.d(TAG, "[DIAG] ориентация из страницы: $action")
                runOnUiThread {
                    when (action) {
                        "landscape" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "portrait" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "unspecified" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
            "FULLSCREEN_STATE" -> {
                val fs = json.optBoolean("fullscreen", false)
                SLog.d(TAG, "[DIAG] FULLSCREEN_STATE=$fs")
                if (!fs) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                runOnUiThread {
                    (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? MainFragment)
                        ?.setPageFullscreen(fs)
                }
            }
            "VIDEO_SCROLL" -> {
                val scrolling = json.optBoolean("scrolling", false)
                runOnUiThread {
                    (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? MainFragment)
                        ?.onPageScroll(scrolling)
                }
            }
            "VIDEO_INFO" -> {
                val tabId = json.optLong("tabId", -1L)
                val frameId = json.optInt("frameId", -1)
                val data = json.optJSONObject("data") ?: json
                runOnUiThread {
                    (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? MainFragment)
                        ?.onVideoInfo(data, tabId, frameId)
                }
            }
            "VIDEO_STATE" -> {
                if (json.has("error")) onVideoError?.invoke(json.optString("error"))
                else {
                    val st = json.optJSONObject("state")
                    val paused = if (st != null && st.has("paused")) st.optBoolean("paused") else null
                    val muted = if (st != null && st.has("muted")) st.optBoolean("muted") else null
                    (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? MainFragment)
                        ?.onVideoState(paused, muted)
                }
            }
        }
    }

    fun sendVideoValue(action: String, value: Float): Boolean {
        val port = sollorPort ?: return false
        return try {
            port.postMessage(JSONObject().apply {
                put("type", "TOGGLE_VIDEO")
                put("action", action)
                put("value", value.toDouble())
            })
            true
        } catch (e: Throwable) { sollorPort = null; false }
    }

    fun sendVideoCommand(action: String): Boolean {
        val port = sollorPort ?: run {
            onVideoError?.invoke("порт расширения не готов")
            return false
        }
        return try {
            port.postMessage(JSONObject().apply {
                put("type", "TOGGLE_VIDEO")
                put("action", action)
            })
            true
        } catch (e: Throwable) {
            sollorPort = null
            onVideoError?.invoke("ошибка отправки: ${e.message}")
            false
        }
    }

    fun toggleScrubberSide(): Boolean {
        val port = sollorPort ?: return false
        return try {
            port.postMessage(JSONObject().apply { put("type", "TOGGLE_SCRUBBER_SIDE") })
            true
        } catch (e: Throwable) { sollorPort = null; false }
    }

    fun reloadScripts(): Boolean {
        val port = sollorPort ?: return false
        return try {
            port.postMessage(JSONObject().apply { put("type", "RELOAD_SCRIPTS") })
            true
        } catch (e: Throwable) { sollorPort = null; false }
    }

    fun createNewTab() { viewModel.createTab(null, runtime) }

    fun goBackInActiveTab() {
        val session = viewModel.getActiveTab()?.session ?: return
        if (GeckoSessionHelper.canGoBack(session)) session.goBack()
        else viewModel.closeTab(viewModel.activeIndex.value ?: 0)
    }
}
