package me.stageguard.aruku.util

import android.util.Log

class LogWrapper(private val tag: String) {
    fun d(msg: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.d(tag, msg)
        } else {
            Log.d(tag, msg, throwable)
        }
    }

    fun i(msg: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.i(tag, msg)
        } else {
            Log.i(tag, msg, throwable)
        }
    }

    fun w(msg: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, msg)
        } else {
            Log.w(tag, msg, throwable)
        }
    }

    fun e(msg: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag, msg)
        } else {
            Log.e(tag, msg, throwable)
        }
    }
}

fun createAndroidLogger(tag: String) = LogWrapper(tag)
fun Any.createAndroidLogger(tag: String = this::class.java.simpleName) = LogWrapper(tag)
