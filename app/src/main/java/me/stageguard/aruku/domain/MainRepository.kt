package me.stageguard.aruku.domain

import androidx.paging.PagingSource
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.service.IBotListObserver
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import net.mamoe.mirai.message.data.Image

interface MainRepository {
    // binder
    fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun getBots(): List<Long>
    fun loginAll()
    fun login(accountNo: Long): Boolean
    fun addBotListObserver(identity: String, observer: IBotListObserver)
    fun removeBotListObserver(identity: String)
    fun addLoginSolver(bot: Long, solver: ILoginSolver)
    fun removeLoginSolver(bot: Long)
    fun queryAccountProfile(account: Long): AccountInfo?
    fun getAvatarUrl(account: Long, contact: ArukuContact): String?
    fun getNickname(account: Long, contact: ArukuContact): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?

    // database
    fun getAccount(account: Long): AccountEntity?
    fun setAccountOfflineManually(account: Long)
    fun getMessagePreview(account: Long): PagingSource<Int, MessagePreviewEntity>
    fun getGroups(account: Long): PagingSource<Int, GroupEntity>
    fun getFriends(account: Long): PagingSource<Int, FriendEntity>
    fun getMessageRecords(
        account: Long,
        subject: Long,
        type: ArukuContactType
    ): PagingSource<Int, MessageRecordEntity>

    // other data sources
    suspend fun queryImageUrl(image: Image): String?
    fun clearUnreadCount(account: Long, contact: ArukuContact)
}

