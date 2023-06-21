package net.mamoe.mirai.api.http.adapter.internal.dto

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementDTO(
    val group: GroupDTO,
    val content: String,
    val senderId: Long,
    val fid: String,
    val allConfirmed: Boolean,
    val confirmedMembersCount: Int,
    val publicationTime: Long,
) : DTO


// parameter
@Serializable
data class AnnouncementListDTO(
    val id: Long,
    val offset: Int = 0,
    val size: Int = Int.MAX_VALUE,
) : DTO

@Serializable
data class AnnouncementDeleteDTO(
    val id: Long,
    val fid: String,
) : DTO

@Serializable
data class PublishAnnouncementDTO(
    val target: Long,
    val content: String,
    val sendToNewMember: Boolean = false,
    val pinned: Boolean = false,
    val showEditCard: Boolean = false,
    val showPopup: Boolean = false,
    val requireConfirmation: Boolean = false,
    val imageUrl: String? = null,
    val imagePath: String? = null,
    val imageBase64: String? = null,
) : DTO