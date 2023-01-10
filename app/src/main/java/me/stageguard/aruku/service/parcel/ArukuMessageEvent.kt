package me.stageguard.aruku.service.parcel

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.util.Into
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource

@Parcelize
data class ArukuMessageEvent(
    val account: Long,
    val type: ArukuContactType,
    val subject: Long,
    val sender: Long, // maybe friend, group or group member
    val senderName: String,
    val message: ArukuMessage
) : Parcelable, Into<MessageRecordEntity> {
    fun readFromParcel(parcel: Parcel) {
        Log.i(tag(), "read from parcel: $parcel")
    }

    override fun into(): MessageRecordEntity {
        return MessageRecordEntity(
            account = this@ArukuMessageEvent.account,
            type = this@ArukuMessageEvent.type,
            subject = this@ArukuMessageEvent.subject,
            sender = this@ArukuMessageEvent.sender,
            senderName = this@ArukuMessageEvent.senderName,
            messageIds = this@ArukuMessageEvent.message.source.ids.joinToString(","),
            messageInternalIds = this@ArukuMessageEvent.message.source.internalIds.joinToString(","),
            message = this@ArukuMessageEvent.message.messages.serializeToMiraiCode(),
            time = this@ArukuMessageEvent.message.source.time
        )
    }
}

@Parcelize
data class ArukuMessage(
    val source: @RawValue MessageSource,
    val messages: @RawValue MessageChain
) : Parcelable

@Parcelize
data class ArukuContact(
    val type: ArukuContactType,
    val subject: Long,
) : Parcelable

@Parcelize
enum class ArukuContactType : Parcelable {
    FRIEND, GROUP, TEMP
}