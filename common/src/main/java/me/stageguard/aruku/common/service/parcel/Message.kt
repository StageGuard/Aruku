package me.stageguard.aruku.common.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class Message(
    open val account: Long,
    open val contact: ContactId,
    open val sender: Long,
    open val senderName: String,
    open val messageId: Long,
    open val sequence: Long,
    open val message: List<me.stageguard.aruku.common.message.MessageElement>,
    open val time: Long,
)

@Parcelize
class MessageImpl(
    override val account: Long,
    override val contact: ContactId,
    override val sender: Long,
    override val senderName: String,
    override val messageId: Long,
    override val sequence: Long,
    override val message: List<me.stageguard.aruku.common.message.MessageElement>,
    override val time: Long,
) : Message(account, contact, sender, senderName, messageId, sequence, message, time), Parcelable {
    companion object {
        val ROAMING_INVALID = MessageImpl(
            account = -1,
            contact = ContactId(ContactType.GROUP, -1),
            sender = -1,
            senderName = "",
            messageId = -1,
            message = listOf(),
            sequence = -1,
            time = -1
        )
    }
}