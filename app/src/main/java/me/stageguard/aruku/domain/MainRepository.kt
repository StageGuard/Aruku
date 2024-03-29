package me.stageguard.aruku.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.common.message.File
import me.stageguard.aruku.common.service.bridge.LoginSolverBridge
import me.stageguard.aruku.common.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.common.service.parcel.AccountProfile
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.GroupMemberInfo
import me.stageguard.aruku.util.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.ContactEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.service.bridge.DelegateBackendBridge
import kotlin.coroutines.CoroutineContext

interface MainRepository {
    val stateFlow: Flow<Map<Long, AccountState>>
    fun referBackendBridge(bridge: DelegateBackendBridge)
    // binder
    fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun deleteBot(accountNo: Long): Boolean
    fun getBots(): List<Long>
    fun login(accountNo: Long): Boolean
    fun logout(accountNo: Long): Boolean
    fun attachLoginSolver(solver: LoginSolverBridge)
    fun openRoamingQuery(account: Long, contact: ContactId): RoamingQueryBridge?
    fun getAccountOnlineState(account: Long): Boolean?
    suspend fun queryAccountInfo(account: Long): AccountInfo?
    fun queryAccountProfile(account: Long): AccountProfile?
    suspend fun getAvatarUrl(account: Long, contact: ContactId): String?
    suspend fun getNickname(account: Long, contact: ContactId): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?

    // database
    suspend fun getAccount(account: Long): AccountEntity?
    suspend fun setAccountOfflineManually(account: Long)
    fun getMessagePreview(account: Long): Flow<LoadState<List<MessagePreviewEntity>>>
    fun getGroups(account: Long): Flow<LoadState<List<ContactEntity>>>
    fun getFriends(account: Long): Flow<LoadState<List<ContactEntity>>>
    fun getMessageRecords(
        account: Long,
        contact: ContactId,
        coroutineContext: CoroutineContext,
    ): Flow<PagingData<MessageRecordEntity>>

    fun updateMessageRecord(vararg records: MessageRecordEntity)

    fun getExactMessageRecord(
        account: Long,
        contact: ContactId,
        messageId: Long,
    ): Flow<List<MessageRecordEntity>>

    // other data sources
    fun clearUnreadCount(account: Long, contact: ContactId)

    fun querySingleMessage(
        account: Long,
        contact: ContactId,
        messageId: Long
    ): Flow<LoadState<MessageRecordEntity>>

    fun queryAudioStatus(audioFileMd5: String): Flow<AudioCache.State>

    fun queryFileStatus(
        account: Long,
        contact: ContactId,
        fileId: String?,
        fileMessageId: Long,
    ): Flow<LoadState<File>>
}

