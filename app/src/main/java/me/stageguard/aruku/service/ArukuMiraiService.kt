package me.stageguard.aruku.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.consumeAsFlow
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
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.contact.toFriendEntity
import me.stageguard.aruku.database.contact.toGroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.data.message.calculateMessageId
import me.stageguard.aruku.domain.data.message.toMessageElements
import me.stageguard.aruku.service.ArukuLoginSolver.Solution
import me.stageguard.aruku.service.bridge.AccountStateBridge
import me.stageguard.aruku.service.bridge.AccountStateBridge.OfflineCause
import me.stageguard.aruku.service.bridge.BotObserverBridge
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.bridge.ServiceBridge_Stub
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuAudio
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.ArukuRoamingMessage
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.service.parcel.toGroupMemberInfo
import me.stageguard.aruku.ui.activity.MainActivity
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

    private val botFactory: BotFactory by inject()
    private val database: ArukuDatabase by inject()
    private val audioCache: AudioCache by inject()

    private val initializeLock = Mutex(true)
    private val bots: ConcurrentHashMap<Long, Bot> = ConcurrentHashMap()
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private val botListObservers: MutableMap<String, BotObserverBridge> = mutableMapOf()

    private var stateBridge: AccountStateBridge? = null
    private val stateChannel = Channel<AccountState>()
    private val stateCacheQueue: ConcurrentLinkedQueue<AccountState> = ConcurrentLinkedQueue()
    private val loginSolutionChannel = Channel<Solution>()

    private val service = this

    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext + SupervisorJob()

    private val serviceBridge = object : ServiceBridge {

        override fun addBot(info: AccountLoginData?, alsoLogin: Boolean): Boolean {
            Log.i(service.tag(), "addBot(info=$info)")
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

        override fun addBotListObserver(identity: String, observer: BotObserverBridge) {
            botListObservers[identity] = observer
        }

        override fun removeBotListObserver(identity: String) {
            val lifecycleObserver = service.botListObservers[identity]
            if (lifecycleObserver != null) {
                service.botListObservers.remove(identity, lifecycleObserver)
            }
        }

        fun notifyBotList() {
            val botList = bots.keys().toList()
            botListObservers.forEach { (_, observer) -> observer.onChange(botList) }
        }

        override fun setAccountStateBridge(bridge: AccountStateBridge) {
            stateBridge = bridge

            // dispatch latest cached state of each bot.
            val dispatched = mutableListOf<Long>()
            var state = stateCacheQueue.poll()
            while (state != null) {
                if (state.account !in dispatched) {
                    bridge.dispatchState(state)
                    dispatched.add(state.account)
                }
                state = stateCacheQueue.poll()
            }
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
                if (stateBridge == null) {
                    // cache state
                    // this often happens that login process has already produced states
                    // but no state bridge to consume.
                    // so we cache state and dispatch all when state bridge is set.
                    stateCacheQueue.offer(state)
                    return@collect
                }
                // dispatch state to bridge directly
                stateBridge?.dispatchState(state)
            }
        }

        launch {
            database.suspendIO {
                accounts()
                    .getAll()
                    .forEach { account ->
                        Log.i(service.tag(), "reading account data ${account.accountNo}.")
                        bots.putIfAbsent(account.accountNo, createBot(account.into(), false))
                    }
            }
            serviceBridge.notifyBotList() // may not notify any observer
            initializeLock.unlock()
        }
        Log.i(tag(), "ArukuMiraiService is created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (ArukuApplication.initialized.get()) {
            launch {
                if (startId == 1) {
                    initializeLock.lock()
                    // on start is called after bind service in service connector
                    serviceBridge.notifyBotList()
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
                Log.i(service.tag(), "ArukuMiraiService is started.")
            }
        } else {
            Log.e(tag(), "ArukuApplication is not initialized yet.")
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
        serviceBridge.notifyBotList()
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
            Log.e(tag(), "bot $account is already online.")
            return true
        }

        launch {
            database.suspendIO { accounts().setManuallyOffline(account, false) }
        }

        botJobs[account] = launch(context = CoroutineExceptionHandler { context, th ->
            if (th is LoginFailedException) {
                Log.e(service.tag(), "login $account failed.", th)
                stateChannel.trySend(AccountState.LoginFailed(account, th.killBot, th.message))
                return@CoroutineExceptionHandler
            }

            val matcher = Regex("create a new bot instance", RegexOption.IGNORE_CASE)
            if (th is IllegalStateException && th.toString().contains(matcher)) {
                Log.w(service.tag(), "bot isn't recoverable, renewing bot and logging.")
                context[Job]?.cancel()
                renewBotAndLogin(account)
                return@CoroutineExceptionHandler
            }

            Log.e(service.tag(), "uncaught exception while logging $account.", th)
            stateChannel.trySend(AccountState.LoginFailed(account, true, th.message))
            removeBot(account)
        } + SupervisorJob()) {
            val scopedEventChannel = bot.eventChannel.parentScope(this)

            scopedEventChannel.subscribe<BotOnlineEvent> { event ->
                Log.i(service.tag(), "bot ${event.bot.id} login success.")
                stateChannel.send(AccountState.LoginSuccess(account))
                if (bot.isActive) ListeningStatus.LISTENING else ListeningStatus.STOPPED
            }

            scopedEventChannel.subscribe<BotOfflineEvent> { event ->
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
                stateChannel.send(AccountState.Offline(bot.id, offlineCause, message))
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
            val tag = this@ArukuMiraiService.tag("Contact")
            Log.i(tag, "syncing contacts of bot ${bot.id} to database.")
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
            Log.i(tag, "sync contact of bot ${bot.id} complete.")
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
            val tag = tag("Message")
            if (t is IllegalStateException &&
                t.toString().contains(Regex("UNKNOWN_MESSAGE_EVENT_TYPE"))
            ) {
                val type = t.toString().substringAfter(':').trim()
                Log.w(tag, "Unknown message type while listening ${bot.id}: $type")
                return@CoroutineExceptionHandler
            }
            Log.e(tag, "Error while subscribing message of ${bot.id}", t)
        }) { event ->
            if (!bot.isActive) return@subscribe ListeningStatus.STOPPED

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
                processMessageChain(bot, contact, event.message)
                database.suspendIO {
                    // insert message event to records
                    messageRecords().upsert(
                        MessageRecordEntity(
                            account = bot.id,
                            contact = contact,
                            sender = event.sender.id,
                            senderName = event.sender.nameCardOrNick,
                            messageId = event.message.source.calculateMessageId(),
                            message = messageElements,
                            time = event.time
                        )
                    )
                    // update message preview
                    messagePreview().upsertP(
                        MessagePreviewEntity(
                            event.bot.id,
                            contact,
                            event.time.toLong(),
                            event.sender.nameCardOrNick + ": " + event.message.contentToString(),
                            1,
                            event.message.source.calculateMessageId()
                        )
                    )
                }
            }

            return@subscribe ListeningStatus.LISTENING
        }

        // subscribe contact changes
        scopedChannel.subscribe<BotEvent> { event ->
            if (!bot.isActive) return@subscribe ListeningStatus.STOPPED
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
                    Log.i(tag, "friend added via event, friend=${friend}.")
                }

                is FriendDeleteEvent -> {
                    database.suspendIO { friends().deleteViaId(event.bot.id, event.friend.id) }
                    Log.i(tag, "friend deleted via event, friend=${event.friend}")
                }

                is BotJoinGroupEvent -> {
                    database.suspendIO { groups().upsert(event.group.toGroupEntity()) }
                    Log.i(tag, "group added via event, friend=${event.group}")
                }

                is BotLeaveEvent -> {
                    database.suspendIO { groups().deleteViaId(event.bot.id, event.groupId) }
                    Log.i(tag, "group deleted via event, friend=${event.group}")
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
            Log.e(service.tag(), "bot $account is already offline.")
            return true
        }
        launch {
            closeBotAndJoin(account)

            database.suspendIO { accounts().setManuallyOffline(account, true) }

            val rm = botJobs.remove(account, job)
            if (!rm) Log.w(service.tag(), "bot job $account is not removed after logout.")
        }
        return true
    }

    private fun removeBot(account: Long): Boolean {
        val bot = bots[account] ?: return false
        launch { closeBotAndJoin(account) }
        bots.remove(account, bot)
        botJobs.remove(account)
        serviceBridge.notifyBotList()
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
            Log.w(tag, "roaming query only support group now.")
            return null
        }
        val bot = bots[account] ?: kotlin.run {
            Log.w(tag, "cannot find bot $account.")
            return null
        }
        val group = bot.getGroup(contact.subject) ?: kotlin.run {
            Log.w(tag, "group ${contact.subject} of bot $account is not found. ")
            return null
        }

        val job = botJobs[account] ?: kotlin.run {
            Log.w(tag, "cannot find bot job $account.")
            return null
        }
        if (!job.isActive) {
            Log.w(tag, "bot job $account is completed or cancelled.")
            return null
        }

        return object : RoamingQueryBridge {
            private val roamingSession by lazy { group.roamingMessages }

            override fun getMessagesBefore(
                seq: Int,
                count: Int,
                includeSeq: Boolean
            ): List<ArukuRoamingMessage>? {
                return runBlocking(job) {
                    runCatching {
                        roamingSession
                            .getMessagesBefore(seq)
                            .asFlow()
                            .cancellable()
                            .take(count)
                            .map { chain ->
                                ArukuRoamingMessage(
                                    contact = contact,
                                    from = chain.source.fromId,
                                    messageId = chain.source.calculateMessageId(),
                                    seq = chain.source.ids.first(),
                                    time = chain.source.time,
                                    message = chain.toMessageElements(group)
                                )
                            }.toList()
                    }.onFailure {
                        Log.w(
                            service.tag(),
                            "cannot query roaming message of $group, seq=$seq, count=$count",
                            it
                        )
                    }.getOrNull()
                }
            }

            override fun getLastMessageSeq(): Int? {
                Log.i("RoamingBridge", "start get last msg seq")
                return runBlocking(job) {
                    Log.i("RoamingBridge", "enter runBlocking")
                    runCatching {
                        Log.i("RoamingBridge", "enter runCatching")
                        val msg = roamingSession
                            .getMessagesBefore()
                            .asFlow()
                            .cancellable()
                            .take(1)
                            .toList()
                        Log.i("RoamingBridge", "fetched list")

                        msg.firstOrNull()
                            ?.sourceOrNull
                            ?.ids
                            ?.firstOrNull()

                    }.onFailure {
                        Log.w(
                            service.tag(),
                            "cannot query last seq of $group",
                            it
                        )
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
    private fun AccountStateBridge.dispatchState(state: AccountState) {
        when (state) {
            is AccountState.Logging -> onLogging(state.account)
            is AccountState.LoginSuccess -> onLoginSuccess(state.account)
            is AccountState.LoginFailed ->
                onLoginFailed(state.account, state.botKilled, state.cause)

            is AccountState.Offline ->
                onOffline(state.account, state.cause, state.message)

            is AccountState.Captcha -> when (state.type) {
                AccountState.CaptchaType.PIC -> {
                    val result = onSolvePicCaptcha(state.account, state.data)
                    launch {
                        loginSolutionChannel.send(Solution.PicCaptcha(state.account, result))
                    }
                }

                AccountState.CaptchaType.SLIDER -> {
                    val result = onSolveSliderCaptcha(state.account, state.data.decodeToString())
                    launch {
                        loginSolutionChannel.send(Solution.SliderCaptcha(state.account, result))
                    }
                }

                AccountState.CaptchaType.SMS -> {
                    val result = onSolveSMSRequest(state.account, state.data.decodeToString())
                    launch {
                        loginSolutionChannel.send(Solution.SMSResult(state.account, result))
                    }
                }

                AccountState.CaptchaType.USF -> {
                    val result =
                        onSolveUnsafeDeviceLoginVerify(state.account, state.data.decodeToString())
                    launch {
                        loginSolutionChannel.send(Solution.USFResult(state.account, result))
                    }
                }
            }
        }
    }

    sealed class AccountState(val account: Long) {
        class Logging(account: Long) : AccountState(account)
        class LoginSuccess(account: Long) : AccountState(account)
        class LoginFailed(account: Long, val botKilled: Boolean, val cause: String?) :
            AccountState(account)

        class Offline(account: Long, val cause: String, val message: String?) :
            AccountState(account)

        class Captcha(account: Long, val type: CaptchaType, val data: ByteArray) :
            AccountState(account)

        enum class CaptchaType { PIC, SLIDER, USF, SMS }
    }
}