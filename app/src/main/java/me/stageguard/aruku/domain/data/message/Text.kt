package me.stageguard.aruku.domain.data.message

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import me.stageguard.aruku.domain.data.MessageElement

@Parcelize
@Serializable
data class PlainText(
    val text: String,
) : MessageElement {
    override fun contentToString(): String {
        return text
    }
}

@Parcelize
@Serializable
data class At(
    val target: Long,
    val display: String,
) : MessageElement {
    override fun contentToString(): String {
        return "@$display"
    }
}

@Parcelize
@Serializable
object AtAll : MessageElement {
    override fun contentToString(): String {
        return "@所有人"
    }
}