package me.stageguard.aruku.common.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ContactInfo(
    val id: ContactId,
    val name: String? = null,
    val avatarUrl: String? = null,
) : Parcelable