package com.example.joywithsollor

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ExtensionEntry(
    val id: String,
    val name: String,
    val version: String = "",
    val description: String = "",
    val url: String? = null,
    val installed: Boolean = false,
    val enabled: Boolean = true
)

/** Каталог подписанных .xpi с сервера + установка/обновление/удаление. */
class ExtensionsManager private constructor(
    private val activity: Activity,
    private val runtime: GeckoRuntime
) {
    private val controller: WebExtensionController get() = runtime.webExtensionController

    private val _entries = MutableLiveData<List<ExtensionEntry>>(emptyList())
    val entries: LiveData<List<ExtensionEntry>> = _entries

    private val _busy = MutableLiveData<String?>(null)
    val busy: LiveData<String?> = _busy

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun clearMessage() { _message.value = null }

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ext-io").apply { isDaemon = true } }
    private val installed = LinkedHashMap<String, WebExtension>()
    private val catalog = LinkedHashMap<String, ExtensionEntry>()

    companion object {
        private const val TAG = "Sollor"
        const val CATALOG_URL = "https://storage.yandexcloud.net/sollor/extensions/index.json"

        @Volatile
        private var instance: ExtensionsManager? = null

        fun get(activity: Activity, runtime: GeckoRuntime): ExtensionsManager =
            instance ?: synchronized(this) {
                instance ?: ExtensionsManager(activity, runtime).also { instance = it }
            }
    }

    init {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>
            ): org.mozilla.geckoview.GeckoResult<WebExtension.PermissionPromptResponse>? =
                org.mozilla.geckoview.GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(true, false)
                )
        }
        refreshInstalled()
    }

    fun loadCatalog() {
        _busy.value = "Загружаю каталог…"
        io.execute {
            val result = runCatching { fetch(CATALOG_URL) }
            activity.runOnUiThread {
                result.onSuccess { parse(it) }
                    .onFailure { _message.value = "Каталог недоступен: ${it.message}" }
                _busy.value = null
                publish()
            }
        }
    }

    private fun parse(text: String) {
        try {
            val arr = org.json.JSONObject(text).optJSONArray("extensions") ?: org.json.JSONArray()
            catalog.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isBlank()) continue
                catalog[id] = ExtensionEntry(
                    id = id,
                    name = o.optString("name", id),
                    version = o.optString("version"),
                    description = o.optString("description"),
                    url = o.optString("url").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            _message.value = "Ошибка каталога: ${e.message}"
        }
    }

    fun install(entryId: String) {
        val entry = catalog[entryId] ?: return
        val url = entry.url ?: run { _message.value = "Нет URL"; return }
        _busy.value = "Устанавливаю ${entry.name}…"
        controller.install(url).accept({ ext: WebExtension? ->
            _busy.value = null
            if (ext == null) { _message.value = "Пустой ответ"; return@accept }
            installed[ext.id] = ext
            _message.value = "${entry.name} установлено"
            refreshInstalled()
        }, { error: Throwable? ->
            _busy.value = null
            _message.value = "Не установилось: ${describe(error)}"
            publish()
        })
    }

    fun update(entryId: String) {
        val ext = installed[entryId] ?: return
        _busy.value = "Обновляю…"
        controller.update(ext).accept({ updated: WebExtension? ->
            _busy.value = null
            _message.value = if (updated == null) "Обновлений нет" else "Обновлено"
            refreshInstalled()
        }, {
            _busy.value = null
            _message.value = "Ошибка обновления"
            refreshInstalled()
        })
    }

    fun updateAll() {
        val list = installed.values.toList()
        if (list.isEmpty()) { _message.value = "Ничего не установлено"; return }
        _busy.value = "Проверяю обновления…"
        var done = 0
        list.forEach { ext ->
            controller.update(ext).accept({ _: WebExtension? ->
                if (++done >= list.size) { _busy.value = null; refreshInstalled() }
            }, {
                if (++done >= list.size) { _busy.value = null; refreshInstalled() }
            })
        }
    }

    fun uninstall(entryId: String) {
        val ext = installed[entryId] ?: return
        _busy.value = "Удаляю…"
        controller.uninstall(ext).accept({
            installed.remove(entryId)
            _busy.value = null
            _message.value = "Удалено"
            refreshInstalled()
        }, { _: Throwable? ->
            _busy.value = null
            _message.value = "Не удалилось"
        })
    }

    fun setEnabled(entryId: String, enabled: Boolean) {
        val ext = installed[entryId] ?: return
        val res = if (enabled) controller.enable(ext, WebExtensionController.EnableSource.USER)
        else controller.disable(ext, WebExtensionController.EnableSource.USER)
        res.accept({ refreshInstalled() }, { _: Throwable? -> })
    }

    fun refreshInstalled() {
        controller.list().accept({ list: List<WebExtension>? ->
            installed.clear()
            list?.forEach { if (!it.isBuiltIn) installed[it.id] = it }
            publish()
        }, { publish() })
    }

    private fun publish() {
        val out = ArrayList<ExtensionEntry>()
        for (ext in installed.values) {
            val meta = ext.metaData
            val cat = catalog[ext.id]
            out += ExtensionEntry(
                id = ext.id,
                name = cat?.name ?: meta?.name ?: ext.id,
                version = meta?.version ?: "",
                description = cat?.description ?: meta?.description ?: "",
                url = cat?.url,
                installed = true,
                enabled = meta?.enabled ?: true
            )
        }
        for (e in catalog.values) {
            if (installed.containsKey(e.id)) continue
            out += e
        }
        _entries.value = out
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun describe(error: Throwable?): String {
        val code = (error as? WebExtension.InstallException)?.code
        return when (code) {
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE -> "нет сети"
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED -> "нужна подпись Mozilla"
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE -> "несовместимо"
            else -> error?.message ?: "неизвестная ошибка"
        }
    }
}
