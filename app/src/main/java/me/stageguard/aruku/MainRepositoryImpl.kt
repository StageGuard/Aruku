package me.stageguard.aruku

import android.util.Log
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
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.bridge.BotObserverBridge
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.util.tag
import java.util.concurrent.ConcurrentHashMap

class MainRepositoryImpl(
    private val binder: ServiceBridge?,
    private val database: ArukuDatabase,
    private val avatarCache: ConcurrentHashMap<Long, String>,
    private val nicknameCache: ConcurrentHashMap<Long, String>
) : MainRepository {
    override fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean {
        assertBinderNotNull(binder)
        return binder?.addBot(info, alsoLogin) ?: false
    }

    override fun removeBot(accountNo: Long): Boolean {
        assertBinderNotNull(binder)
        return binder?.removeBot(accountNo) ?: false
    }

    override fun deleteBot(accountNo: Long): Boolean {
        assertBinderNotNull(binder)
        return binder?.deleteBot(accountNo) ?: false
    }

    override fun getBots(): List<Long> {
        assertBinderNotNull(binder)
        return binder?.getBots() ?: listOf()
    }

    override fun loginAll() {
        assertBinderNotNull(binder)
        binder?.loginAll()
    }

    override fun login(accountNo: Long): Boolean {
        assertBinderNotNull(binder)
        return binder?.login(accountNo) ?: false
    }

    override fun logout(accountNo: Long): Boolean {
        assertBinderNotNull(binder)
        return binder?.logout(accountNo) ?: false
    }

    override fun addBotListObserver(identity: String, observer: BotObserverBridge) {
        assertBinderNotNull(binder)
        binder?.addBotListObserver(identity, observer)
    }

    override fun removeBotListObserver(identity: String) {
        assertBinderNotNull(binder)
        binder?.removeBotListObserver(identity)
    }

    override fun addLoginSolver(bot: Long, solver: LoginSolverBridge) {
        assertBinderNotNull(binder)
        binder?.addLoginSolver(bot, solver)
    }

    override fun removeLoginSolver(bot: Long) {
        assertBinderNotNull(binder)
        binder?.removeLoginSolver(bot)
    }

    override fun queryAccountInfo(account: Long): AccountInfo? {
        assertBinderNotNull(binder)
        return try {
            binder?.queryAccountInfo(account)
        } catch (ex: Exception) {
            Log.w(tag(), "cannot get account info of $account: $ex", ex)
            null
        }
    }

    override fun queryAccountProfile(account: Long): AccountProfile? {
        assertBinderNotNull(binder)
        return try {
            binder?.queryAccountProfile(account)
        } catch (ex: Exception) {
            Log.w(tag(), "cannot get account profile of $account: $ex", ex)
            null
        }
    }

    override fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
        val cache = avatarCache[contact.subject]
        return if (cache != null) cache else {
            assertBinderNotNull(binder)
            try {
                binder?.getAvatarUrl(account, contact)?.also {
                    avatarCache[contact.subject] = it
                }
            } catch (ex: Exception) {
                Log.w(tag(), "cannot get avatar url on $contact of bot $account: $ex", ex)
                null
            }
        }

    }

    override fun getNickname(account: Long, contact: ArukuContact): String? {
        val cache = nicknameCache[contact.subject]
        return if (cache != null) cache else {
            assertBinderNotNull(binder)
            try {
                binder?.getNickname(account, contact)?.also {
                    nicknameCache[contact.subject] = it
                }
            } catch (ex: Exception) {
                Log.w(tag(), "cannot get nickname on $contact of bot $account: $ex", ex)
                null
            }
        }
    }

    override fun getGroupMemberInfo(
        account: Long,
        groupId: Long,
        memberId: Long
    ): GroupMemberInfo? {
        assertBinderNotNull(binder)
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

    override fun getAccount(account: Long): AccountEntity? {
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

    private fun assertBinderNotNull(b: ServiceBridge?) {
        if (b == null) Log.w(tag(), "ServiceBridge is null, cannot perform actions.")
    }
}