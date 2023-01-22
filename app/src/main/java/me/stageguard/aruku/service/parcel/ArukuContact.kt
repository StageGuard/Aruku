package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ui.page.ChatPageNav


@Parcelize
@Serializable
data class ArukuContact(
    val type: ArukuContactType,
    val subject: Long,
) : Parcelable {
    fun toNavArg(messageId: Int? = null): String {
        return Json.encodeToString(ChatPageNav(this, messageId))
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + subject.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ArukuContact) return false
        return other.hashCode() == hashCode()
    }
}

@Parcelize
enum class ArukuContactType : Parcelable {
    FRIEND, GROUP, TEMP
}