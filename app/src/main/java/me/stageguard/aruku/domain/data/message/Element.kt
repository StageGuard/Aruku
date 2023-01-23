package me.stageguard.aruku.domain.data.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Forward(
    val list: List<Node>
) : MessageElement {
    override fun contentToString(): String {
        return "[转发消息]"
    }

    @Parcelize
    @Serializable
    data class Node(
        val senderId: Long,
        val senderName: String,
        val time: Long,
        val messages: List<MessageElement>
    ) : Parcelable
}

@Parcelize
@Serializable
data class Quote(
    val messageId: Int,
) : MessageElement {
    override fun contentToString(): String {
        return ""
    }
}