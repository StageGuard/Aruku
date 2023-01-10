package me.stageguard.aruku.ui.page

import android.os.Bundle
import android.os.Parcelable
import androidx.navigation.NavType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.service.parcel.ArukuContact

object ArukuContactNavType : NavType<ArukuContact>(false) {

    override val name: String
        get() = "ArukuContact"

    override fun put(bundle: Bundle, key: String, value: ArukuContact) {
        bundle.putParcelable(key, value as Parcelable?)
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    override fun get(bundle: Bundle, key: String): ArukuContact {
        return bundle[key] as ArukuContact
    }

    override fun parseValue(value: String): ArukuContact {
        return Json.decodeFromString(value)
    }
}