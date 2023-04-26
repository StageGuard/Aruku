package me.stageguard.aruku

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.CombinedMessagePagingSource
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.ServiceConnector
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.AudioStatusListener
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.util.createAndroidLogger
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class MainRepositoryImpl(
    private val connectorRef: WeakReference<ServiceConnector>,
    private val database: ArukuDatabase,
    private val avatarCache: ConcurrentHashMap<Long, String>,
    private val nicknameCache: ConcurrentHashMap<Long, String>
) : MainRepository {
    private val logger = createAndroidLogger("MainRepositoryImpl")
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

    override fun attachLoginSolver(solver: LoginSolverBridge) {
        assertServiceConnected()
        binder?.attachLoginSolver(solver)
    }

    override fun openRoamingQuery(account: Long, contact: ArukuContact): RoamingQueryBridge? {
        assertServiceConnected()
        return binder?.openRoamingQuery(account, contact)
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
        return database.suspendIO {
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
            logger.w("cannot get account profile of $account: $ex", ex)
            null
        }
    }

    override suspend fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
        val cache = avatarCache[contact.subject]
        if (cache != null) return cache

        var result = binder?.getAvatarUrl(account, contact)
        if (result == null) result = database.suspendIO {
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
        if (result == null) result = database.suspendIO {
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
            logger.w("cannot get member id on group $groupId, member $memberId of bot $account", ex)
            null
        }
    }

    override fun attachAudioStatusListener(audioFileMd5: String, listener: AudioStatusListener) {
        assertServiceConnected()
        binder?.attachAudioStatusListener(audioFileMd5, listener)
    }

    override fun detachAudioStatusListener(audioFileMd5: String) {
        assertServiceConnected()
        binder?.detachAudioStatusListener(audioFileMd5)
    }

    override suspend fun getAccount(account: Long): AccountEntity? {
        return database.suspendIO {
            val result = database.accounts()[account]
            if (result.isEmpty()) null else (result.singleOrNull() ?: result.first().also {
                logger.w("get account $account has multiple results, peaking first.")
            })
        }
    }

    override suspend fun setAccountOfflineManually(account: Long) {
        return database.suspendIO {
            database.accounts().setManuallyOffline(account, true)
        }
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
        contact: ArukuContact,
        context: CoroutineContext,
    ): Flow<PagingData<MessageRecordEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                initialLoadSize = 40,
                enablePlaceholders = false
            ),
            initialKey = 0,
            pagingSourceFactory = { CombinedMessagePagingSource(account, contact, context) }
        ).flow
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
            logger.w("ServiceConnector has been collected by gc.")
            return
        }
        if (connector.connected.value != true) {
            logger.w("ServiceConnector is not connected to service.")
        }
    }
}