package me.stageguard.aruku.util

fun Any.toLogTag(module: String? = null) = "[Aruku] ${module ?: this::class.java.simpleName} "