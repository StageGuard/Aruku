package me.stageguard.aruku

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.account.toEntity
import me.stageguard.aruku.database.account.toLoginData
import me.stageguard.aruku.database.contact.ContactEntity
import me.stageguard.aruku.database.contact.toEntity
import me.stageguard.aruku.database.message.AudioUrlEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.database.message.toEntity
import me.stageguard.aruku.database.message.toPreviewEntity
import me.stageguard.aruku.domain.CombinedMessagePagingSource
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.domain.data.message.Audio
import me.stageguard.aruku.service.ServiceConnector
import me.stageguard.aruku.service.bridge.AudioStatusListener
import me.stageguard.aruku.service.bridge.AudioUrlQueryBridge
import me.stageguard.aruku.service.bridge.BotStateObserver
import me.stageguard.aruku.service.bridge.ContactSyncBridge
import me.stageguard.aruku.service.bridge.DisposableBridge
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.bridge.MessageSubscriber
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountInfoImpl
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.AccountState
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.ContactInfo
import me.stageguard.aruku.service.parcel.ContactSyncOp
import me.stageguard.aruku.service.parcel.ContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.service.parcel.Message
import me.stageguard.aruku.util.createAndroidLogger
import me.stageguard.aruku.util.weakReference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class MainRepositoryImpl(
    private val database: ArukuDatabase,
    private val avatarCache: ConcurrentHashMap<Long, String>,
    private val nicknameCache: ConcurrentHashMap<Long, String>
) : MainRepository, LifecycleEventObserver, CoroutineScope by MainScope() {
    private val logger = createAndroidLogger("MainRepositoryImpl")

    private var connectorRef: WeakReference<ServiceConnector>? = null
    private val binder: ServiceBridge? get() = connectorRef?.get()?.binder
    private val mainScope = this
    @OptIn(ExperimentalCoroutinesApi::class)
    private val binderAwaitContext = Dispatchers.IO.limitedParallelism(1)

    /**
     * singleton disposables
     *
     * there several singleton bridges:
     * * [MessageSubscriber]
     * * [LoginSolverBridge]
     * * [ContactSyncBridge]
     * * [BotStateObserver]
     * * [AudioUrlQueryBridge]
     *
     * singleton bridge only has one instance at the whole application(exclude service) lifecycle.
     * it is only instantiated in [setupServiceBridge] and disposed on lifecycle destroy.
     *
     * Note that [BotStateObserver] is managed by terminal caller coroutine of [stateFlow].
     * TODO: move to singletonBridgeDisposables
     *
     */
    private val singletonBridgeDisposables: ConcurrentLinkedQueue<DisposableBridge> = ConcurrentLinkedQueue()

    private val audioStatusListeners: ConcurrentHashMap<String, DisposableBridge> = ConcurrentHashMap()

    override val stateFlow: Flow<Map<Long, AccountState>>
        get() = channelFlow {
            val binder0 = awaitBinder()
            val states: MutableMap<Long, AccountState> = mutableMapOf()

            // send initial state
            send(binder0.getLastBotState())
            // observe state changes
            val disposable = binder0.attachBotStateObserver(BotStateObserver { state ->
                if (state is AccountState.Offline && state.cause == AccountState.OfflineCause.REMOVE_BOT) {
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
            all.forEach { account ->
                logger.i("reading account data ${account.accountNo}.")
                awaitBinder().addBot(account.toLoginData(), !account.isOfflineManually)
            }
        }
    }

    override fun referConnector(connector: ServiceConnector) {
        connectorRef = connector.weakReference()
    }

    private fun setupServiceBridge() = launch {
        val binder0 = awaitBinder()

        binder0.attachContactSyncer(object : ContactSyncBridge {
            override fun onSyncContact(op: ContactSyncOp, account: Long, contacts: List<ContactInfo>) {
                with(mainScope) { database.launchIO {
                    if (op == ContactSyncOp.REFRESH) {
                        val cached = contacts().getContacts(account)
                        val online = contacts.map { it.toEntity(account) }

                        contacts().delete(*(cached - online.toSet()).toTypedArray())
                        contacts().upsert(*online.toTypedArray())
                    }
                    if (op == ContactSyncOp.REMOVE) {
                        contacts().delete(*contacts.map { it.toEntity(account) }.toTypedArray())
                    }
                    if (op == ContactSyncOp.ENTRANCE) {
                        contacts().upsert(*contacts.map { it.toEntity(account) }.toTypedArray())
                    }
                } }
            }

            override fun onUpdateAccountInfo(info: AccountInfo) {
                with(mainScope) { database.launchIO {
                    val account = accounts()[info.accountNo].singleOrNull()
                    if (account != null) accounts().upsert(account.apply {
                        this.nickname = info.nickname
                        this.avatarUrl = info.avatarUrl
                    })
                } }
            }
        }).also { singletonBridgeDisposables.offer(it) }

        binder0.attachAudioQueryBridge(object : AudioUrlQueryBridge {
            // TODO: don't runBlocking
            override fun query(fileMd5: String): String? = runBlocking {
                database.audioUrls().getAudioUrl(fileMd5).singleOrNull()?.url
            }
        }).also { singletonBridgeDisposables.offer(it) }

        binder0.subscribeMessages(object : MessageSubscriber {
            override fun onMessage(message: Message) {
                with(mainScope) { database.launchIO {
                    messageRecords().upsert(message.toEntity())
                    processMessage(message)

                    val existing = messagePreview().getExactMessagePreview(
                        message.account,
                        message.contact.subject,
                        message.contact.type
                    ).singleOrNull()

                    messagePreview().upsert(message.toPreviewEntity(
                        existing?.unreadCount?.plus(1) ?: 1
                    ))
                } }
            }
        }).also { singletonBridgeDisposables.offer(it) }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_CREATE) {
            if (singletonBridgeDisposables.size > 0) {
                logger.w("there are service bridge that hasn't disposed before setup.")
                singletonBridgeDisposables.clear()
            }
            setupServiceBridge()
            logger.i("singleton bridge is setup.")
        }
        if (event == Lifecycle.Event.ON_DESTROY) {
            var disposable = singletonBridgeDisposables.poll()
            while (disposable != null) {
                disposable.dispose()
                disposable = singletonBridgeDisposables.poll()
            }
            logger.i("all singleton bridge is disposed.")
        }
    }



    private suspend fun processMessage(message: Message) {
        val audio = message.message.find { it is Audio } as? Audio
        if(audio != null) database.suspendIO {
            audioUrls().upsert(AudioUrlEntity(audio.fileMd5, audio.url))
        }
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
        val disposable = binder?.attachLoginSolver(solver)
        if (disposable != null) singletonBridgeDisposables.offer(disposable)
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

    override fun attachAudioStatusListener(audioFileMd5: String, listener: AudioStatusListener) {
        assertServiceConnected()
        val disposable = binder?.attachAudioStatusListener(audioFileMd5, listener)
        if (disposable != null) {
            audioStatusListeners[audioFileMd5] = disposable
            logger.i("audio listener for $audioFileMd5 is attached.")
        }
    }

    override fun detachAudioStatusListener(audioFileMd5: String) {
        assertServiceConnected()
        val disposable = audioStatusListeners[audioFileMd5]
        if (disposable != null) {
            disposable.dispose()
            audioStatusListeners.remove(audioFileMd5, disposable)
            logger.i("audio listener for $audioFileMd5 is disposed.")
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
            try {
                emitAll(database.messagePreview().getMessages(account).map { LoadState.Ok(it) })
            } catch (ex: Exception) {
                emit(LoadState.Error(ex))
            }
        }.catch {
            emit(LoadState.Error(it))
        }
    }

    override fun getGroups(account: Long): Flow<LoadState<List<ContactEntity>>> {
        return flow {
            emit(LoadState.Loading())
            try {
                emitAll(database.contacts()
                    .getContactsFlow(account, ContactType.GROUP).map { LoadState.Ok(it) })
            } catch (ex: Exception) {
                emit(LoadState.Error(ex))
            }
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

    override fun getMessageRecords(
        account: Long,
        contact: ContactId,
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

    override fun clearUnreadCount(account: Long, contact: ContactId) {
        val dao = database.messagePreview()
        val preview = dao.getExactMessagePreview(account, contact.subject, contact.type)

        preview.forEach {
            dao.update(it.apply { it.unreadCount = 0 })
        }
    }

    private fun assertServiceConnected() {
        val connector = connectorRef?.get()
        if (connector == null) {
            logger.w("ServiceConnector has been collected by gc.")
            return
        }
        if (connector.connected.value != true) {
            logger.w("ServiceConnector is not connected to service.")
        }
    }

    /**
     * await binder will block whole thread
     * just select a thread from io thread pool ^_^
     */
    private suspend fun awaitBinder(): ServiceBridge {
        return withContext(binderAwaitContext) {
            suspendCancellableCoroutine { cont ->
                var binder0 = binder
                while (cont.isActive && binder0 == null) {
                    binder0 = binder

                }
                if (binder0 != null) {
                    cont.resumeWith(Result.success(binder0))
                } else {
                    cont.resumeWith(Result.failure(Exception("await binder is null")))
                }
            }
        }
    }
}