package com.example.joywithsollor

import android.util.Log

/** Логи только в отладочной сборке: в релизе ни строк, ни конкатенации. */
object SLog {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }
}
