package com.example.joywithsollor

import org.mozilla.geckoview.GeckoSession

data class Tab(
    val id: Long,
    val session: GeckoSession,
    var title: String = "New Tab",
    var url: String = ""
)