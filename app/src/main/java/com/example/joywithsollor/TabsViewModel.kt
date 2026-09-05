package com.example.joywithsollor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

class TabsViewModel : ViewModel() {

    private val _tabs = MutableLiveData<List<Tab>>(emptyList())
    val tabs: LiveData<List<Tab>> = _tabs

    private val _activeIndex = MutableLiveData<Int>(0)
    val activeIndex: LiveData<Int> = _activeIndex

    private var nextId = 1L

    // Инициализация первой вкладки при запуске
    fun initFirstTab(runtime: GeckoRuntime) {
        if (_tabs.value.isNullOrEmpty()) {
            // ЗАМЕНИТЕ НА ВАШУ СТРАНИЦУ
            createTab("https://yaremko.ru/plllay", runtime)
        }
    }

    fun createTab(url: String? = null, runtime: GeckoRuntime) {
        val session = GeckoSession()
        session.open(runtime)
        val tab = Tab(
            id = nextId++,
            session = session,
            title = "New Tab",
            url = url ?: ""
        )
        val current = _tabs.value.orEmpty().toMutableList()
        current.add(tab)
        _tabs.value = current
        val newIndex = current.size - 1
        _activeIndex.value = newIndex
        if (!url.isNullOrEmpty()) {
            session.loadUri(url)
        } else {
            // ЗАМЕНИТЕ НА ВАШУ СТРАНИЦУ
            session.loadUri("https://google.com")
        }
    }

    fun switchTab(index: Int) {
        if (index < 0 || index >= (_tabs.value?.size ?: 0)) return
        _activeIndex.value = index
    }

    fun closeTab(index: Int) {
        val current = _tabs.value.orEmpty().toMutableList()
        if (index < 0 || index >= current.size) return
        val tab = current.removeAt(index)
        tab.session.close()
        _tabs.value = current
        if (current.isEmpty()) {
            // Если закрыли последнюю вкладку — создаём новую с вашей страницей
            onTabsEmpty?.invoke()
            return
        }
        if (_activeIndex.value == index) {
            val newIndex = if (index < current.size) index else current.size - 1
            _activeIndex.value = newIndex
        } else if (_activeIndex.value != null && _activeIndex.value!! > index) {
            _activeIndex.value = _activeIndex.value!! - 1
        }
    }

    var onTabsEmpty: (() -> Unit)? = null

    fun updateTabInfo(index: Int, title: String?, url: String?) {
        val current = _tabs.value.orEmpty().toMutableList()
        if (index < 0 || index >= current.size) return
        val old = current[index]
        val new = old.copy(
            title = title ?: old.title,
            url = url ?: old.url
        )
        current[index] = new
        _tabs.value = current
    }

    fun getActiveTab(): Tab? {
        val list = _tabs.value ?: return null
        val idx = _activeIndex.value ?: 0
        return if (idx in list.indices) list[idx] else null
    }
}