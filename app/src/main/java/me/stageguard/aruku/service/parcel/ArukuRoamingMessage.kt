package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.domain.data.message.MessageElement

@Parcelize
data class ArukuRoamingMessage(
    val contact: ArukuContact,
    val from: Long,
    val messageId: Int,
    val seq: Int,
    val time: Int,
    val message: List<MessageElement>
) : Parcelable {
    companion object {
        val INVALID = ArukuRoamingMessage(
            contact = ArukuContact(ArukuContactType.GROUP, -1),
            from = -1,
            messageId = -1,
            seq = -1,
            time = -1,
            message = emptyList(),
        )
    }
}