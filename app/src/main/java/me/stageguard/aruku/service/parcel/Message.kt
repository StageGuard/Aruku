package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.domain.data.message.MessageElement

sealed class Message(
    open val account: Long,
    open val contact: ContactId,
    open val sender: Long,
    open val senderName: String,
    open val messageId: Long,
    open val message: List<MessageElement>,
    open val time: Long,
)

@Parcelize
class MessageImpl(
    override val account: Long,
    override val contact: ContactId,
    override val sender: Long,
    override val senderName: String,
    override val messageId: Long,
    override val message: List<MessageElement>,
    override val time: Long,
) : Message(account, contact, sender, senderName, messageId, message, time), Parcelable {
    companion object {
        val ROAMING_INVALID = MessageImpl(
            account = -1,
            contact = ContactId(ContactType.GROUP, -1),
            sender = -1,
            senderName = "",
            messageId = -1,
            message = listOf(),
            time = -1
        )
    }
}