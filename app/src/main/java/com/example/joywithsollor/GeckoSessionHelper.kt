package com.example.joywithsollor

import org.mozilla.geckoview.GeckoSession

/** GeckoSession не имеет public canGoBack(): храним последнее значение из делегата. */
object GeckoSessionHelper {
    private val back = HashMap<GeckoSession, Boolean>()

    fun onCanGoBackChanged(session: GeckoSession, can: Boolean) { back[session] = can }
    fun canGoBack(session: GeckoSession): Boolean = back[session] ?: false
    fun forget(session: GeckoSession) { back.remove(session) }
}
