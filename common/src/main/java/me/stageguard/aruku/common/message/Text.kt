package me.stageguard.aruku.common.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class PlainText(
    val text: String,
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return text
    }
}

@Parcelize
@Serializable
data class At(
    val target: Long,
    val display: String,
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return "@$display"
    }
}

@Parcelize
@Serializable
object AtAll : MessageElement, Parcelable {
    override fun contentToString(): String {
        return "@所有人"
    }
}