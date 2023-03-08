package me.stageguard.aruku.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.service.bridge.AccountStateBridge
import me.stageguard.aruku.service.bridge.BotObserverBridge
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface MainRepository {
    // binder
    fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun deleteBot(accountNo: Long): Boolean
    fun getBots(): List<Long>
    fun loginAll()
    fun login(accountNo: Long): Boolean
    fun logout(accountNo: Long): Boolean
    fun addBotListObserver(identity: String, observer: BotObserverBridge)
    fun removeBotListObserver(identity: String)
    fun setAccountStateBridge(bridge: AccountStateBridge)
    fun openRoamingQuery(account: Long, contact: ArukuContact): RoamingQueryBridge?
    fun getAccountOnlineState(account: Long): Boolean?
    suspend fun queryAccountInfo(account: Long): AccountInfo?
    fun queryAccountProfile(account: Long): AccountProfile?
    suspend fun getAvatarUrl(account: Long, contact: ArukuContact): String?
    suspend fun getNickname(account: Long, contact: ArukuContact): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?

    // database
    suspend fun getAccount(account: Long): AccountEntity?
    suspend fun setAccountOfflineManually(account: Long)
    fun getMessagePreview(account: Long): Flow<LoadState<List<MessagePreviewEntity>>>
    fun getGroups(account: Long): Flow<LoadState<List<GroupEntity>>>
    fun getFriends(account: Long): Flow<LoadState<List<FriendEntity>>>
    fun getMessageRecords(
        account: Long,
        contact: ArukuContact,
        context: CoroutineContext = EmptyCoroutineContext
    ): Flow<PagingData<MessageRecordEntity>>

    // other data sources
    fun clearUnreadCount(account: Long, contact: ArukuContact)
}

