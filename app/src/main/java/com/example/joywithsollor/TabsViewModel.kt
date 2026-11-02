package com.example.joywithsollor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

class TabsViewModel : ViewModel() {

    private val _tabs = MutableLiveData<List<Tab>>(emptyList())
    val tabs: LiveData<List<Tab>> = _tabs

    private val _activeIndex = MutableLiveData(0)
    val activeIndex: LiveData<Int> = _activeIndex

    private val _closeTick = MutableLiveData(0)
    val closeTick: LiveData<Int> = _closeTick

    private var nextId = 1L

    companion object {
        const val HOME_URL = "https://cntrl.digital/plllay-app"
        private const val NEW_TAB_URL = "https://google.com"
    }

    fun initFirstTab(runtime: GeckoRuntime) {
        if (_tabs.value.isNullOrEmpty()) createTab(HOME_URL, runtime)
    }

    fun createTab(url: String? = null, runtime: GeckoRuntime) {
        val session = GeckoSession()
        session.open(runtime)
        val target = url ?: NEW_TAB_URL
        val tab = Tab(id = nextId++, session = session, title = "New Tab", url = target)
        val list = _tabs.value.orEmpty().toMutableList()
        list.add(tab)
        _tabs.value = list
        _activeIndex.value = list.size - 1
        android.util.Log.d("Sollor", "TAB_CREATE id=" + tab.id + " url=" + target +
                " active=" + _activeIndex.value + " total=" + list.size)
        session.loadUri(target)
    }

    /** Добавить вкладку с уже созданной сессией (Gecko сам загрузит адрес). */
    fun addTabWithSession(session: GeckoSession, url: String) {
        val tab = Tab(id = nextId++, session = session, title = "New Tab", url = url)
        val list = _tabs.value.orEmpty().toMutableList()
        list.add(tab)
        _tabs.value = list
        _activeIndex.value = list.size - 1
        android.util.Log.d("Sollor", "TAB_CREATE id=" + tab.id + " url=" + url +
                " active=" + _activeIndex.value + " total=" + list.size + " (new session)")
    }

    fun switchTab(index: Int) {
        if (index < 0 || index >= (_tabs.value?.size ?: 0)) return
        _activeIndex.value = index
    }

    fun closeTab(index: Int) {
        val list = _tabs.value.orEmpty().toMutableList()
        if (index < 0 || index >= list.size) return

        val activeBefore = _activeIndex.value ?: 0
        val removed = list.removeAt(index)
        GeckoSessionHelper.forget(removed.session)
        removed.session.close()
        _tabs.value = list

        if (list.isEmpty()) {
            onTabsEmpty?.invoke()
            _closeTick.value = (_closeTick.value ?: 0) + 1
            return
        }

        _activeIndex.value = when {
            activeBefore > index -> activeBefore - 1
            activeBefore == index && index < list.size -> index
            activeBefore == index -> list.size - 1
            else -> activeBefore
        }
        _closeTick.value = (_closeTick.value ?: 0) + 1
    }

    var onTabsEmpty: (() -> Unit)? = null

    fun updateTabInfo(index: Int, title: String?, url: String?) {
        val list = _tabs.value.orEmpty().toMutableList()
        if (index < 0 || index >= list.size) return
        val old = list[index]
        list[index] = old.copy(title = title ?: old.title, url = url ?: old.url)
        _tabs.value = list
    }

    fun getActiveTab(): Tab? {
        val list = _tabs.value ?: return null
        val i = _activeIndex.value ?: 0
        return if (i in list.indices) list[i] else null
    }
}
