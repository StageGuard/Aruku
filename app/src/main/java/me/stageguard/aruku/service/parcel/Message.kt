package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.domain.data.message.MessageElement

sealed class Message(
    open val account: Long,
    open val contact: ContactId,
    open val sender: Long,
    open val senderName: String,
    open val messageId: Int,
    open val message: List<MessageElement>,
    open val time: Long,
)

@Parcelize
class MessageImpl(
    override val account: Long,
    override val contact: ContactId,
    override val sender: Long,
    override val senderName: String,
    override val messageId: Int,
    override val message: List<MessageElement>,
    override val time: Long,
) : Message(account, contact, sender, senderName, messageId, message, time), Parcelable