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
data class RemoteFileDTO(
    val name: String,
    val id: String? = null,
    val path: String,
    val parent: RemoteFileDTO? = null,
    val contact: GroupDTO,
    val isFile: Boolean,
    val isDictionary: Boolean,
    val isDirectory: Boolean,
    val size: Long,
    val sha1: String?,
    val md5: String?,
    val uploaderId: Long?,
    val uploadTime: Long?,
    val lastModifyTime: Long?,
    val downloadInfo: DownloadInfoDTO? = null,
) : DTO

@Serializable
data class DownloadInfoDTO(
    val sha1: String,
    val md5: String,
    val downloadTimes: Int,
    val uploaderId: Long,
    val uploadTime: Long,
    val lastModifyTime: Long,
    val url: String,
) : DTO


// parameter

@Serializable
abstract class AbstractFileTargetDTO: DTO {
    abstract val id: String
    abstract val path: String?
    abstract val target: Long?
    abstract val group: Long?
    abstract val qq: Long?
}

@Serializable
data class FileTargetDTO(
    override val id: String = "",
    override val path: String? = null,
    override val target: Long? = null,
    override val group: Long? = null,
    override val qq: Long? = null,
): AbstractFileTargetDTO()

@Serializable
data class FileListDTO(
    override val id: String = "",
    override val path: String? = null,
    override val target: Long? = null,
    override val group: Long? = null,
    override val qq: Long? = null,
    val offset: Int = 0,
    val size: Int = Int.MAX_VALUE,
    val withDownloadInfo: Boolean = false,
): AbstractFileTargetDTO()

@Serializable
data class FileInfoDTO(
    override val id: String = "",
    override val path: String? = null,
    override val target: Long? = null,
    override val group: Long? = null,
    override val qq: Long? = null,
    val withDownloadInfo: Boolean = false
): AbstractFileTargetDTO()

@Serializable
data class MkDirDTO(
    override val id: String = "",
    override val path: String? = null,
    override val target: Long? = null,
    override val group: Long? = null,
    override val qq: Long? = null,
    val directoryName: String,
) : AbstractFileTargetDTO()

@Serializable
data class RenameFileDTO(
    override val id: String = "",
    override val path: String? = null,
    override val target: Long? = null,
    override val group: Long? = null,
    override val qq: Long? = null,
    val renameTo: String,
) : AbstractFileTargetDTO()

@Serializable
data class MoveFileDTO(
    override val id: String = "",
    override val path: String? = null,
    override val target: Long? = null,
    override val group: Long? = null,
    override val qq: Long? = null,
    val moveTo: String? = null,
    val moveToPath: String? = null,
) : AbstractFileTargetDTO()
