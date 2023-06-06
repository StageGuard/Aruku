package me.stageguard.aruku.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.ui.page.ChatPageNav


fun ContactId.toNavArg(messageId: Long? = null): String {
    return Json.encodeToString(ChatPageNav(this, messageId))
}