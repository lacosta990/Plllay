package com.example.joywithsollor

import android.content.pm.ActivityInfo
import android.os.Build
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
    private lateinit var viewModel: TabsViewModel

    var sollorExtension: WebExtension? = null
        private set

    private var sollorPort: WebExtension.Port? = null

    companion object {
        private const val TAG = "Sollor"
        private const val EXTENSION_ID = "sollor@example.com"
        private const val EXTENSION_URI = "resource://android/assets/extensions/sollor/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Обычный режим: окно НЕ рисует под системными барами.
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Обычный режим: вырез камеры учитывается системой.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = attrs
        }

        window.statusBarColor = "#0C0C0D".toColorInt()
        window.navigationBarColor = "#17171A".toColorInt()

        val settings = GeckoRuntimeSettings.Builder()
            .allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
            .consoleOutput(true)
            .build()

        settings.arguments.toMutableList().apply {
            add("--setpref=network.dns.disableIPv6=true")
        }

        runtime = GeckoRuntime.create(this, settings)

        registerSollorExtension()

        viewModel = ViewModelProvider(this).get(TabsViewModel::class.java)
        viewModel.initFirstTab(runtime)

        viewModel.onTabsEmpty = {
            viewModel.createTab("https://cntrl.digital/plllay-app", runtime)
        }

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
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun registerSollorExtension() {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept({ extension ->
                if (extension == null) {
                    Log.e(TAG, "❌ Расширение вернуло null")
                    return@accept
                }
                Log.d(TAG, "✅ Расширение загружено успешно")
                sollorExtension = extension

                try {
                    runtime.webExtensionController.setAddonManagerDelegate(
                        object : WebExtensionController.AddonManagerDelegate {
                            override fun onReady(extension: WebExtension) {
                                Log.d(TAG, "🟢 AddonManager: onReady ${extension.id}")
                            }
                            override fun onInstalled(extension: WebExtension) {
                                Log.d(TAG, "🟢 AddonManager: onInstalled ${extension.id}")
                            }
                            override fun onUninstalled(extension: WebExtension) {
                                Log.d(TAG, "🟢 AddonManager: onUninstalled ${extension.id}")
                            }
                        }
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "AddonManagerDelegate недоступен: ${e.message}")
                }

                val portDelegate = object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        Log.d(TAG, "📩 Сообщение через порт: $message")
                        handlePortMessage(message)
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        Log.d(TAG, "🔌 Порт отключён")
                        if (port == sollorPort) sollorPort = null
                    }
                }

                val messageDelegate = object : WebExtension.MessageDelegate {
                    override fun onConnect(port: WebExtension.Port) {
                        Log.d(TAG, "🔗 Порт расширения подключён!")
                        sollorPort = port
                        port.setDelegate(portDelegate)
                    }
                    override fun onMessage(
                        nativeApp: String,
                        message: Any,
                        sender: WebExtension.MessageSender
                    ): GeckoResult<Any>? {
                        Log.d(TAG, "📩 Сообщение от расширения: $message")
                        handlePortMessage(message)
                        return null
                    }
                }

                try {
                    extension.setMessageDelegate(messageDelegate, "browser")
                    Log.d(TAG, "✅ setMessageDelegate выполнен")
                } catch (e: Throwable) {
                    Log.e(TAG, "❌ setMessageDelegate: ${e.message}")
                }
            }, { error ->
                Log.e(TAG, "❌ Ошибка загрузки расширения: ${error?.message}")
            })
    }

    /**
     * Разбирает сообщение, пришедшее из JS через порт.
     * Поддерживает SCREEN_ORIENTATION_CONTROL для принудительной смены ориентации.
     */
    private fun handlePortMessage(message: Any) {
        val json: JSONObject = when (message) {
            is JSONObject -> message
            is Map<*, *> -> {
                val obj = JSONObject()
                message.forEach { (k, v) ->
                    if (k != null) obj.put(k.toString(), v)
                }
                obj
            }
            else -> {
                Log.w(TAG, "Неизвестный тип сообщения: ${message.javaClass.simpleName}")
                return
            }
        }

        when (json.optString("type")) {
            "SCREEN_ORIENTATION_CONTROL" -> {
                val action = json.optString("action")
                Log.d(TAG, "🔄 Ориентация → $action")
                runOnUiThread {
                    when (action) {
                        "landscape" ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "portrait" ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "unspecified" ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        else ->
                            Log.w(TAG, "Неизвестное действие ориентации: $action")
                    }
                }
            }
            else -> {
                // Ответы от TOGGLE_SCRUBBER_SIDE и RELOAD_SCRIPTS приходят сюда же.
            }
        }
    }

    /**
     * Отправляет в background.js команду переключить сторону скраббера.
     * @return true если команда отправлена, false если порт ещё не готов.
     */
    fun toggleScrubberSide(): Boolean {
        val port = sollorPort
        if (port == null) {
            Log.w(TAG, "Порт ещё не готов — команда не отправлена")
            return false
        }
        val message = JSONObject().apply { put("type", "TOGGLE_SCRUBBER_SIDE") }
        Log.d(TAG, "📤 Отправка через порт: $message")
        return try {
            port.postMessage(message)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "❌ postMessage упал: ${e.message}")
            sollorPort = null
            false
        }
    }

    /**
     * Просит расширение очистить Cache Storage с инжектируемыми скриптами
     * и перезагрузить страницу. Следующий запуск loader.js подтянет свежие
     * sollor.js / sollor.css / cleaner.js с облака.
     */
    fun reloadScripts(): Boolean {
        val port = sollorPort
        if (port == null) {
            Log.w(TAG, "Порт ещё не готов — команда не отправлена")
            return false
        }
        val message = JSONObject().apply { put("type", "RELOAD_SCRIPTS") }
        Log.d(TAG, "📤 Отправка через порт: $message")
        return try {
            port.postMessage(message)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "❌ postMessage упал: ${e.message}")
            sollorPort = null
            false
        }
    }
}