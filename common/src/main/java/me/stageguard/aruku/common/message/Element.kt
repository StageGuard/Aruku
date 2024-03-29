package me.stageguard.aruku.common.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Forward(
    val list: List<Node>
) : MessageElement, Parcelable {
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
    val messageId: Long,
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return ""
    }
}