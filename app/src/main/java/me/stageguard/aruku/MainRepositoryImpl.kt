package me.stageguard.aruku

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.message.File
import me.stageguard.aruku.common.service.bridge.BotStateObserver
import me.stageguard.aruku.common.service.bridge.LoginSolverBridge
import me.stageguard.aruku.common.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.common.service.bridge.suspendIO
import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.AccountInfoImpl
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.common.service.parcel.AccountProfile
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.AccountState.OfflineCause
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.ContactType
import me.stageguard.aruku.common.service.parcel.GroupMemberInfo
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.account.toEntity
import me.stageguard.aruku.database.account.toLoginData
import me.stageguard.aruku.database.contact.ContactEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.database.message.toEntity
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.domain.SequenceRoamingMessageMediator
import me.stageguard.aruku.service.bridge.AudioStateListener
import me.stageguard.aruku.service.bridge.DelegateBackendBridge
import me.stageguard.aruku.util.weakReference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class MainRepositoryImpl(
    private val database: ArukuDatabase,
) : MainRepository, CoroutineScope by MainScope() {
    private val logger = createAndroidLogger()

    private var binderRef: WeakReference<DelegateBackendBridge>? = null
    private val binder: DelegateBackendBridge? get() = binderRef?.get()

    private val mainScope = this

    private val avatarCache = ConcurrentHashMap<Long, String>()
    private val nicknameCache = ConcurrentHashMap<Long, String>()

    override val stateFlow: Flow<Map<Long, AccountState>>
        get() = channelFlow {
            val binder0 = awaitBinder()
            val states: MutableMap<Long, AccountState> = mutableMapOf()

            // send initial state
            send(binder0.getLastBotState())
            // observe state changes
            val disposable = binder0.attachBotStateObserver(BotStateObserver { state ->
                if (state is AccountState.Offline && state.cause == OfflineCause.REMOVE_BOT) {
                    states.remove(state.account)
                    return@BotStateObserver
                }
                states[state.account] = state

                trySendBlocking(states).onFailure { logger.w("Failed to send account states.", it) }
            })

            awaitClose { disposable.dispose() }
        }


    init {
        mainScope.launch {
            val all = database.suspendIO { accounts().getAll() }
            val binder = awaitBinder()

            val existingBotStates = binder.getLastBotState()
            all.forEach { account ->
                if (existingBotStates[account.accountNo] == null) {
                    logger.i("reading account data ${account.accountNo}.")
                    binder.addBot(account.toLoginData(), !account.isOfflineManually)
                }
            }
        }
    }

    override fun referBackendBridge(bridge: DelegateBackendBridge) {
        binderRef = bridge.weakReference()
    }

    override fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean {
        launch { database.suspendIO {
            accounts().upsert(info.toEntity().apply {
                val existing = accounts()[info.accountNo].singleOrNull()
                nickname = existing?.nickname ?: nickname
                avatarUrl = existing?.avatarUrl ?: avatarUrl
            })
        } }
        assertServiceConnected()
        return binder?.addBot(info, alsoLogin) ?: false
    }

    override fun removeBot(accountNo: Long): Boolean {
        assertServiceConnected()
        launch { database.suspendIO {
            val existing = accounts()[accountNo]
            accounts().delete(*existing.toTypedArray())
        } }
        return binder?.removeBot(accountNo) ?: false
    }

    override fun deleteBot(accountNo: Long): Boolean {
        launch { database.suspendIO {
            val accountDao = accounts()
            val existing = accountDao[accountNo].singleOrNull()
            if (existing != null) accountDao.delete(existing)
        } }
        assertServiceConnected()
        return removeBot(accountNo)
    }

    override fun getBots(): List<Long> {
        assertServiceConnected()
        return binder?.getBots() ?: listOf()
    }

    override fun login(accountNo: Long): Boolean {
        launch { database.suspendIO {
            accounts().setManuallyOffline(accountNo, false)
        } }
        assertServiceConnected()
        return binder?.login(accountNo) ?: false
    }

    override fun logout(accountNo: Long): Boolean {
        launch { database.suspendIO {
            accounts().setManuallyOffline(accountNo, true)
        } }
        assertServiceConnected()
        return binder?.logout(accountNo) ?: false
    }

    override fun attachLoginSolver(solver: LoginSolverBridge) {
        assertServiceConnected()
    }

    override fun openRoamingQuery(account: Long, contact: ContactId): RoamingQueryBridge? {
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
                AccountInfoImpl(accountNo, nickname, avatarUrl)
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

    override suspend fun getAvatarUrl(account: Long, contact: ContactId): String? {
        val cache = avatarCache[contact.subject]
        if (cache != null) return cache

        var result = binder?.getAvatarUrl(account, contact)
        if (result == null) result = database.suspendIO {
            database.contacts().getContact(account, contact.subject, contact.type).firstOrNull()?.avatarUrl
        }
        if (result != null) avatarCache[contact.subject] = result
        return result
    }

    override suspend fun getNickname(account: Long, contact: ContactId): String? {
        val cache = nicknameCache[contact.subject]
        if (cache != null) return cache

        var result = binder?.getNickname(account, contact)
        if (result == null) result = database.suspendIO {
            database.contacts().getContact(account, contact.subject, contact.type).firstOrNull()?.name
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
            emitAll(database.messagePreview().getMessages(account).map { LoadState.Ok(it) })
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    override fun getGroups(account: Long): Flow<LoadState<List<ContactEntity>>> {
        return flow {
            emit(LoadState.Loading())
            emitAll(database.contacts().getContactsFlow(account, ContactType.GROUP).map { LoadState.Ok(it) })
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    override fun getFriends(account: Long): Flow<LoadState<List<ContactEntity>>> {
        return flow {
            emit(LoadState.Loading())
            try {
                emitAll(database.contacts()
                    .getContactsFlow(account, ContactType.FRIEND).map { LoadState.Ok(it) })
            } catch (ex: Exception) {
                emit(LoadState.Error(ex))
            }
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getMessageRecords(
        account: Long,
        contact: ContactId,
        coroutineContext: CoroutineContext,
    ): Flow<PagingData<MessageRecordEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            remoteMediator = SequenceRoamingMessageMediator(
                account, contact, database,
                coroutineContext, ::openRoamingQuery
            ),
            pagingSourceFactory = {
                database.messageRecords().getMessagesPaging(account, contact.subject, contact.type)
            }
        ).flow
    }

    override fun getExactMessageRecord(
        account: Long,
        contact: ContactId,
        messageId: Long,
    ): Flow<List<MessageRecordEntity>> {
        return database.messageRecords()
            .getExactMessage(account, contact.subject, contact.type, messageId)
    }

    override fun clearUnreadCount(account: Long, contact: ContactId) {
        val dao = database.messagePreview()
        val preview = dao.getExactMessagePreview(account, contact.subject, contact.type)

        preview.forEach {
            dao.update(it.apply { it.unreadCount = 0 })
        }
    }

    /**
     * audio status is live status.
     */
    override fun queryAudioStatus(audioFileMd5: String): Flow<AudioCache.State> = channelFlow {
        val deferred = CompletableDeferred<Unit>()
        val binder0 = awaitBinder()

        send(AudioCache.State.Preparing(0.0))
        val disposable = binder0.attachAudioListener(audioFileMd5, object : AudioStateListener {
            override fun onState(state: AudioCache.State) { trySend(state) }
        })

        invokeOnClose {
            disposable.dispose()
            deferred.complete(Unit)
        }

        deferred.await()
    }

    override fun querySingleMessage(
        account: Long,
        contact: ContactId,
        messageId: Long
    ): Flow<LoadState<MessageRecordEntity>> {
        val dbSourceDeferred = CompletableDeferred<LoadState<MessageRecordEntity>>()
        // load from database
        val dbSourceJob = launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
            database.suspendIO {
                messageRecords().getExactMessage(account, contact.subject, contact.type, messageId)
            }.cancellable().catch {
                dbSourceDeferred.complete(LoadState.Error(it))
            }.collect { cache ->
                val state: LoadState<MessageRecordEntity> = if (cache.isEmpty()) {
                    LoadState.Error(EmptyMessageRecordException())
                } else LoadState.Ok(cache.first())

                dbSourceDeferred.complete(state)
            }
        }

        return flow {
            emit(LoadState.Loading())
            dbSourceJob.start()

            runCatching {
                withTimeout(100) {
                    val result = dbSourceDeferred.await()
                    if (result is LoadState.Error) throw result.throwable

                    emit(result)
                    logger.i("query single message $messageId replied from database source.")
                }
            }.onFailure { th ->
                if (th !is TimeoutCancellationException && th !is EmptyMessageRecordException) {
                    logger.w("error while awaiting query single message from db source.", th)
                    emit(LoadState.Error(th))
                    return@onFailure
                }

                val roaming = openRoamingQuery(account, contact)
                if (roaming == null) {
                    logger.w(
                        "error while creating roaming query session to query single message.",
                        th
                    )
                    emit(LoadState.Error(th))
                    return@onFailure
                }

                val remoteSource = roaming.suspendIO { getMessagesBefore(messageId, 1, false) }
                if (remoteSource.isNullOrEmpty()) {
                    emit(LoadState.Error(IllegalStateException("remote query single message is null.")))
                    return@onFailure
                }

                val entity = remoteSource.first().toEntity()
                database.suspendIO { messageRecords().upsert(entity) }
                emit(LoadState.Ok(entity))
                logger.i("query single message $messageId replied from remote source.")
            }
            dbSourceJob.cancel()
        }.onCompletion {
            dbSourceJob.cancelAndJoin()
        }
    }

    override fun queryFileStatus(
        account: Long,
        contact: ContactId,
        fileId: String?,
        fileMessageId: Long,
    ): Flow<LoadState<File>> = channelFlow {
        send(LoadState.Loading())
        val dbSourceDeferred = CompletableDeferred<Unit>()

        getExactMessageRecord(account, contact, fileMessageId)
            .cancellable()
            .take(1)
            .collect {
                val message = it.singleOrNull() ?: return@collect
                val fileElement = message.message.singleOrNull() ?: return@collect
                if (fileElement !is File) return@collect

                dbSourceDeferred.complete(Unit)
                logger.i("query file status of message $fileMessageId replied from database source.")
                send(LoadState.Ok(fileElement))
            }

        launch(Dispatchers.IO) {
            if(fileId == null) {
                val roaming = openRoamingQuery(account, contact)
                if (roaming == null) {
                    if (!dbSourceDeferred.isCompleted)
                        send(LoadState.Error(Throwable("cannot open roaming query session.")))
                    return@launch
                }

                val message = roaming.getMessagesBefore(fileMessageId, 1, exclude = false)
                if (message.isNullOrEmpty()) {
                    if (!dbSourceDeferred.isCompleted)
                        send(LoadState.Error(Throwable("query file message is empty.")))
                    return@launch
                }

                val file = message.first().message.singleOrNull() as? File
                if (file == null) {
                    if (!dbSourceDeferred.isCompleted)
                        send(LoadState.Error(Throwable("query file message is empty.")))
                    return@launch
                }

                logger.i("query file status of message $fileMessageId replied from roaming message.")
                send(LoadState.Ok(file))
            } else {
                val remoteFile = awaitBinder().queryFile(account, contact, fileId)
                if (remoteFile == null) {
                    if (!dbSourceDeferred.isCompleted)
                        send(LoadState.Error(Throwable("query file is empty.")))
                    return@launch
                }

                val file = File(
                    id = remoteFile.id,
                    url = remoteFile.url,
                    name = remoteFile.name,
                    md5 = remoteFile.md5,
                    extension = remoteFile.extension,
                    size = remoteFile.size,
                    expiryTime = remoteFile.expiryTime,
                )

                getExactMessageRecord(account, contact, fileMessageId)
                    .cancellable()
                    .take(1)
                    .collect {
                        val message = it.singleOrNull() ?: return@collect
                        database.messageRecords().upsert(message.copy(message = listOf(file)))
                    }

                logger.i("query file status of message $fileMessageId replied from service.")
                send(LoadState.Ok(file))
            }
        }

    }

    private fun assertServiceConnected() {
        val connector = binderRef?.get()
        if (connector == null) {
            logger.w("ServiceConnector has been collected by gc.")
            return
        }
    }

    /**
     * await binder will block whole thread
     */
    private suspend fun awaitBinder(): DelegateBackendBridge {
        return withContext(ArukuApplication.INSTANCE.binderAwaitContext) {
            suspendCancellableCoroutine { cont ->
                var binder0 = binder
                while (cont.isActive && binder0 == null) binder0 = binder

                if (binder0 != null) {
                    cont.resumeWith(Result.success(binder0))
                } else {
                    cont.resumeWith(Result.failure(Exception("await binder is null")))
                }
            }
        }
    }

    private class EmptyMessageRecordException() : Exception("message record is not found from database source")
}