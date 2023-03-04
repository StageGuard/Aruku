package me.stageguard.aruku

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.ServiceConnector
import me.stageguard.aruku.service.bridge.AccountStateBridge
import me.stageguard.aruku.service.bridge.BotObserverBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.util.tag
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class MainRepositoryImpl(
    private val connectorRef: WeakReference<ServiceConnector>,
    private val database: ArukuDatabase,
    private val avatarCache: ConcurrentHashMap<Long, String>,
    private val nicknameCache: ConcurrentHashMap<Long, String>
) : MainRepository {
    private val binder: ServiceBridge? get() = connectorRef.get()?.binder

    override fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean {
        assertServiceConnected()
        return binder?.addBot(info, alsoLogin) ?: false
    }

    override fun removeBot(accountNo: Long): Boolean {
        assertServiceConnected()
        return binder?.removeBot(accountNo) ?: false
    }

    override fun deleteBot(accountNo: Long): Boolean {
        assertServiceConnected()
        return binder?.deleteBot(accountNo) ?: false
    }

    override fun getBots(): List<Long> {
        assertServiceConnected()
        return binder?.getBots() ?: listOf()
    }

    override fun loginAll() {
        assertServiceConnected()
        binder?.loginAll()
    }

    override fun login(accountNo: Long): Boolean {
        assertServiceConnected()
        return binder?.login(accountNo) ?: false
    }

    override fun logout(accountNo: Long): Boolean {
        assertServiceConnected()
        return binder?.logout(accountNo) ?: false
    }

    override fun addBotListObserver(identity: String, observer: BotObserverBridge) {
        assertServiceConnected()
        binder?.addBotListObserver(identity, observer)
    }

    override fun removeBotListObserver(identity: String) {
        assertServiceConnected()
        binder?.removeBotListObserver(identity)
    }

    override fun setAccountStateBridge(bridge: AccountStateBridge) {
        assertServiceConnected()
        binder?.setAccountStateBridge(bridge)
    }

    override fun getAccountOnlineState(account: Long): Boolean? {
        assertServiceConnected()
        return binder?.getAccountOnlineState(account)
    }

    override suspend fun queryAccountInfo(account: Long): AccountInfo? {
        assertServiceConnected()
        val result = binder?.queryAccountInfo(account)
        if (result != null) {
            return result
        }
        return withContext(Dispatchers.IO) {
            database.accounts()[account].firstOrNull()?.run {
                AccountInfo(accountNo, nickname, avatarUrl)
            }
        }

    }

    override fun queryAccountProfile(account: Long): AccountProfile? {
        assertServiceConnected()
        return try {
            binder?.queryAccountProfile(account)
        } catch (ex: Exception) {
            Log.w(tag(), "cannot get account profile of $account: $ex", ex)
            null
        }
    }

    override suspend fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
        val cache = avatarCache[contact.subject]
        if (cache != null) return cache

        var result = binder?.getAvatarUrl(account, contact)
        if (result == null) result = withContext(Dispatchers.IO) {
            when (contact.type) {
                ArukuContactType.FRIEND ->
                    database.friends().getFriend(account, contact.subject).firstOrNull()?.avatarUrl

                ArukuContactType.GROUP ->
                    database.groups().getGroup(account, contact.subject).firstOrNull()?.avatarUrl

                else -> null
            }
        }
        if (result != null) avatarCache[contact.subject] = result
        return result
    }

    override suspend fun getNickname(account: Long, contact: ArukuContact): String? {
        val cache = nicknameCache[contact.subject]
        if (cache != null) return cache

        var result = binder?.getNickname(account, contact)
        if (result == null) result = withContext(Dispatchers.IO) {
            when (contact.type) {
                ArukuContactType.FRIEND ->
                    database.friends().getFriend(account, contact.subject).firstOrNull()?.name

                ArukuContactType.GROUP ->
                    database.groups().getGroup(account, contact.subject).firstOrNull()?.name

                else -> null
            }
        }
        if (result != null) nicknameCache[contact.subject] = result
        return result
    }

    override fun getGroupMemberInfo(
        account: Long,
        groupId: Long,
        memberId: Long
    ): GroupMemberInfo? {
        assertServiceConnected()
        return try {
            binder?.getGroupMemberInfo(account, groupId, memberId)
        } catch (ex: Exception) {
            Log.w(
                tag(),
                "cannot get member id on group $groupId, member $memberId of bot $account: $ex",
                ex
            )
            null
        }
    }

    override suspend fun getAccount(account: Long): AccountEntity? {
        val result = database.accounts()[account]
        return if (result.isEmpty()) null else (result.singleOrNull() ?: result.first().also {
            Log.w(tag(), "get account $account has multiple results, peaking first.")
        })
    }

    override fun setAccountOfflineManually(account: Long) {
        val dao = database.accounts()
        val results = dao[account]
        results.forEach { it.isOfflineManually = true }
        dao.update(*results.toTypedArray())
    }

    override fun getMessagePreview(account: Long): Flow<LoadState<List<MessagePreviewEntity>>> {
        return flow {
            emit(LoadState.Loading())
            try {
                emitAll(database.messagePreview().getMessages(account).map { LoadState.Ok(it) })
            } catch (ex: Exception) {
                emit(LoadState.Error(ex))
            }
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    override fun getGroups(account: Long): Flow<LoadState<List<GroupEntity>>> {
        return flow {
            emit(LoadState.Loading())
            try {
                emitAll(database.groups().getGroupsFlow(account).map { LoadState.Ok(it) })
            } catch (ex: Exception) {
                emit(LoadState.Error(ex))
            }
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    override fun getFriends(account: Long): Flow<LoadState<List<FriendEntity>>> {
        return flow {
            emit(LoadState.Loading())
            try {
                emitAll(database.friends().getFriendsFlow(account).map { LoadState.Ok(it) })
            } catch (ex: Exception) {
                emit(LoadState.Error(ex))
            }
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    override fun getMessageRecords(
        account: Long,
        subject: Long,
        type: ArukuContactType
    ): Flow<PagingData<MessageRecordEntity>> {
        return Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            database.messageRecords().getMessagesPaging(account, subject, type)
        }.flow
    }

    override fun clearUnreadCount(account: Long, contact: ArukuContact) {
        val dao = database.messagePreview()
        val preview = dao.getExactMessagePreview(account, contact.subject, contact.type)

        preview.forEach {
            dao.update(it.apply { it.unreadCount = 0 })
        }
    }

    private fun assertServiceConnected() {
        val connector = connectorRef.get()
        if (connector == null) {
            Log.w(tag(), "ServiceConnector has been collected by gc.")
            return
        }
        if (connector.connected.value != true) {
            Log.w(tag(), "ServiceConnector is not connected to service.")
        }
    }
}