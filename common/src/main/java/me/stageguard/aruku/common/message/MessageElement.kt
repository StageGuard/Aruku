package me.stageguard.aruku.common.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface MessageElement : Parcelable {
    fun contentToString(): String
}

@Suppress("NOTHING_TO_INLINE")
inline fun Collection<MessageElement>.contentToString() =
    joinToString("") { it.contentToString() }