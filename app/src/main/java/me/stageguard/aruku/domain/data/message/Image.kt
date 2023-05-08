package me.stageguard.aruku.domain.data.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Image(
    val url: String,
    val uuid: String,
    val width: Int,
    val height: Int,
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return "[图片]"
    }
}

@Parcelize
@Serializable
data class Face(
    val id: Int,
    val name: String,
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return name
    }
}