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

/**
 * 通用参数
 */

@Serializable
data class IntIdDTO(
    val id: Int
) : DTO

@Serializable
data class LongTargetDTO(
    val target: Long
) : DTO

@Serializable
data class IntTargetDTO(
    val target: Int
) : DTO

@Serializable
data class NudgeDTO(
    val target: Long,
    val subject: Long,
    val kind: String,
) : DTO


// Some list

@Serializable
class FriendList(
    val data: List<QQDTO>
) : RestfulResult()

@Serializable
class GroupList(
    val data: List<GroupDTO>
) : RestfulResult()

@Serializable
class MemberList(
    val data: List<MemberDTO>
) : RestfulResult()

@Serializable
class RemoteFileList(
    val data: List<RemoteFileDTO>
) : RestfulResult()

@Serializable
class AnnouncementList(
    val data: List<AnnouncementDTO>
) : RestfulResult()
