/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.api.http.adapter.internal.dto

import kotlinx.serialization.Serializable

@Serializable
data class MuteDTO(
    val target: Long,
    val memberId: Long = 0,
    val time: Int = 0
) : DTO

@Serializable
data class KickDTO(
    val target: Long,
    val memberId: Long,
    val block: Boolean = false,
    val ms: String = ""
) : DTO

@Serializable
data class ModifyAdminDTO(
    val target: Long,
    val memberId: Long,
    val assign: Boolean,
) : DTO

@Serializable
data class GroupConfigDTO(
    val target: Long,
    val config: GroupDetailDTO
) : DTO

@Serializable
data class GroupDetailDTO(
    val name: String? = null,
    val confessTalk: Boolean? = null,
    val allowMemberInvite: Boolean? = null,
    val autoApprove: Boolean? = null,
    val anonymousChat: Boolean? = null,
    val muteAll: Boolean? = null,
) : DTO

@Serializable
data class MemberTargetDTO(
    val target: Long,
    val memberId: Long
) : DTO

@Serializable
data class MemberMultiTargetDTO(
    val target: Long,
    val memberIds: LongArray?
) : DTO {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemberMultiTargetDTO

        if (target != other.target) return false
        if (!memberIds.contentEquals(other.memberIds)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = target.hashCode()
        result = 31 * result + memberIds.contentHashCode()
        return result
    }
}

@Serializable
data class MemberInfoDTO(
    val target: Long,
    val memberId: Long,
    val info: MemberDetailDTO
) : DTO

@Serializable
data class MemberDetailDTO(
    val name: String? = null,
    val specialTitle: String? = null
) : DTO

