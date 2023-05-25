package me.stageguard.aruku.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.domain.RetrofitDownloadService
import me.stageguard.aruku.domain.data.message.calculateMessageId
import me.stageguard.aruku.domain.data.message.getSeqByMessageId
import me.stageguard.aruku.domain.data.message.toMessageElements
import me.stageguard.aruku.service.ArukuLoginSolver.Solution
import me.stageguard.aruku.service.bridge.AudioStatusListener
import me.stageguard.aruku.service.bridge.AudioUrlQueryBridge
import me.stageguard.aruku.service.bridge.BotStateObserver
import me.stageguard.aruku.service.bridge.ContactSyncBridge
import me.stageguard.aruku.service.bridge.DisposableBridge
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.bridge.MessageSubscriber
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.bridge.ServiceBridge_Stub
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountInfoImpl
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.AccountState
import me.stageguard.aruku.service.parcel.AccountState.CaptchaType
import me.stageguard.aruku.service.parcel.AccountState.OfflineCause
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.ContactSyncOp
import me.stageguard.aruku.service.parcel.ContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.service.parcel.Message
import me.stageguard.aruku.service.parcel.MessageImpl
import me.stageguard.aruku.service.parcel.toContactInfo
import me.stageguard.aruku.service.parcel.toGroupMemberInfo
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.createAndroidLogger
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.weakReference
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.Mirai
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.contact.remarkOrNameCardOrNick
import net.mamoe.mirai.event.Listener
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotEvent
import net.mamoe.mirai.event.events.BotJoinGroupEvent
import net.mamoe.mirai.event.events.BotLeaveEvent
import net.mamoe.mirai.event.events.BotOfflineEvent
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.events.FriendAddEvent
import net.mamoe.mirai.event.events.FriendDeleteEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.FriendMessageSyncEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.GroupTempMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.events.StrangerRelationChangeEvent
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.OnlineAudio
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.message.data.sourceOrNull
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.toUHexString
import retrofit2.Retrofit
import xyz.cssxsh.mirai.device.MiraiDeviceGenerator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class ArukuMiraiService : LifecycleService(), CoroutineScope {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 72
        const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuMiraiService"
    }

    private val logger = createAndroidLogger()

    private val retrofitClient by lazy { Retrofit.Builder().baseUrl("http://localhost/").build() }
    private var audioUrlQueryBridge: AudioUrlQueryBridge? = null
    private val audioCache by lazy {
        AudioCache(
            context = coroutineContext,
            cacheFolder = ArukuApplication.INSTANCE.externalCacheDir!!.resolve("audio_cache"),
            audioUrlQueryBridge = audioUrlQueryBridge.weakReference(),
            downloadService = retrofitClient.create(RetrofitDownloadService::class.java)
        )
    }

    private val loginData: ConcurrentHashMap<Long, AccountLoginData> = ConcurrentHashMap()
    private val bots: ConcurrentHashMap<Long, Bot> = ConcurrentHashMap()
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private var stateObserver: BotStateObserver? = null
    private val stateChannel = Channel<AccountState>()
    private val stateCacheQueue: ConcurrentLinkedQueue<AccountState> = ConcurrentLinkedQueue()
    private val lastState: ConcurrentHashMap<Long, AccountState> = ConcurrentHashMap()

    private var loginSolver: LoginSolverBridge? = null
    private val loginSolutionChannel = Channel<Solution>()

    private var contactSyncer: ContactSyncBridge? = null

    private var messageSubscriber: MessageSubscriber? = null
    private val messageCacheQueue: ConcurrentLinkedQueue<Message> = ConcurrentLinkedQueue()

    private val service = this

    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext + SupervisorJob()

    private val serviceBridge = object : ServiceBridge {

        override fun addBot(info: AccountLoginData?, alsoLogin: Boolean): Boolean {
            logger.i("add bot: $info")
            if (info == null) return false
            service.addBot(info)
            if (alsoLogin) service.login(info.accountNo)
            return true
        }

        override fun removeBot(accountNo: Long): Boolean {
            return service.removeBot(accountNo)
        }

        override fun getBots(): List<Long> {
            return service.bots.keys().toList()

        }

        override fun login(accountNo: Long): Boolean {
            return service.login(accountNo)
        }

        override fun logout(accountNo: Long): Boolean {
            return service.logout(accountNo)
        }

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
            return lastState
        }

        override fun attachLoginSolver(solver: LoginSolverBridge): DisposableBridge {
            loginSolver = solver
            return DisposableBridge { loginSolver = null }
        }

        override fun openRoamingQuery(account: Long, contact: ContactId): RoamingQueryBridge? {
            return createRoamingQuerySession(account, contact)
        }

        override fun getAccountOnlineState(account: Long): Boolean {
            return bots[account]?.isOnline ?: false
        }

        override fun getAvatarUrl(account: Long, contact: ContactId): String? {
            val bot = Bot.getInstanceOrNull(account)
            return when (contact.type) {
                ContactType.GROUP -> bot?.getGroup(contact.subject)?.avatarUrl
                ContactType.FRIEND -> bot?.getFriend(contact.subject)?.avatarUrl
                ContactType.TEMP -> bot?.getStranger(contact.subject)?.avatarUrl
            }
        }

        override fun getNickname(account: Long, contact: ContactId): String? {
            return service.getContactNickname(account, contact)
        }

        override fun getGroupMemberInfo(
            account: Long,
            groupId: Long,
            memberId: Long
        ): GroupMemberInfo? {
            return service.getGroupMember(account, groupId, memberId)?.toGroupMemberInfo()
        }

        override fun queryAccountInfo(account: Long): AccountInfo? {
            val bot = Bot.getInstanceOrNull(account)
            return if (bot != null) AccountInfoImpl(
                accountNo = bot.id,
                nickname = bot.nick,
                avatarUrl = bot.avatarUrl
            ) else null
        }

        override fun queryAccountProfile(account: Long): AccountProfile? {
            val bot = Bot.getInstanceOrNull(account) ?: return null
            val profile = runBlocking(Dispatchers.IO) { Mirai.queryProfile(bot, account) }
            return AccountProfile(
                accountNo = bot.id,
                nickname = bot.nick,
                avatarUrl = bot.avatarUrl,
                age = profile.age,
                email = profile.email,
                qLevel = profile.qLevel,
                sign = profile.sign,
                sex = profile.sex.ordinal
            )
        }

        override fun attachAudioQueryBridge(bridge: AudioUrlQueryBridge): DisposableBridge {
            audioUrlQueryBridge = bridge
            return DisposableBridge { audioUrlQueryBridge = null }
        }

        override fun attachAudioStatusListener(
            audioFileMd5: String,
            listener: AudioStatusListener
        ): DisposableBridge {
            audioCache.attachListener(audioFileMd5, listener)
            return DisposableBridge { audioCache.detachListener(audioFileMd5) }
        }

        override fun attachContactSyncer(bridge: ContactSyncBridge): DisposableBridge {
            contactSyncer = bridge
            return DisposableBridge { contactSyncer = null }
        }

        override fun subscribeMessages(bridge: MessageSubscriber): DisposableBridge {
            messageSubscriber = bridge

            launch {
                var message = messageCacheQueue.poll()
                while (message != null) {
                    bridge.onMessage(message)
                    message = messageCacheQueue.poll()
                }
            }

            return DisposableBridge { contactSyncer = null }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return ServiceBridge_Stub(serviceBridge)
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
        logger.i("service is created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        createNotification()

        return START_STICKY
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
        bots.forEach { (_, bot) -> removeBot(bot.id) }
        super.onDestroy()
    }

    private fun addBot(account: AccountLoginData): Boolean {
        loginData[account.accountNo] = account

        if (bots[account.accountNo] != null) {
            removeBot(account.accountNo)
        }
        bots[account.accountNo] = createBot(account)

        if (!stateChannel.trySend(
            AccountState.Offline(account.accountNo, OfflineCause.INIT, null)
        ).isSuccess) {
            logger.w("Failed to send add bot state.")
        }

        return true
    }

    /**
     *  bot job is to hold suspension of bot which is sub job of service,
     *  bot is also the sub job of service.
     *
     *  all coroutines launched by `bot.launch` should be cancelled if bot is closed.
     *
     *  all event listener should be cancelled if bot job is closed.
     *
     *  coroutine scope graph :
     *  ```
     *                    ServiceScope
     *                         |
     *                  +------+------+
     *                  |             |
     *             BotJobScope     BotScope
     *                  |             |
     *            EventChannels   bot.launch
     *  ```
     *
     *  if we want to completely stop a bot, we should cancel bot first,
     *
     *  so that the BotOfflineEvent can broadcast to event channel
     *
     *  at that time the bot job is completed because bot.join() is finished.
     *
     *  then cancel bot job to ensure that event channels and all jobs is stopped.
     */
    private fun login(account: Long): Boolean {
        var bot = bots[account] ?: return false
        if (bot.isOnline) return true
        if (bot.coroutineContext[Job]?.isCancelled == true) {
            logger.w("bot $account is cancelled, renewing bot instance.")
            val info = loginData[account]
            if (info != null) bots[account] = createBot(info).also { bot = it }
        }

        val job = botJobs[account]
        if (job != null && job.isActive) {
            logger.e("bot $account is already online.")
            return true
        }

        var onlineEventSubscriber: Listener<BotOnlineEvent>? = null
        var offlineEventSubscriber: Listener<BotOfflineEvent>? = null
        var botSubscribers: List<Listener<*>>? = null

        botJobs[account] = launch(context = CoroutineExceptionHandler { context, th ->
            botJobs.remove(account)

            val matcher = Regex("create a new bot instance", RegexOption.IGNORE_CASE)
            if (th is IllegalStateException && th.toString().contains(matcher)) {
                logger.e("bot $account isn't recoverable.")
                context[Job]?.cancel()
                onlineEventSubscriber?.cancel()
                offlineEventSubscriber?.cancel()
                stateChannel.trySend(AccountState.Offline(account, OfflineCause.LOGIN_FAILED, th.unwrapMessage()))
                return@CoroutineExceptionHandler
            }

            logger.e("uncaught exception while logging $account.", th)
            stateChannel.trySend(AccountState.Offline(account, OfflineCause.LOGIN_FAILED, th.unwrapMessage()))
        } + SupervisorJob()) {

            onlineEventSubscriber = bot.eventChannel.subscribe<BotOnlineEvent> { event ->
                logger.i("bot ${event.bot.id} login success.")
                stateChannel.send(AccountState.Online(event.bot.id))
                if (bot.isActive) ListeningStatus.LISTENING else ListeningStatus.STOPPED
            }

            offlineEventSubscriber = bot.eventChannel.subscribe { event ->
                val message: String
                var stopSubscription = false

                @OptIn(MiraiExperimentalApi::class)
                val offlineCause = when (event) {
                    is BotOfflineEvent.Active -> {
                        message = "offline manually"
                        stopSubscription = true
                        OfflineCause.SUBJECTIVE
                    }

                    is BotOfflineEvent.Force -> {
                        message = event.message
                        stopSubscription = event.reconnect
                        OfflineCause.FORCE
                    }

                    else -> {
                        message = (event as? BotOfflineEvent.CauseAware)?.cause?.message ?: "dropped"
                        OfflineCause.DISCONNECTED
                    }
                }

                stateChannel.send(AccountState.Offline(event.bot.id, offlineCause, message))
                if (stopSubscription) {
                    onlineEventSubscriber?.cancel()
                    botSubscribers?.forEach(Listener<*>::cancel)
                    ListeningStatus.STOPPED
                } else ListeningStatus.LISTENING
            }

            stateChannel.send(AccountState.Logging(bot.id))
            bot.login()
            botSubscribers = initializeBot(bot)

            bot.join()
        }
        return true
    }

    private fun closeBot(account: Long) {
        bots[account]?.close()
        botJobs[account]?.cancel()
    }

    // should use bot coroutine scope
    private fun initializeBot(bot: Bot): List<Listener<*>> = buildList {
        // ensure that process contacts after syncing complete
        val syncContactLock = Mutex(true)
        // cache contacts and profile
        bot.launch {
            logger.i("syncing contacts of bot ${bot.id} to database.")

            val syncer = contactSyncer
            if (syncer == null) {
                logger.w("contact syncer is null")
                return@launch
            }

            syncer.onSyncContact(ContactSyncOp.REFRESH, bot.id, buildList {
                addAll(bot.groups.map(Group::toContactInfo))
                addAll(bot.friends.map(Friend::toContactInfo))
            })
            syncer.onUpdateAccountInfo(bot.run { AccountInfoImpl(id, nick, avatarUrl) })

            logger.i("sync contact of bot ${bot.id} complete.")
            syncContactLock.unlock()
        }

        // subscribe message events
        add(bot.eventChannel.subscribe<MessageEvent>(coroutineContext = CoroutineExceptionHandler { _, t ->
            if (t is IllegalStateException &&
                t.toString().contains(Regex("UNKNOWN_MESSAGE_EVENT_TYPE"))
            ) {
                val type = t.toString().substringAfter(':').trim()
                logger.w("Unknown message type while listening ${bot.id}: $type")
                return@CoroutineExceptionHandler
            }
            logger.e("Error while subscribing message of ${bot.id}", t)
        }) { event ->
            if (!event.bot.isActive) return@subscribe ListeningStatus.STOPPED

            val contact = ContactId(
                when (event) {
                    is GroupMessageEvent, is GroupMessageSyncEvent -> ContactType.GROUP
                    is FriendMessageEvent, is FriendMessageSyncEvent -> ContactType.FRIEND
                    is GroupTempMessageEvent -> ContactType.TEMP
                    else -> error("UNKNOWN_MESSAGE_EVENT_TYPE: $event")
                }, subject.id
            )

            // process in service coroutine scope to ensure that
            // message must be processed if bot is closed in the middle of processing.
            this@ArukuMiraiService.launch {
                val messageElements = event.message.toMessageElements(subject)
                processMessageChain(event.bot, contact, event.message)

                val message = MessageImpl(
                    account = event.bot.id,
                    contact = contact,
                    sender = event.sender.id,
                    senderName = event.sender.nameCardOrNick,
                    messageId = event.message.source.calculateMessageId(),
                    sequence = event.message.source.ids.first().toLong(),
                    message = messageElements,
                    time = event.time.toLong() * 1000 + (System.currentTimeMillis() % 1000)
                )

                val subscriber = messageSubscriber
                if (subscriber == null) {
                    messageCacheQueue.offer(message)
                    return@launch
                }

                subscriber.onMessage(message)
            }

            return@subscribe ListeningStatus.LISTENING
        }.apply {
            invokeOnCompletion {
                logger.i("message subscriber of bot ${bot.id} is cancelled.")
            }
            logger.i("message subscriber of bot ${bot.id} is started.")
        })

        // sync contact changes
        add(bot.eventChannel.subscribe<BotEvent> { event ->
            if (!event.bot.isActive) return@subscribe ListeningStatus.STOPPED
            if (
                event !is FriendAddEvent &&
                event !is FriendDeleteEvent &&
                event !is BotJoinGroupEvent &&
                event !is BotLeaveEvent &&
                event !is StrangerRelationChangeEvent.Friended
            ) return@subscribe ListeningStatus.LISTENING

            syncContactLock.lock()
            val syncer = contactSyncer

            if(syncer != null) when (event) {
                is FriendAddEvent, is StrangerRelationChangeEvent.Friended -> {
                    val friend = if (event is FriendAddEvent) event.friend
                    else (event as StrangerRelationChangeEvent.Friended).friend

                    syncer.onSyncContact(ContactSyncOp.ENTRANCE, event.bot.id, listOf(friend.toContactInfo()))
                    logger.i("friend added via event, friend=${friend}.")
                }

                is FriendDeleteEvent -> {
                    syncer.onSyncContact(ContactSyncOp.REMOVE, event.bot.id, listOf(event.friend.toContactInfo()))
                    logger.i("friend deleted via event, friend=${event.friend}")
                }

                is BotJoinGroupEvent -> {
                    syncer.onSyncContact(ContactSyncOp.ENTRANCE, event.bot.id, listOf(event.group.toContactInfo()))
                    logger.i("group added via event, friend=${event.group}")
                }

                is BotLeaveEvent -> {
                    syncer.onSyncContact(ContactSyncOp.REMOVE, event.bot.id, listOf(event.group.toContactInfo()))
                    logger.i("group deleted via event, friend=${event.group}")
                }
            } else logger.w("contact syncer is null.")

            syncContactLock.unlock()
            return@subscribe ListeningStatus.LISTENING
        }.apply {
            invokeOnCompletion {
                logger.i("contact syncer subscriber of bot ${bot.id} is cancelled.")
            }
            logger.i("contact syncer subscriber of bot ${bot.id} is started.")
        })
    }

    private fun logout(account: Long): Boolean {
        val bot = bots[account] ?: return false
        if (!bot.isOnline) return true

        val job = botJobs[account]
        if (job == null || !job.isActive) {
            logger.e("bot $account is already offline.")
            return true
        }
        closeBot(account)

        val rm = botJobs.remove(account, job)
        if (!rm) logger.w("bot job $account is not removed after logout.")
        return true
    }

    private fun removeBot(account: Long): Boolean {
        val bot = bots[account] ?: return false
        closeBot(account)

        bots.remove(account, bot)
        botJobs.remove(account)
        return true
    }

    private suspend fun processMessageChain(bot: Bot, subject: ContactId, message: MessageChain) {
        message.forEach {
            if (it is Audio) {
                val fileMd5 = it.fileMd5.toUHexString("")
                val url = (it as OnlineAudio).urlForDownload
                audioCache.appendDownloadJob(fileMd5, url)
            }
        }
    }

    private fun getContactNickname(account: Long, contact: ContactId): String? {
        val bot = Bot.getInstanceOrNull(account)
        return when (contact.type) {
            ContactType.GROUP -> bot?.getGroup(contact.subject)?.name
            ContactType.FRIEND -> bot?.getFriend(contact.subject)?.nick
            ContactType.TEMP -> bot?.getStranger(contact.subject)?.nick
        }
    }

    private fun getGroupMember(
        account: Long,
        groupId: Long,
        memberId: Long
    ): Member? {
        val bot = Bot.getInstanceOrNull(account) ?: return null
        val group = bot.getGroup(groupId) ?: return null
        return group.getMember(memberId)
    }

    private fun createRoamingQuerySession(
        account: Long,
        contact: ContactId
    ): RoamingQueryBridge? {
        if (contact.type != ContactType.GROUP) {
            logger.w("roaming query only support group now.")
            return null
        }
        val bot = bots[account] ?: kotlin.run {
            logger.w("roaming query cannot find bot $account.")
            return null
        }
        val group = bot.getGroup(contact.subject) ?: kotlin.run {
            logger.w("roaming query group ${contact.subject} of bot $account is not found. ")
            return null
        }

        val job = botJobs[account] ?: kotlin.run {
            logger.w("roaming query cannot find bot job $account.")
            return null
        }
        if (!job.isActive) {
            logger.w("roaming query bot job $account is completed or cancelled.")
            return null
        }

        return object : RoamingQueryBridge {
            private val roamingSession by lazy { group.roamingMessages }

            override fun getMessagesBefore(
                messageId: Long,
                count: Int,
                exclude: Boolean
            ): List<Message>? {
                val seq = getSeqByMessageId(messageId)
                logger.d("fetching roaming messages, account=$account, group=$group, calculatedSeq=$seq, count=$count")

                return runBlocking(job) {
                    roamingSession
                        .getMessagesBefore(if (exclude) seq - 1 else seq)
                        .asFlow()
                        .cancellable()
                        .map<MessageChain, Message> { chain ->
                            MessageImpl(
                                account = account,
                                contact = contact,
                                sender = chain.source.fromId,
                                senderName = when(contact.type) {
                                    ContactType.GROUP -> getGroupMember(account, contact.subject, chain.source.fromId)
                                        ?.remarkOrNameCardOrNick ?: chain.source.fromId.toString()
                                    else -> chain.source.fromId.toString()
                                },
                                messageId = chain.source.calculateMessageId(),
                                sequence = chain.source.ids.first().toLong(),
                                time = chain.source.time.toLong() * 1000,
                                message = chain.toMessageElements(group)
                            )
                        }
                        .catch {
                            logger.w("roaming query cannot process current: $it")
                            emit(MessageImpl.ROAMING_INVALID)
                        }
                        .take(count)
                        .toList()
                }
            }

            override fun getLastMessageId(): Long? {
                return runBlocking(job) {
                    runCatching {
                        val msg = roamingSession
                            .getMessagesBefore()
                            .asFlow()
                            .cancellable()
                            .take(1)
                            .toList()

                        return@runCatching msg
                            .firstOrNull()
                            ?.sourceOrNull
                            ?.calculateMessageId()
                    }.onFailure {
                        logger.w("cannot query last message source of $group", it)
                    }.getOrNull()
                }
            }

        }
    }

    private fun createBot(accountInfo: AccountLoginData): Bot {
        val botWorkingDir =
            ArukuApplication.INSTANCE.filesDir.resolve("mirai/${accountInfo.accountNo}/")
        if (!botWorkingDir.exists()) botWorkingDir.mkdirs()

        return BotFactory.newBot(accountInfo.accountNo, accountInfo.passwordMd5) {
            workingDir = botWorkingDir
            parentCoroutineContext = coroutineContext
            protocol = MiraiProtocol.valueOf(accountInfo.protocol)
            autoReconnectOnForceOffline = accountInfo.autoReconnect
            heartbeatStrategy = HeartbeatStrategy.valueOf(accountInfo.heartbeatStrategy)
            heartbeatPeriodMillis = accountInfo.heartbeatPeriodMillis
            heartbeatTimeoutMillis = accountInfo.heartbeatTimeoutMillis
            reconnectionRetryTimes = accountInfo.reconnectionRetryTimes
            statHeartbeatPeriodMillis = accountInfo.statHeartbeatPeriodMillis

            loginSolver = ArukuLoginSolver(stateChannel, loginSolutionChannel)
            botLoggerSupplier = { ArukuMiraiLogger("Bot.${it.id}", true) }
            networkLoggerSupplier = { ArukuMiraiLogger("Net.${it.id}", true) }

            val deviceInfoFile = botWorkingDir.resolve("device.json")
            if (deviceInfoFile.exists()) {
                fileBasedDeviceInfo(deviceInfoFile.absolutePath)
            } else {
                deviceInfo = {
                    MiraiDeviceGenerator().generate().also { generated ->
                        deviceInfoFile.createNewFile()
                        deviceInfoFile.writeText(Json.encodeToString(generated))
                    }
                }
            }
            enableContactCache()
        }
    }

    // dispatch a state to binder bridge
    private fun BotStateObserver.dispatchState(state: AccountState) {
        lastState[state.account] = state
        if (state is AccountState.Offline && state.cause == OfflineCause.REMOVE_BOT) {
            lastState.remove(state.account)
        }

        onDispatch(state)

        if (state is AccountState.Captcha) launch {
            val loginSolver0 = loginSolver ?: run {
                logger.w("Bot produced a captcha state but login solver is not attached.")
                return@launch
            }

            val solution = when (state.type) {
                CaptchaType.PIC -> loginSolver0.onSolvePicCaptcha(state.account, state.data)
                    .run { Solution.PicCaptcha(state.account, this) }

                CaptchaType.SLIDER -> loginSolver0.onSolveSliderCaptcha(
                    state.account,
                    state.data.decodeToString()
                )
                    .run { Solution.SliderCaptcha(state.account, this) }

                CaptchaType.USF -> loginSolver0.onSolveUnsafeDeviceLoginVerify(
                    state.account,
                    state.data.decodeToString()
                ).run { Solution.USFResult(state.account, this) }

                CaptchaType.SMS -> loginSolver0.onSolveSMSRequest(
                    state.account,
                    state.data.decodeToString()
                )
                    .run { Solution.SMSResult(state.account, this) }
            }

            stateChannel.send(AccountState.Logging(state.account))
            loginSolutionChannel.send(solution)
        }
    }

    private fun Throwable.unwrapMessage(): String? {
        if (message != null) return message
        if (cause != null) return cause!!.unwrapMessage()
        if (suppressed.isNotEmpty()) return suppressed.asSequence()
            .map { it.unwrapMessage() }
            .first()
        return null
    }
}