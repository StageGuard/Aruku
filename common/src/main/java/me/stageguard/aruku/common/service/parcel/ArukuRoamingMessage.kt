package me.stageguard.aruku.common.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ArukuRoamingMessage(
    val contact: ContactId,
    val from: Long,
    val messageId: Int,
    val seq: Int,
    val time: Long,
    val message: List<me.stageguard.aruku.common.message.MessageElement>
) : Parcelable {
    companion object {
        val INVALID = ArukuRoamingMessage(
            contact = ContactId(ContactType.GROUP, -1),
            from = -1,
            messageId = -1,
            seq = -1,
            time = -1,
            message = emptyList(),
        )
    }
}