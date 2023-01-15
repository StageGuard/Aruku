package me.stageguard.aruku.ui.page

import android.os.Bundle
import android.os.Parcelable
import androidx.navigation.NavType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.service.parcel.ArukuContact

object ChatPageNavType : NavType<ChatPageNav>(false) {

    override val name: String
        get() = "ChatPageNav"

    override fun put(bundle: Bundle, key: String, value: ChatPageNav) {
        val data = Bundle().apply {
            putParcelable("contact", value.contact as Parcelable)
            putLong("messageId", value.messageId ?: -1L)
        }
        bundle.putBundle(key, data)
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    override fun get(bundle: Bundle, key: String): ChatPageNav {
        val data = bundle[key] as Bundle
        return ChatPageNav(
            data["contact"] as ArukuContact,
            (data["messageId"] as? Long).let { if (it == -1L) null else it }
        )
    }

    override fun parseValue(value: String): ChatPageNav {
        return Json.decodeFromString(value)
    }
}

@Parcelize
data class ChatPageNav(
    val contact: ArukuContact,
    val messageId: Long? = null
) : Parcelable