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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
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
import me.stageguard.aruku.service.bridge.BotObserverBridge
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.bridge.ServiceBridge_Stub
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuAudio
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.service.parcel.toGroupMemberInfo
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.tag
import me.stageguard.aruku.util.weakReference
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.Mirai
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotEvent
import net.mamoe.mirai.event.events.BotJoinGroupEvent
import net.mamoe.mirai.event.events.BotLeaveEvent
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.event.events.FriendAddEvent
import net.mamoe.mirai.event.events.FriendDeleteEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupTempMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.events.StrangerRelationChangeEvent
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.OnlineAudio
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import org.koin.android.ext.android.inject
import xyz.cssxsh.mirai.device.MiraiDeviceGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    private val loginSolvers: MutableMap<Long, LoginSolverBridge> = mutableMapOf()

    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext + SupervisorJob()

    private val bridge = object : ServiceBridge {
        override fun addBot(info: AccountLoginData?, alsoLogin: Boolean): Boolean {
            Log.i(tag(), "addBot(info=$info)")
            if (info == null) return false
            this@ArukuMiraiService.addBot(info)
            if (alsoLogin) this@ArukuMiraiService.login(info.accountNo)
            return true
        }

        override fun removeBot(accountNo: Long): Boolean {
            return this@ArukuMiraiService.removeBot(accountNo)
        }

        override fun deleteBot(accountNo: Long): Boolean {
            return this@ArukuMiraiService.deleteBot(accountNo)
        }

        override fun getBots(): List<Long> {
            return this@ArukuMiraiService.bots.keys().toList()

        }

        override fun loginAll() {
            this@ArukuMiraiService.bots.forEach { (no, _) -> login(no) }
        }

        override fun login(accountNo: Long): Boolean {
            return this@ArukuMiraiService.login(accountNo)
        }

        override fun logout(accountNo: Long): Boolean {
            return this@ArukuMiraiService.logout(accountNo)
        }

        override fun addBotListObserver(identity: String, observer: BotObserverBridge) {
            botListObservers[identity] = observer
        }

        override fun removeBotListObserver(identity: String) {
            val lifecycleObserver = this@ArukuMiraiService.botListObservers[identity]
            if (lifecycleObserver != null) {
                this@ArukuMiraiService.botListObservers.remove(identity, lifecycleObserver)
            }
        }

        fun notifyBotList() {
            val botList = bots.keys().toList()
            botListObservers.forEach { (_, observer) -> observer.onChange(botList) }
        }

        override fun addLoginSolver(bot: Long, solver: LoginSolverBridge) {
            loginSolvers[bot] = solver
        }

        override fun removeLoginSolver(bot: Long) {
            loginSolvers.remove(bot)
        }

        override fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
            val bot = Bot.getInstanceOrNull(account)
            return when (contact.type) {
                ArukuContactType.GROUP -> {
                    if (bot != null) {
                        bot.getGroup(contact.subject)?.avatarUrl
                    } else runBlocking {
                        databaseIO {
                            groups().getGroup(account, contact.subject).firstOrNull()?.avatarUrl
                        }
                    }
                }

                ArukuContactType.FRIEND -> {
                    if (bot != null) {
                        bot.getFriend(contact.subject)?.avatarUrl
                    } else runBlocking {
                        databaseIO {
                            friends().getFriend(account, contact.subject).firstOrNull()?.avatarUrl
                        }
                    }
                }

                ArukuContactType.TEMP -> bot?.getStranger(contact.subject)?.avatarUrl
            }
        }

        override fun getNickname(account: Long, contact: ArukuContact): String? {
            val bot = Bot.getInstanceOrNull(account)
            return when (contact.type) {
                ArukuContactType.GROUP -> {
                    if (bot != null) {
                        bot.getGroup(contact.subject)?.name
                    } else runBlocking {
                        databaseIO {
                            groups().getGroup(account, contact.subject).firstOrNull()?.name
                        }
                    }
                }

                ArukuContactType.FRIEND -> {
                    if (bot != null) {
                        bot.getFriend(contact.subject)?.nick
                    } else runBlocking {
                        databaseIO {
                            friends().getFriend(account, contact.subject).firstOrNull()?.name
                        }
                    }
                }

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
            ) else runBlocking {
                databaseIO {
                    val data = accounts()[account].firstOrNull()
                    if (data == null) null else AccountInfo(
                        accountNo = data.accountNo,
                        nickname = data.nickname,
                        avatarUrl = data.avatarUrl
                    )
                }
            }
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
        return ServiceBridge_Stub(bridge)
    }

    override fun onCreate() {
        super.onCreate()
        val service = this
        runBlocking {
            databaseIO {
                accounts()
                    .getAll()
                    .forEach { account ->
                        Log.i(service.tag(), "reading account data ${account.accountNo}.")
                        bots.putIfAbsent(account.accountNo, createBot(account.into(), false))
                    }
            }
            bridge.notifyBotList() // may not notify any observer
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
                    bridge.notifyBotList()
                }
                databaseIO {
                    val accountDao = accounts()
                    bots.forEach { (account, _) ->
                        val offlineManually = accountDao[account].firstOrNull()?.isOfflineManually
                            ?: return@forEach

                        if (!offlineManually) login(account)

                    }

                }
                if (startId == 1) initializeLock.unlock()
            }
            Log.i(tag(), "ArukuMiraiService is started.")
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
        bridge.notifyBotList()
        return true
    }

    private fun login(account: Long): Boolean {
        val targetBot = bots[account] ?: return false
        if (targetBot.isOnline) return true

        val existingBotJob = botJobs[account]
        if (existingBotJob != null && existingBotJob.isActive) {
            Log.e(tag(), "bot $account is already online.")
            return true
        }

        launch {
            databaseIO {
                val accountDao = accounts()
                val accountInfo = accountDao[account].singleOrNull()
                if (accountInfo != null) accountDao.update(accountInfo.apply {
                    isOfflineManually = false
                })
            }
        }

        botJobs[account] = launch(context = CoroutineExceptionHandler { _, th ->
            if (th is LoginFailedException) {
                Log.e(tag(), "login $account failed.", th)
                loginSolvers[account]?.onLoginFailed(account, th.killBot, th.message)
            } else if (th is IllegalStateException) {
                val matcher = Regex("create a new Bot instance", RegexOption.IGNORE_CASE)
                if (th.toString().contains(matcher)) {
                    Log.w(tag(), "bot closed, recreating new bot instance and logging.")
                    removeBot(account)
                    launch(Dispatchers.IO + CoroutineExceptionHandler { _, innerTh ->
                        Log.e(tag(), "uncaught exception while logging $account.", innerTh)
                        loginSolvers[account]?.onLoginFailed(account, true, innerTh.message)
                        removeBot(account)
                    }) {
                        val accountInfo = database.accounts()[account].singleOrNull()
                        if (accountInfo != null && !accountInfo.isOfflineManually) {
                            addBot(accountInfo.into())
                            login(account)
                        }
                    }
                } else {
                    Log.e(tag(), "uncaught exception while logging $account.", th)
                    loginSolvers[account]?.onLoginFailed(account, true, th.message)
                    removeBot(account)
                }
            } else {
                Log.e(tag(), "uncaught exception while logging $account.", th)
                loginSolvers[account]?.onLoginFailed(account, true, th.message)
                removeBot(account)
            }
        }) {
            val eventChannel = targetBot.eventChannel.parentScope(this)

            eventChannel.subscribeOnce<BotOnlineEvent> {
                withContext(this@ArukuMiraiService.coroutineContext) {
                    Log.i(this@ArukuMiraiService.tag(), "bot ${it.bot.id} login success.")
                    loginSolvers[account]?.onLoginSuccess(account)
                }
            }

            targetBot.login()
            initializeLoggedBot(targetBot)

            targetBot.join()
        }
        return true
    }

    // should use bot coroutine scope
    private fun initializeLoggedBot(bot: Bot) {
        // cache contacts and profile
        val syncContactLock = Mutex(true)
        bot.launch {
            Log.i(tag("Contact"), "syncing contacts to database.")
            databaseIO {
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
            Log.i(tag("Contact"), "sync contacts complete.")
            syncContactLock.unlock()
        }
        // update basic account info
        bot.launch {
            databaseIO {
                val accountDao = accounts()
                val account = accountDao[bot.id].first()
                accountDao.update(
                    account.copy(
                        nickname = bot.nick,
                        avatarUrl = bot.avatarUrl
                    )
                )
            }
        }
        // subscribe events
        bot.launch {
            bot.eventChannel.parentScope(this)
                .subscribe<MessageEvent>(coroutineContext = CoroutineExceptionHandler { _, throwable ->
                    if (throwable is IllegalStateException && throwable.toString()
                            .contains(Regex("UNKNOWN_MESSAGE_EVENT_TYPE"))
                    ) {
                        Log.w(tag("Message"), "Unknown message type while listening ${bot.id}")
                    } else {
                        Log.e(
                            tag("Message"),
                            "Error while subscribing message of ${bot.id}",
                            throwable
                        )
                    }
                }) { event ->
                    if (!this@ArukuMiraiService.isActive) return@subscribe ListeningStatus.STOPPED

                    val contact = ArukuContact(
                        when (event) {
                            is GroupMessageEvent -> ArukuContactType.GROUP
                            is FriendMessageEvent -> ArukuContactType.FRIEND
                            is GroupTempMessageEvent -> ArukuContactType.TEMP
                            else -> error("UNKNOWN_MESSAGE_EVENT_TYPE")
                        }, subject.id
                    )

                    val messageElements = event.message.toMessageElements(this@subscribe)
                    processMessageChain(bot, contact, event.message)
                    databaseIO {
                        // insert message event to records
                        messageRecords().insert(
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
                        val preview = messagePreview()
                        val messagePreview = preview.getExactMessagePreview(
                            event.bot.id,
                            contact.subject,
                            contact.type
                        )
                        if (messagePreview.isEmpty()) {
                            preview.insert(
                                MessagePreviewEntity(
                                    event.bot.id,
                                    contact,
                                    event.time.toLong(),
                                    event.sender.nameCardOrNick + ": " + event.message.contentToString(),
                                    1,
                                    event.message.source.calculateMessageId()
                                )
                            )
                        } else {
                            preview.update(messagePreview.single().apply p@{
                                this@p.time = event.time.toLong()
                                this@p.previewContent =
                                    event.sender.nameCardOrNick + ": " + event.message.contentToString()
                                this@p.unreadCount = this@p.unreadCount + 1
                                this@p.messageId = event.message.source.calculateMessageId()
                            })
                        }

                    }

                    return@subscribe ListeningStatus.LISTENING
                }

            // subscribe contact changes
            bot.eventChannel.subscribe<BotEvent> { event ->
                if (!this@launch.isActive) return@subscribe ListeningStatus.STOPPED
                if (
                    event !is FriendAddEvent &&
                    event !is FriendDeleteEvent &&
                    event !is BotJoinGroupEvent &&
                    event !is BotLeaveEvent &&
                    event !is StrangerRelationChangeEvent.Friended
                ) return@subscribe ListeningStatus.LISTENING

                syncContactLock.lock()
                when (event) {
                    is FriendAddEvent, is StrangerRelationChangeEvent.Friended -> {
                        val friend =
                            if (event is FriendAddEvent) event.friend else (event as StrangerRelationChangeEvent.Friended).friend

                        databaseIO {
                            val friendDao = friends()
                            val existing = friendDao.getFriend(event.bot.id, friend.id)
                            if (existing.isEmpty()) {
                                friendDao.insert(friend.toFriendEntity())
                            } else {
                                friendDao.update(existing.single().apply e@{
                                    this@e.name = friend.nick
                                    this@e.group = friend.friendGroup.id
                                })
                            }
                        }
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "friend added via event, friend=${friend}"
                        )
                    }

                    is FriendDeleteEvent -> {
                        databaseIO {
                            val friendDao = friends()
                            friendDao.deleteViaId(event.bot.id, event.friend.id)
                        }
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "friend deleted via event, friend=${event.friend}"
                        )
                    }

                    is BotJoinGroupEvent -> {
                        databaseIO {
                            val groupDao = groups()
                            val existing = groupDao.getGroup(event.bot.id, event.groupId)
                            if (existing.isEmpty()) {
                                groupDao.insert(event.group.toGroupEntity())
                            } else {
                                groupDao.update(existing.single().apply e@{
                                    this@e.name = event.group.name
                                })
                            }
                        }
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "group added via event, friend=${event.group}"
                        )
                    }

                    is BotLeaveEvent -> {
                        databaseIO {
                            val groupDao = groups()
                            groupDao.deleteViaId(event.bot.id, event.groupId)
                        }
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "group deleted via event, friend=${event.group}"
                        )
                    }
                }

                syncContactLock.unlock()
                return@subscribe ListeningStatus.LISTENING
            }
        }
    }

    private fun logout(account: Long): Boolean {
        val targetBot = bots[account] ?: return false
        if (!targetBot.isOnline) return true

        val existingBotJob = botJobs[account]
        if (existingBotJob == null || !existingBotJob.isActive) {
            Log.e(tag(), "bot $account is already offline.")
            return true
        }

        existingBotJob.cancel()
        targetBot.cancel()

        launch {
            databaseIO {
                val accountDao = accounts()
                val accountInfo = accountDao[account].singleOrNull()
                if (accountInfo != null) accountDao.update(accountInfo.apply {
                    isOfflineManually = true
                })
            }
        }

        botJobs.remove(account)
        return true
    }

    private fun removeBot(account: Long): Boolean {
        val bot = bots[account]
        val botJob = botJobs[account]
        return bot?.run {
            launch {
                bot.closeAndJoin()
                botJob?.cancelAndJoin()
            }
            bots.remove(account)
            bridge.notifyBotList()
            botJobs.remove(account)
            true
        } ?: false
    }

    private fun deleteBot(account: Long): Boolean {
        val r = removeBot(account)
        launch {
            databaseIO {
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

    private fun createBot(accountInfo: AccountLoginData, saveAccount: Boolean = false): Bot {
        val botWorkingDir =
            ArukuApplication.INSTANCE.filesDir.resolve("mirai/${accountInfo.accountNo}/")
        if (!botWorkingDir.exists()) botWorkingDir.mkdirs()

        if (saveAccount) launch(Dispatchers.IO) {
            database {
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

            botLoggerSupplier = { ArukuMiraiLogger("Bot.${it.id}", true) }
            networkLoggerSupplier = { ArukuMiraiLogger("Net.${it.id}", true) }

            val deviceInfoFile = botWorkingDir.resolve("device.json")
            if (deviceInfoFile.exists()) {
                fileBasedDeviceInfo(deviceInfoFile.absolutePath)
            } else {
                deviceInfo = {
                    MiraiDeviceGenerator().load(it).also { generated ->
                        deviceInfoFile.createNewFile()
                        deviceInfoFile.writeText(Json.encodeToString(generated))
                    }
                }
            }

            loginSolver = ArukuLoginSolver(loginSolvers.weakReference())
            enableContactCache()
        }
    }

    private suspend fun <T> databaseIO(block: ArukuDatabase.() -> T) =
        withContext(Dispatchers.IO) { suspendCoroutine { it.resume(database(block)) } }
}