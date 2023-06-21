package me.stageguard.aruku.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.message.Audio
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge_Proxy
import me.stageguard.aruku.common.service.bridge.BotStateObserver
import me.stageguard.aruku.common.service.bridge.ContactSyncBridge
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.common.service.bridge.LoginSolverBridge
import me.stageguard.aruku.common.service.bridge.MessageSubscriber
import me.stageguard.aruku.common.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.common.service.parcel.AccountProfile
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.AccountState.OfflineCause
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.ContactInfo
import me.stageguard.aruku.common.service.parcel.ContactSyncOp
import me.stageguard.aruku.common.service.parcel.GroupMemberInfo
import me.stageguard.aruku.common.service.parcel.Message
import me.stageguard.aruku.common.service.parcel.RemoteFile
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.contact.toEntity
import me.stageguard.aruku.database.message.AudioUrlEntity
import me.stageguard.aruku.database.message.toEntity
import me.stageguard.aruku.database.message.toPreviewEntity
import me.stageguard.aruku.domain.RetrofitDownloadService
import me.stageguard.aruku.service.bridge.AudioStateListener
import me.stageguard.aruku.service.bridge.BackendStateListener
import me.stageguard.aruku.service.bridge.DelegateBackendBridge
import me.stageguard.aruku.service.parcel.BackendState
import me.stageguard.aruku.service.parcel.BackendState.FailureReason
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.stringRes
import org.koin.android.ext.android.inject
import retrofit2.Retrofit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

/**
 * service for holding connection of backend service and
 * process bridge-oriented states,
 *
 * ArukuService can hold multiple backend connections,
 * but will it not identify backend to app.
 */
class ArukuService : LifecycleService(), CoroutineScope, ServiceConnection {
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 72
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuService"

