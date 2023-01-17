package me.stageguard.aruku.domain

import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.service.IBotListObserver
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.service.parcel.*
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
    fun getMessagePreview(account: Long): Flow<LoadState<List<MessagePreviewEntity>>>
    fun getGroups(account: Long): Flow<LoadState<List<GroupEntity>>>
    fun getFriends(account: Long): Flow<LoadState<List<FriendEntity>>>
    fun getMessageRecords(
        account: Long,
        subject: Long,
        type: ArukuContactType
    ): Flow<LoadState<List<MessageRecordEntity>>>

    // other data sources
    suspend fun queryImageUrl(image: Image): String?
    fun clearUnreadCount(account: Long, contact: ArukuContact)
}

