/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.api.http.adapter.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed class ContactDTO : DTO {
    abstract val id: Long
}*/

@Serializable
@SerialName("Friend")
data class QQDTO(
    val id: Long,
    val nickname: String,
    val remark: String
) : DTO

@Serializable
@SerialName("Stranger")
data class StrangerDTO(
    val id: Long,
    val nickname: String,
    val remark: String,
) : DTO

@Serializable
@SerialName("Member")
data class MemberDTO(
    val id: Long,
    val memberName: String,
    val specialTitle: String,
    val permission: String,
    val joinTimestamp: Int,
    val lastSpeakTimestamp: Int,
    val muteTimeRemaining: Int,
    val group: GroupDTO
) : DTO

@Serializable
@SerialName("Group")
data class GroupDTO(
    val id: Long,
    val name: String,
    val permission: String,
) : DTO

@Serializable
@SerialName("OtherClient")
data class OtherClientDTO(
    val id: Long,
    val platform: String
) : DTO

//@Serializable
//data class ComplexSubjectDTO(
//    override val id: Long,
//    val kind: String
//) : ContactDTO() {
//    constructor(contact: Contact) : this(
//        contact.id, when (contact) {
//            is Stranger -> "Stranger"
//            is Friend -> "Friend"
//            is Group -> "Group"
//            is OtherClient -> "OtherClient"
//            else -> error("Contact type ${contact::class.simpleName} not supported")
//        }
//    )
//}

@Serializable
data class ProfileDTO(
    val nickname: String,
    val email: String,
    val age: Int,
    val level: Int,
    val sign: String,
    val sex: String,
) : DTO