        private const val SERVICE_IMPL = "me.stageguard.aruku.BACKEND_SERVICE_IMPLEMENTATION"
    }

    private val logger = createAndroidLogger()
    private val database by inject<ArukuDatabase>()
    private val retrofit by inject<Retrofit>()

    // key is listener hash
    private val listeners: ConcurrentHashMap<Int, BackendStateListener> = ConcurrentHashMap()
    // key is package name
    private var registeredBackend: List<ResolveInfo> by Delegates.notNull()
    private val holders: ConcurrentHashMap<String, BackendServiceHolder> = ConcurrentHashMap()

    // polymorphic account state channel
    private var stateObserver: BotStateObserver? = null
    private val stateChannel = Channel<AccountState>()
    private val stateCacheQueue: ConcurrentLinkedQueue<AccountState> = ConcurrentLinkedQueue()
    private val lastState: ConcurrentHashMap<Long, AccountState> = ConcurrentHashMap()

    private val audioCache: AudioCache = AudioCache(
        context = coroutineContext,
        cacheFolder = ArukuApplication.INSTANCE.externalCacheDir!!.resolve("audio_cache"),
        database = database,
        downloadService = retrofit.create(RetrofitDownloadService::class.java),
    )

    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext + SupervisorJob()

    private val binder = object : ServiceBinder {
        override fun registerBackendStateListener(listener: BackendStateListener): DisposableBridge {
            val listenerHash = listener.hashCode()
            listeners[listenerHash] = listener
            return DisposableBridge { listeners.remove(listenerHash) }
        }

        override fun bindBackendService(packageName: String) {
            this@ArukuService.bindBackendService(packageName)
        }

        override fun getBackendBridge(packageName: String): DelegateBackendBridge? {
            val backendBridge = holders[packageName]?.bridge ?: return null

            return object : DelegateBackendBridge {
                override fun attachBotStateObserver(observer: BotStateObserver): DisposableBridge {
                    if (stateObserver != null) logger.w("attaching multiple BotStateObserver.")
                    stateObserver = observer

                    // dispatch latest cached state of each bot.
                    launch {
                        val dispatched = mutableListOf<Long>()
                        var state = stateCacheQueue.poll()
                        while (state != null) {
                            if (state.account !in dispatched) {
                                observer.dispatchState(state)
                                dispatched.add(state.account)
                            }
                            state = stateCacheQueue.poll()
                        }
                    }
                    return DisposableBridge { stateObserver = null }
                }

                override fun getLastBotState(): Map<Long, AccountState> {
                    if (lastState.isEmpty()) {
                        holders.values.forEach {
                            val bridgeBotStates = it.bridge.getLastBotState()
                            bridgeBotStates.forEach { (bot, state) -> lastState[bot] = state }
                        }
                    }
                    return lastState
                }

                override fun attachAudioListener(fileMd5: String, observer: AudioStateListener): DisposableBridge {
                    audioCache.attachListener(fileMd5, observer::onState)
                    return DisposableBridge { audioCache.detachListener(fileMd5) }
                }

                override fun openRoamingQuery(account: Long, contact: ContactId): RoamingQueryBridge? =
                    backendBridge.openRoamingQuery(account, contact)
                override fun getAccountOnlineState(account: Long) =
                    backendBridge.getAccountOnlineState(account)
                override fun queryAccountInfo(account: Long): AccountInfo? =
                    backendBridge.queryAccountInfo(account)
                override fun queryAccountProfile(account: Long): AccountProfile? =
                    backendBridge.queryAccountProfile(account)
                override fun getAvatarUrl(account: Long, contact: ContactId) =
                    backendBridge.getAvatarUrl(account, contact)
                override fun getNickname(account: Long, contact: ContactId): String? =
                    backendBridge.getNickname(account, contact)
                override fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo? =
                    backendBridge.getGroupMemberInfo(account, groupId, memberId)
                override fun queryFile(account: Long, contact: ContactId, fileId: String): RemoteFile? =
                    backendBridge.queryFile(account, contact, fileId)
                override fun addBot(info: AccountLoginData?, alsoLogin: Boolean): Boolean =
                    backendBridge.addBot(info, alsoLogin)
                override fun removeBot(accountNo: Long): Boolean = backendBridge.removeBot(accountNo)
                override fun getBots(): List<Long> = backendBridge.getBots()
                override fun login(accountNo: Long): Boolean = backendBridge.login(accountNo)
                override fun logout(accountNo: Long) = backendBridge.logout(accountNo)
                override fun attachContactSyncer(bridge: ContactSyncBridge) =
                    throw NotImplementedError("calling method from DelegateBackendBridge returns DisposableBridge is not allowed.")
                override fun attachLoginSolver(solver: LoginSolverBridge) =
                    throw NotImplementedError("calling method from DelegateBackendBridge returns DisposableBridge is not allowed.")
                override fun subscribeMessages(bridge: MessageSubscriber) =
                    throw NotImplementedError("calling method from DelegateBackendBridge returns DisposableBridge is not allowed.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        launch {
            val channelFlow = stateChannel.consumeAsFlow()

            channelFlow.flowOn(Dispatchers.Main).collect { state ->
                if (stateObserver == null) {
                    // cache state
                    // this often happens that login process has already produced states
                    // but no state bridge to consume.
                    // so we cache state and dispatch all when state bridge is set.
                    stateCacheQueue.offer(state)
                    return@collect
                }
                // dispatch state to bridge directly
                stateObserver?.dispatchState(state)
            }
        }

        registeredBackend = packageManager.queryIntentServices(
            Intent().apply { action = SERVICE_IMPL },
            PackageManager.GET_META_DATA
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotification()

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return ServiceBinder_Stub(binder)
    }

    fun bindBackendService(packageName: String) {
        val serviceResolution = registeredBackend.find { it.serviceInfo.packageName == packageName }
            ?: run {
                logger.w("backend service is not found in package $packageName.")
                dispatchBackendState(BackendState.ConnectFailed(packageName, FailureReason.NO_BACKEND_IMPL))
                return
            }

        val holder = holders[packageName]
        // fast path for binding backend service
        if (holder != null && holder.state is BackendState.Connected) {
            dispatchBackendState(holder.state)
            return
        }

        // slow path
        val result = bindService(
            Intent(SERVICE_IMPL).apply {
               component = serviceResolution.serviceInfo.run { ComponentName(this.packageName, name) }
            }, this, Context.BIND_ABOVE_CLIENT)
        logger.i("bind backend service $packageName, result: $result")
        if (!result) dispatchBackendState(BackendState.ConnectFailed(packageName, FailureReason.BIND_SERVICE_FAILED))
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val packageName = name?.packageName
        if (packageName != null && registeredBackend.find { it.serviceInfo.packageName == packageName } != null) {
            if (service == null || !service.isBinderAlive) {
                logger.w("registered backend is connected but bridge binder is null or died.")
                return
            }

            val connectedState = BackendState.Connected(packageName)
            holders[packageName] = BackendServiceHolder(
                packageName = packageName,
                bridge = ArukuBackendBridge_Proxy(service),
                stateConsumer = { stateChannel.trySend(it) },
                onSyncContact = ::syncContact,
                onUpdateAccountInfo = ::updateAccountInfo,
                onMessage = ::receiveMessage
            ).apply { state = connectedState }

            logger.i("backend $packageName is connected.")
            dispatchBackendState(connectedState)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        val packageName = name?.packageName
        if (packageName != null && registeredBackend.find { it.serviceInfo.packageName == packageName } != null) {

            val disconnectedState = BackendState.Disconnected(packageName)
            holders[packageName]?.state = disconnectedState
            holders.remove(packageName)

            logger.i("backend $packageName is disconnected.")
            dispatchBackendState(disconnectedState)
        }
    }

    private fun syncContact(op: ContactSyncOp, account: Long, contacts: List<ContactInfo>) {
        database.launchIO {
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
        }
    }

    private fun updateAccountInfo(info: AccountInfo) {
        database.launchIO {
            val account = accounts()[info.accountNo].singleOrNull()
            if (account != null) accounts().upsert(account.apply {
                this.nickname = info.nickname
                this.avatarUrl = info.avatarUrl
            })
        }
    }

    private fun receiveMessage(message: Message) {
        database.launchIO {
            messageRecords().upsert(message.toEntity())

            val audio = message.message.find { it is Audio } as? Audio
            if(audio != null) database.suspendIO {
                audioUrls().upsert(AudioUrlEntity(audio.fileMd5, audio.url))
            }

            val existing = messagePreview().getExactMessagePreview(
                message.account,
                message.contact.subject,
                message.contact.type
            ).singleOrNull()

            messagePreview().upsert(message.toPreviewEntity(
                existing?.unreadCount?.plus(1) ?: 1
            ))
        }
    }
    @Suppress("JavaMapForEach")
    private fun dispatchBackendState(state: BackendState) {
        listeners.forEach { _, listener -> listener.onState(state) }
    }

    private fun BotStateObserver.dispatchState(state: AccountState) {
        lastState[state.account] = state
        if (state is AccountState.Offline && state.cause == OfflineCause.REMOVE_BOT) {
            lastState.remove(state.account)
        }

        onDispatch(state)
    }

    private fun createNotification() {
        val context = ArukuApplication.INSTANCE.applicationContext
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager


        val existingNotification =
            notificationManager.activeNotifications.find { it.id == FOREGROUND_NOTIFICATION_ID }

        if (existingNotification == null) {
            var channel =
                notificationManager.getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL_ID)

            if (channel == null) {
                channel = NotificationChannel(
                    FOREGROUND_NOTIFICATION_CHANNEL_ID,
                    R.string.app_name.stringRes,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }

                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )

            val notification = Notification.Builder(this, channel.id).apply {
                setContentTitle(R.string.app_name.stringRes)
                setContentText(R.string.service_notification_text.stringRes)
                setSmallIcon(R.drawable.ic_launcher_foreground)
                setContentIntent(pendingIntent)
                setTicker(R.string.service_notification_text.stringRes)
            }.build()

            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        holders.forEach { (packageName, holder) ->
            holder.dispose()
            dispatchBackendState(BackendState.Disconnected(packageName))
        }
        holders.clear()
        try { unbindService(this) } catch (_: Exception) { }
        logger.i("all backend is disconnected via destruction aruku service.")
    }
}