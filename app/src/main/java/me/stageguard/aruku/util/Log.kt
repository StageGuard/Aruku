package me.stageguard.aruku.util

import android.util.Log

fun Any.toLogTag(module: String? = null) = "[Aruku] ${module ?: this::class.java.simpleName} "

fun <T> T.log(tag: String = "debug") = apply {
    this.toString().logE(tag)
}

fun String?.logI(tag: String) {
    Log.i(tag, this ?: "null")
}

fun String?.logE(tag: String) {
    Log.e(tag, this ?: "null")
}