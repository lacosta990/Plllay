package com.example.joywithsollor

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension

class MainActivity : AppCompatActivity() {

    lateinit var runtime: GeckoRuntime
    private lateinit var viewModel: TabsViewModel

    companion object {
        private const val TAG = "Sollor"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.parseColor("#272A30")
        window.navigationBarColor = Color.parseColor("#272A30")

        val settings = GeckoRuntimeSettings.Builder()
            .allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
            .build()

        // Фикс для мобильного интернета (отключаем IPv6)
        settings.arguments.toMutableList().apply {
            add("--setpref=network.dns.disableIPv6=true")
        }

        runtime = GeckoRuntime.create(this, settings)

        registerSollorExtension()

        viewModel = ViewModelProvider(this).get(TabsViewModel::class.java)
        viewModel.initFirstTab(runtime)

        viewModel.onTabsEmpty = {
            viewModel.createTab("https://yaremko.ru/plllay", runtime)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MainFragment())
                .commit()
        }
    }

    private fun registerSollorExtension() {
        val extensionUri = "resource://android/assets/extensions/sollor/"
        val extensionId = "sollor@example.com"

        runtime.webExtensionController.ensureBuiltIn(extensionUri, extensionId)
            .accept(
                { extension ->
                    Log.d(TAG, "✅ Расширение Sollor загружено успешно")
                },
                { error ->
                    Log.e(TAG, "❌ Ошибка загрузки расширения: ${error?.message}")
                }
            )
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}