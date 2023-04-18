package me.stageguard.aruku.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.contact.toFriendEntity
import me.stageguard.aruku.database.contact.toGroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.data.message.calculateMessageId
import me.stageguard.aruku.domain.data.message.contentToString
import me.stageguard.aruku.domain.data.message.toMessageElements
import me.stageguard.aruku.service.ArukuLoginSolver.Solution
import me.stageguard.aruku.service.bridge.*
import me.stageguard.aruku.service.parcel.*
import me.stageguard.aruku.service.parcel.AccountState.CaptchaType
import me.stageguard.aruku.service.parcel.AccountState.OfflineCause
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.createAndroidLogger
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.Mirai
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.EventChannel
import net.mamoe.mirai.event.Listener
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import net.mamoe.mirai.utils.MiraiExperimentalApi
import org.koin.android.ext.android.inject
import xyz.cssxsh.mirai.device.MiraiDeviceGenerator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class ArukuMiraiService : LifecycleService(), CoroutineScope {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 72
        const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuMiraiService"
    }

    private val logger = createAndroidLogger("ArukuMiraiService")

    private val botFactory: BotFactory by inject()
    private val database: ArukuDatabase by inject()
    private val audioCache: AudioCache by inject()

    private val initializeLock = Mutex(true)
    private val bots: ConcurrentHashMap<Long, Bot> = ConcurrentHashMap()
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private var stateObserver: BotStateObserver? = null
    private val stateChannel = Channel<AccountState>()
    private val stateCacheQueue: ConcurrentLinkedQueue<AccountState> = ConcurrentLinkedQueue()
    private val lastState: ConcurrentHashMap<Long, AccountState> = ConcurrentHashMap()

    private var loginSolver: LoginSolverBridge? = null
    private val loginSolutionChannel = Channel<Solution>()

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

        override fun deleteBot(accountNo: Long): Boolean {
            return service.deleteBot(accountNo)
        }

        override fun getBots(): List<Long> {
            return service.bots.keys().toList()

        }

        override fun loginAll() {
            service.bots.forEach { (no, _) -> login(no) }
        }

        override fun login(accountNo: Long): Boolean {
            return service.login(accountNo)
        }

        override fun logout(accountNo: Long): Boolean {
            return service.logout(accountNo)
        }

        override fun attachBotStateObserver(identity: String, observer: BotStateObserver) {
            if (stateObserver != null) logger.w("attaching multiple BotStateObserver.")
            stateObserver = observer

            // dispatch latest cached state of each bot.
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

        override fun detachBotStateObserver() {
            stateObserver = null
        }

        override fun getLastBotState(): Map<Long, AccountState> {
            return lastState
        }

        override fun attachLoginSolver(solver: LoginSolverBridge) {
            loginSolver = solver
        }

        override fun detachLoginSolver() {
            loginSolver = null
        }

        override fun openRoamingQuery(account: Long, contact: ArukuContact): RoamingQueryBridge? {
            return createRoamingQuerySession(account, contact)
        }

        override fun getAccountOnlineState(account: Long): Boolean {
            return bots[account]?.isOnline ?: false
        }

        override fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
            val bot = Bot.getInstanceOrNull(account)
            return when (contact.type) {
                ArukuContactType.GROUP -> bot?.getGroup(contact.subject)?.avatarUrl
                ArukuContactType.FRIEND -> bot?.getFriend(contact.subject)?.avatarUrl
                ArukuContactType.TEMP -> bot?.getStranger(contact.subject)?.avatarUrl
            }
        }

        override fun getNickname(account: Long, contact: ArukuContact): String? {
            val bot = Bot.getInstanceOrNull(account)
            return when (contact.type) {
                ArukuContactType.GROUP -> bot?.getGroup(contact.subject)?.name
                ArukuContactType.FRIEND -> bot?.getFriend(contact.subject)?.nick
                ArukuContactType.TEMP -> bot?.getStranger(contact.subject)?.nick
            }
        }

        override fun getGroupMemberInfo(
            account: Long,
            groupId: Long,
            memberId: Long
        ): GroupMemberInfo? {
            val bot = Bot.getInstanceOrNull(account) ?: return null
            val group = bot.getGroup(groupId) ?: return null
            return group.getMember(memberId)?.toGroupMemberInfo()
        }

        override fun queryAccountInfo(account: Long): AccountInfo? {
            val bot = Bot.getInstanceOrNull(account)
            return if (bot != null) AccountInfo(
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
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return ServiceBridge_Stub(serviceBridge)
    }

    override fun onCreate() {
        super.onCreate()

        launch {
            stateChannel.consumeAsFlow().collect { state ->
                if (stateObserver == null) {
                    // cache state
                    // this often happens that login process has already produced states
                    // but no state bridge to consume.
                    // so we cache state and dispatch all when state bridge is set.
                    stateCacheQueue.offer(state)
                    return@collect
                }
                // dispatch state to bridge directly
                withContext(Dispatchers.Main) { stateObserver?.dispatchState(state) }
            }
        }

        launch {
            val all = database.suspendIO { accounts().getAll() }
            all.forEach { account ->
                logger.i("reading account data ${account.accountNo}.")
                bots.putIfAbsent(account.accountNo, createBot(account.into(), false))
                stateChannel.send(AccountState.Offline(account.accountNo, OfflineCause.INIT, null))
            }
            initializeLock.unlock()
        }
        logger.i("service is created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (ArukuApplication.initialized.get()) {
            launch {
                if (startId == 1) {
                    initializeLock.lock()
                    // on start is called after bind service in service connector
                    bots.forEach { (account, _) ->
                        stateChannel.send(AccountState.Offline(account, OfflineCause.INIT, null))
                    }
                }
                database.suspendIO {
                    val accountDao = accounts()
                    bots.forEach { (account, _) ->
                        val offlineManually = accountDao[account].firstOrNull()?.isOfflineManually
                            ?: return@forEach

                        if (!offlineManually) login(account)

                    }

                }
                if (startId == 1) initializeLock.unlock()
                logger.i("service is started.")
            }
        } else {
            logger.e("ArukuApplication is not initialized yet.")
        }

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
        if (bots[account.accountNo] != null) {
            removeBot(account.accountNo)
        }
        bots[account.accountNo] = createBot(account, true)
        if (!stateChannel.trySend(AccountState.Offline(account.accountNo, OfflineCause.INIT, null)).isSuccess) {
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
        val bot = bots[account] ?: return false
        if (bot.isOnline) return true

        val job = botJobs[account]
        if (job != null && job.isActive) {
            logger.e("bot $account is already online.")
            return true
        }

        launch {
            database.suspendIO { accounts().setManuallyOffline(account, false) }
        }

        var onlineEventSubscriber: Listener<BotOnlineEvent>? = null
        var offlineEventSubscriber: Listener<BotOfflineEvent>? = null

        botJobs[account] = launch(context = CoroutineExceptionHandler { context, th ->
            if (th is LoginFailedException) {
                logger.e("login $account failed.", th)
                stateChannel.trySend(AccountState.Offline(account, OfflineCause.LOGIN_FAILED, th.message))
                return@CoroutineExceptionHandler
            }

            val matcher = Regex("create a new bot instance", RegexOption.IGNORE_CASE)
            if (th is IllegalStateException && th.toString().contains(matcher)) {
                logger.w("bot isn't recoverable, renewing bot and logging.")
                context[Job]?.cancel()
                onlineEventSubscriber?.cancel()
                offlineEventSubscriber?.cancel()
                renewBotAndLogin(account)
                return@CoroutineExceptionHandler
            }

            logger.e("uncaught exception while logging $account.", th)
            stateChannel.trySend(AccountState.Offline(account, OfflineCause.LOGIN_FAILED, th.message))
            removeBot(account)
        } + SupervisorJob()) {
            val scopedEventChannel = bot.eventChannel.parentScope(this)

            onlineEventSubscriber = scopedEventChannel.subscribe<BotOnlineEvent> { event ->
                logger.i("bot ${event.bot.id} login success.")
                stateChannel.send(AccountState.Online(event.bot.id))
                if (event.bot.isActive) ListeningStatus.LISTENING else ListeningStatus.STOPPED
            }

            offlineEventSubscriber = scopedEventChannel.subscribe<BotOfflineEvent> { event ->
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
                        message =
                            (event as? BotOfflineEvent.CauseAware)?.cause?.message ?: "dropped"
                        OfflineCause.DISCONNECTED
                    }
                }

                stateChannel.send(AccountState.Offline(event.bot.id, offlineCause, message))
                if (stopSubscription) ListeningStatus.STOPPED else ListeningStatus.LISTENING
            }

            stateChannel.send(AccountState.Logging(bot.id))
            bot.login()
            initializeBot(bot, scopedEventChannel)

            bot.join()
        }
        return true
    }

    private suspend fun closeBotAndJoin(account: Long) {
        bots[account]?.closeAndJoin()
        botJobs[account]?.cancelAndJoin()
    }

    private fun renewBotAndLogin(account: Long) = launch {
        closeBotAndJoin(account)
        botJobs.remove(account)
        val info = database.suspendIO { database.accounts()[account].singleOrNull() }
        if (info != null) {
            bots[account] = createBot(info.into())
        }
        login(account)
    }

    // should use bot coroutine scope
    private fun initializeBot(bot: Bot, scopedChannel: EventChannel<BotEvent>) {
        // ensure that process contacts after syncing complete
        val syncContactLock = Mutex(true)
        // cache contacts and profile
        bot.launch {
            logger.i("syncing contacts of bot ${bot.id} to database.")
            database.suspendIO {
                val (groupDao, friendDao) = groups() to friends()

                val (onlineGroups, onlineGroupsIds) = bot.groups.run { this to this.map { it.id } }
                val (cachedGroups, cachedGroupsIds) = groupDao.getGroups(bot.id)
                    .run { this to this.map { it.id } }

                val newGroups = onlineGroups.filter { it.id !in cachedGroupsIds }
                val deletedGroups = cachedGroups.filter { it.id !in onlineGroupsIds }

                groupDao.delete(*deletedGroups.toTypedArray())
                groupDao.update(
                    *(onlineGroups - newGroups.toSet()).map(Group::toGroupEntity).toTypedArray()
                )
                groupDao.insert(*newGroups.map(Group::toGroupEntity).toTypedArray())

                val (onlineFriends, onlineFriendsIds) = bot.friends.run { this to this.map { it.id } }
                val (cachedFriends, cachedFriendsIds) = friendDao.getFriends(bot.id)
                    .run { this to this.map { it.id } }

                val newFriends = onlineFriends.filter { it.id !in cachedFriendsIds }
                val deletedFriends = cachedFriends.filter { it.id !in onlineFriendsIds }

                friendDao.delete(*deletedFriends.toTypedArray())
                friendDao.update(
                    *(onlineFriends - newFriends.toSet()).map(Friend::toFriendEntity).toTypedArray()
                )
                friendDao.insert(*newFriends.map(Friend::toFriendEntity).toTypedArray())
            }
            logger.i("sync contact of bot ${bot.id} complete.")
            syncContactLock.unlock()
        }
        // update basic account info
        bot.launch {
            database.suspendIO {
                val accountDao = accounts()
                val account = accountDao[bot.id].first()
                accountDao.update(account.apply {
                    nickname = bot.nick
                    avatarUrl = bot.avatarUrl
                })
            }
        }
        // subscribe message events
        scopedChannel.subscribe<MessageEvent>(coroutineContext = CoroutineExceptionHandler { _, t ->
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

            val contact = ArukuContact(
                when (event) {
                    is GroupMessageEvent, is GroupMessageSyncEvent -> ArukuContactType.GROUP
                    is FriendMessageEvent, is FriendMessageSyncEvent -> ArukuContactType.FRIEND
                    is GroupTempMessageEvent -> ArukuContactType.TEMP
                    else -> error("UNKNOWN_MESSAGE_EVENT_TYPE: $event")
                }, subject.id
            )

            // process in service coroutine scope to ensure that
            // message must be processed if bot is closed in the middle of processing.
            this@ArukuMiraiService.launch {
                val messageElements = event.message.toMessageElements(subject)
                processMessageChain(event.bot, contact, event.message)
                database.suspendIO {
                    // insert message event to records
                    messageRecords().upsert(
                        MessageRecordEntity(
                            account = event.bot.id,
                            contact = contact,
                            sender = event.sender.id,
                            senderName = event.sender.nameCardOrNick,
                            messageId = event.message.source.calculateMessageId(),
                            message = messageElements,
                            time = event.time
                        )
                    )
                    // update message preview
                    val previewDao = messagePreview()
                    previewDao.upsert(
                        MessagePreviewEntity(
                            event.bot.id,
                            contact,
                            event.time.toLong(),
                            buildString {
                                append(event.sender.nameCardOrNick)
                                append(": ")
                                append(messageElements.contentToString())
                            },
                            previewDao.getExactMessagePreview(
                                event.bot.id, contact.subject, contact.type
                            ).singleOrNull()?.unreadCount ?: 1,
                            event.message.source.calculateMessageId()
                        )
                    )
                }
            }

            return@subscribe ListeningStatus.LISTENING
        }

        // subscribe contact changes
        scopedChannel.subscribe<BotEvent> { event ->
            if (!event.bot.isActive) return@subscribe ListeningStatus.STOPPED
            if (
                event !is FriendAddEvent &&
                event !is FriendDeleteEvent &&
                event !is BotJoinGroupEvent &&
                event !is BotLeaveEvent &&
                event !is StrangerRelationChangeEvent.Friended
            ) return@subscribe ListeningStatus.LISTENING

            syncContactLock.lock()
            val tag = this@ArukuMiraiService.tag("Contact")
            when (event) {
                is FriendAddEvent, is StrangerRelationChangeEvent.Friended -> {
                    val friend = if (event is FriendAddEvent) event.friend
                    else {
                        (event as StrangerRelationChangeEvent.Friended).friend
                    }

                    database.suspendIO { friends().upsert(friend.toFriendEntity()) }
                    logger.i("friend added via event, friend=${friend}.")
                }

                is FriendDeleteEvent -> {
                    database.suspendIO { friends().deleteViaId(event.bot.id, event.friend.id) }
                    logger.i("friend deleted via event, friend=${event.friend}")
                }

                is BotJoinGroupEvent -> {
                    database.suspendIO { groups().upsert(event.group.toGroupEntity()) }
                    logger.i("group added via event, friend=${event.group}")
                }

                is BotLeaveEvent -> {
                    database.suspendIO { groups().deleteViaId(event.bot.id, event.groupId) }
                    logger.i("group deleted via event, friend=${event.group}")
                }
            }

            syncContactLock.unlock()
            return@subscribe ListeningStatus.LISTENING
        }
    }

    private fun logout(account: Long): Boolean {
        val bot = bots[account] ?: return false
        if (!bot.isOnline) return true

        val job = botJobs[account]
        if (job == null || !job.isActive) {
            logger.e("bot $account is already offline.")
            return true
        }
        launch {
            closeBotAndJoin(account)

            database.suspendIO { accounts().setManuallyOffline(account, true) }

            val rm = botJobs.remove(account, job)
            if (!rm) logger.w("bot job $account is not removed after logout.")
        }
        return true
    }

    private fun removeBot(account: Long): Boolean {
        val bot = bots[account] ?: return false
        launch { closeBotAndJoin(account) }
        bots.remove(account, bot)
        botJobs.remove(account)
        stateChannel.trySend(AccountState.Offline(account, OfflineCause.REMOVE_BOT, null))
        return true
    }

    private fun deleteBot(account: Long): Boolean {
        val r = removeBot(account)
        launch {
            database.suspendIO {
                val accountDao = accounts()
                val existing = accountDao[account].singleOrNull()
                if (existing != null) accountDao.delete(existing)
            }
        }
        return r
    }

    private fun processMessageChain(bot: Bot, subject: ArukuContact, message: MessageChain) {
        message.forEach {
            if (it is Audio) {
                audioCache.appendDownloadJob(
                    lifecycleScope,
                    ArukuAudio(it.filename, it.fileMd5, it.fileSize, it.codec.formatName),
                    (it as OnlineAudio).urlForDownload
                )
            }
        }
    }

    private fun createRoamingQuerySession(
        account: Long,
        contact: ArukuContact
    ): RoamingQueryBridge? {
        val tag = tag("RoamingQuery")
        if (contact.type != ArukuContactType.GROUP) {
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
                seq: Int,
                count: Int,
                includeSeq: Boolean
            ): List<ArukuRoamingMessage>? {
                logger.d("fetching roaming messages, account=$account, group=$group, seq=$seq, count=$count")
                return runBlocking(job) {
                    runCatching {
                        roamingSession
                            .getMessagesBefore(seq)
                            .asFlow()
                            .cancellable()
                            .map { chain ->
                                ArukuRoamingMessage(
                                    contact = contact,
                                    from = chain.source.fromId,
                                    messageId = chain.source.calculateMessageId(),
                                    seq = chain.source.ids.first(),
                                    time = chain.source.time,
                                    message = chain.toMessageElements(group)
                                )
                            }
                            .catch {
                                logger.w("roaming query cannot process current: $it")
                                emit(ArukuRoamingMessage.INVALID)
                            }
                            .take(count)
                            .toList()
                    }.onFailure {
                        logger.w("cannot query roaming message of $group, seq=$seq, count=$count", it)
                    }.getOrNull()
                }
            }

            override fun getLastMessageSeq(): Int? {
                return runBlocking(job) {
                    runCatching {
                        val msg = roamingSession
                            .getMessagesBefore()
                            .asFlow()
                            .cancellable()
                            .take(1)
                            .toList()

                        msg.firstOrNull()
                            ?.sourceOrNull
                            ?.ids
                            ?.firstOrNull()

                    }.onFailure {
                        logger.w("cannot query last seq of $group", it)
                    }.getOrNull()
                }
            }

        }
    }

    private fun createBot(accountInfo: AccountLoginData, saveAccount: Boolean = false): Bot {
        val botWorkingDir =
            ArukuApplication.INSTANCE.filesDir.resolve("mirai/${accountInfo.accountNo}/")
        if (!botWorkingDir.exists()) botWorkingDir.mkdirs()

        if (saveAccount) launch {
            database.suspendIO {
                val accountDao = accounts()
                val existAccountInfo = accountDao[accountInfo.accountNo]
                if (existAccountInfo.isEmpty()) {
                    accountDao.insert(accountInfo.into())
                } else {
                    accountDao.update(existAccountInfo.single().apply a@{
                        this@a.passwordMd5 = accountInfo.passwordMd5
                        this@a.loginProtocol = accountInfo.protocol
                        this@a.heartbeatStrategy = accountInfo.heartbeatStrategy
                        this@a.heartbeatPeriodMillis = accountInfo.heartbeatPeriodMillis
                        this@a.heartbeatTimeoutMillis = accountInfo.heartbeatTimeoutMillis
                        this@a.statHeartbeatPeriodMillis = accountInfo.statHeartbeatPeriodMillis
                        this@a.autoReconnect = accountInfo.autoReconnect
                        this@a.reconnectionRetryTimes = accountInfo.reconnectionRetryTimes
                    })
                }
            }
        }

        return botFactory.newBot(accountInfo.accountNo, accountInfo.passwordMd5) {
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
                CaptchaType.PIC -> loginSolver0.onSolvePicCaptcha(
                    state.account,
                    state.data
                ).run { Solution.PicCaptcha(state.account, this) }

                CaptchaType.SLIDER -> loginSolver0.onSolveSliderCaptcha(
                    state.account,
                    state.data.decodeToString()
                ).run { Solution.SliderCaptcha(state.account, this) }

                CaptchaType.USF -> loginSolver0.onSolveUnsafeDeviceLoginVerify(
                    state.account,
                    state.data.decodeToString()
                ).run { Solution.USFResult(state.account, this) }

                CaptchaType.SMS -> loginSolver0.onSolveSMSRequest(
                    state.account,
                    state.data.decodeToString()
                ).run { Solution.SMSResult(state.account, this) }
            }

            stateChannel.send(AccountState.Logging(state.account))
            loginSolutionChannel.send(solution)
        }

    }
}