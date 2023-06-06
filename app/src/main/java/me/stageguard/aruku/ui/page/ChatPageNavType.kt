package me.stageguard.aruku.ui.page

import android.os.Bundle
import android.os.Parcelable
import androidx.navigation.NavType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object ChatPageNavType : NavType<ChatPageNav>(false) {

    override val name: String
        get() = "ChatPageNav"

    override fun put(bundle: Bundle, key: String, value: ChatPageNav) {
        bundle.putParcelable(key, value as Parcelable?)
    }

    @Suppress("DEPRECATION")
    override fun get(bundle: Bundle, key: String): ChatPageNav {
        return bundle[key] as ChatPageNav
    }

    override fun parseValue(value: String): ChatPageNav {
        return Json.decodeFromString(value)
    }
}

@Parcelize
@Serializable
data class ChatPageNav(
    val contact: me.stageguard.aruku.common.service.parcel.ContactId,
    val messageId: Long? = null
) : Parcelable