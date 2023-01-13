package me.stageguard.aruku.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.contact.toFriendEntity
import me.stageguard.aruku.database.contact.toGroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.service.parcel.*
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import net.mamoe.mirai.utils.mapToArray
import org.koin.android.ext.android.inject
import xyz.cssxsh.mirai.device.MiraiDeviceGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class ArukuMiraiService : LifecycleService() {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 72
        const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuMiraiService"
    }

    private val botFactory: BotFactory by inject()
    private val database: ArukuDatabase by inject()

    private val bots: MutableLiveData<MutableMap<Long, Bot>> = MutableLiveData(mutableMapOf())
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private val botListObservers: MutableMap<String, Observer<Map<Long, Bot>>> = mutableMapOf()
    private val loginSolvers: MutableMap<Long, ILoginSolver> = mutableMapOf()
    private val messageConsumers: MutableMap<Long, MutableMap<String, IMessageConsumer>> = mutableMapOf()

    private val binderInterface = object : IArukuMiraiInterface.Stub() {
        override fun addBot(info: AccountInfo?, alsoLogin: Boolean): Boolean {
            Log.i(tag(), "addBot(info=$info)")
            if (info == null) return false
            this@ArukuMiraiService.addBot(info, true)
            if (alsoLogin) this@ArukuMiraiService.login(info.accountNo)
            return true
        }

        override fun removeBot(accountNo: Long): Boolean {
            return this@ArukuMiraiService.removeBot(accountNo)
        }

        override fun getBots(): LongArray {
            return this@ArukuMiraiService.bots.value?.map { it.key }?.toLongArray() ?: longArrayOf()
        }

        override fun loginAll() {
            this@ArukuMiraiService.bots.value?.forEach { (no, _) -> login(no) }
        }

        override fun login(accountNo: Long): Boolean {
            return this@ArukuMiraiService.login(accountNo)
        }

        override fun addBotListObserver(identity: String?, observer: IBotListObserver?) {
            if (identity == null || observer == null) {
                Log.w(this@ArukuMiraiService.tag(), "Empty identity or observer for botListObservers.")
                return

            }
            val lifecycleObserver = Observer { new: Map<Long, Bot> ->
                observer.onChange(new.keys.mapToArray { it }.toLongArray())
            }
            botListObservers[identity] = lifecycleObserver
            lifecycleScope.launch(Dispatchers.Main) {
                this@ArukuMiraiService.bots.observe(this@ArukuMiraiService, lifecycleObserver)
            }
        }

        override fun removeBotListObserver(identity: String?) {
            val lifecycleObserver = this@ArukuMiraiService.botListObservers[identity]
            if (lifecycleObserver != null) {
                this@ArukuMiraiService.bots.removeObserver(lifecycleObserver)
                this@ArukuMiraiService.botListObservers.remove(identity, lifecycleObserver)
            }
        }

        override fun addLoginSolver(bot: Long, solver: ILoginSolver?) {
            if (solver != null) {
                loginSolvers[bot] = solver
            } else {
                Log.w(this@ArukuMiraiService.tag(), "Empty login solver for loginSolvers.")
            }
        }

        override fun removeLoginSolver(bot: Long) {
            loginSolvers.remove(bot)
        }

        override fun addMessageEventConsumer(bot: Long, identity: String?, consumer: IMessageConsumer?) {
            if (identity == null || consumer == null) {
                Log.w(this@ArukuMiraiService.tag(), "Empty identity or consumer for messageConsumers.")
                return
            }
            var exist = true
            val botConsumers = messageConsumers.getOrPut(bot) {
                exist = false
                mutableMapOf(identity to consumer)
            }
            if (exist) botConsumers[identity] = consumer
        }

        override fun removeMessageEventConsumer(identity: String?) {
            messageConsumers.iterator().forEach { entry ->
                entry.value.remove(identity)
            }
        }

        override fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
            val bot = Bot.getInstanceOrNull(account)
            return if (bot != null) when (contact.type) {
                ArukuContactType.GROUP -> bot.getGroup(contact.subject)?.avatarUrl
                ArukuContactType.FRIEND -> bot.getFriend(contact.subject)?.avatarUrl
                ArukuContactType.TEMP -> bot.getStranger(contact.subject)?.avatarUrl
            } else null
        }

        override fun getNickname(account: Long, contact: ArukuContact): String? {
            val bot = Bot.getInstanceOrNull(account)
            return if (bot != null) when (contact.type) {
                ArukuContactType.GROUP -> bot.getGroup(contact.subject)?.name
                ArukuContactType.FRIEND -> bot.getFriend(contact.subject)?.nick
                ArukuContactType.TEMP -> bot.getStranger(contact.subject)?.nick
            } else null
        }
    }

    override fun onCreate() {
        super.onCreate()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (ArukuApplication.initialized.get()) {
            lifecycleScope.launch(Dispatchers.IO) {
                database { accounts().getAll() }.filter { !it.isOfflineManually }.forEach { account ->
                    Log.i(this@ArukuMiraiService.tag(), "reading account data ${account.accountNo}")
                    val existingBot = bots.value?.get(account.accountNo)
                    if (existingBot == null) {
                        bots.value?.set(account.accountNo, createBot(account.into(), pushToDatabase = false))
                        bots.notifyPost()
                        login(account.accountNo)
                    }
                }
            }
            Log.i(tag(), "ArukuMiraiService is created.")
        } else {
            Log.e(tag(), "ArukuApplication is not initialized yet.")
        }

        createNotification()

        return Service.START_STICKY
    }

    private fun createNotification() {
        val context = ArukuApplication.INSTANCE.applicationContext
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager


        val existingNotification = notificationManager.activeNotifications.find { it.id == FOREGROUND_NOTIFICATION_ID }

        if (existingNotification == null) {
            var channel = notificationManager.getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL_ID)

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

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binderInterface
    }

    override fun onDestroy() {
        bots.value?.forEach { (_, bot) -> removeBot(bot.id, false) }
        super.onDestroy()
    }

    private fun addBot(account: AccountInfo, notifyPost: Boolean = false): Boolean {
        if (bots.value?.get(account.accountNo) != null) {
            removeBot(account.accountNo, notifyPost)
        }
        bots.value?.set(account.accountNo, createBot(account, pushToDatabase = true))
        if (notifyPost) bots.notifyPost() else bots.notify()
        return true
    }

    private fun login(accountNo: Long): Boolean {
        val targetBot = bots.value?.get(accountNo)
        return if (targetBot != null) {
            if (!targetBot.isOnline) {
                lifecycleScope.launch(Dispatchers.IO) {
                    database {
                        val accountDao = accounts()
                        val account = accountDao[accountNo].singleOrNull()
                        if (account != null) accountDao.update(account.apply { isOfflineManually = false })
                    }
                }
                val existingBotJob = botJobs[accountNo]
                if (existingBotJob != null) {
                    existingBotJob.cancel()
                    botJobs.remove(accountNo, existingBotJob)
                }
                val job = lifecycleScope.launch(context = CoroutineExceptionHandler { _, throwable ->
                    if (throwable is LoginFailedException) {
                        Log.e(tag(), "login $accountNo failed.", throwable)
                        loginSolvers[accountNo]?.onLoginFailed(accountNo, throwable.killBot, throwable.message)
                    } else if (throwable is IllegalStateException) {
                        if (throwable.toString()
                                .contains(Regex("create a new Bot instance", RegexOption.IGNORE_CASE))
                        ) {
                            Log.w(tag(), "bot closed, recreating new bot instance and logging.")
                            removeBot(accountNo)
                            lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, innerThrowable ->
                                Log.e(tag(), "uncaught exception while logging $accountNo.", innerThrowable)
                                loginSolvers[accountNo]?.onLoginFailed(accountNo, true, innerThrowable.message)
                                removeBot(accountNo)

                            }) {
                                val account = database.accounts()[accountNo].singleOrNull()
                                if (account != null && !account.isOfflineManually) {
                                    addBot(account.into(), notifyPost = true)
                                    login(accountNo)
                                }
                            }
                        } else {
                            Log.e(tag(), "uncaught exception while logging $accountNo.", throwable)
                            loginSolvers[accountNo]?.onLoginFailed(accountNo, true, throwable.message)
                            removeBot(accountNo)
                        }
                    } else {
                        Log.e(tag(), "uncaught exception while logging $accountNo.", throwable)
                        loginSolvers[accountNo]?.onLoginFailed(accountNo, true, throwable.message)
                        removeBot(accountNo)
                    }
                }) {
                    val eventChannel = targetBot.eventChannel.parentScope(this)

                    eventChannel.subscribeOnce<BotOnlineEvent> {
                        withContext(this@ArukuMiraiService.lifecycleScope.coroutineContext) {
                            Log.i(this@ArukuMiraiService.tag(), "login success")
                            loginSolvers[accountNo]?.onLoginSuccess(accountNo)
                        }

                    }
                    targetBot.login()
                    doInitAfterLogin(targetBot)
                    targetBot.join()
                }
                botJobs[accountNo] = job
            }
            true
        } else false
    }

    private fun doInitAfterLogin(bot: Bot) {
        val syncContactLock = Mutex(true)

        lifecycleScope.launch(Dispatchers.IO) {
            // cache contacts
            Log.i(tag("Contact"), "syncing contacts to database.")
            kotlin.run {
                val (groupDao, friendDao) = database { groups() to friends() }

                val (onlineGroups, onlineGroupsIds) = bot.groups.run { this to this.map { it.id } }
                val (cachedGroups, cachedGroupsIds) = groupDao.getGroups(bot.id).run { this to this.map { it.id } }

                val newGroups = onlineGroups.filter { it.id !in cachedGroupsIds }
                val deletedGroups = cachedGroups.filter { it.id !in onlineGroupsIds }

                groupDao.delete(*deletedGroups.toTypedArray())
                groupDao.update(*(onlineGroups - newGroups.toSet()).map(Group::toGroupEntity).toTypedArray())
                groupDao.insert(*newGroups.map(Group::toGroupEntity).toTypedArray())

                val (onlineFriends, onlineFriendsIds) = bot.friends.run { this to this.map { it.id } }
                val (cachedFriends, cachedFriendsIds) = friendDao.getFriends(bot.id).run { this to this.map { it.id } }

                val newFriends = onlineFriends.filter { it.id !in cachedFriendsIds }
                val deletedFriends = cachedFriends.filter { it.id !in onlineFriendsIds }

                friendDao.delete(*deletedFriends.toTypedArray())
                friendDao.update(*(onlineFriends - newFriends.toSet()).map(Friend::toFriendEntity).toTypedArray())
                friendDao.insert(*newFriends.map(Friend::toFriendEntity).toTypedArray())
            }
            Log.i(tag("Contact"), "sync contacts complete.")
            syncContactLock.unlock()
        }
        lifecycleScope.launch {
            // subscribe messages
            bot.eventChannel.parentScope(lifecycleScope)
                .subscribe<MessageEvent>(coroutineContext = CoroutineExceptionHandler { _, throwable ->
                    if (throwable is IllegalStateException && throwable.toString()
                            .contains(Regex("UNKNOWN_MESSAGE_EVENT_TYPE"))
                    ) {
                        Log.w(tag("Message"), "Unknown message type while listening ${bot.id}")
                    } else {
                        Log.e(tag("Message"), "Error while subscribing message of ${bot.id}", throwable)
                    }
                }) { event ->
                    if (!this@ArukuMiraiService.lifecycleScope.isActive) return@subscribe ListeningStatus.STOPPED

                    val messageType = when (event) {
                        is GroupMessageEvent -> ArukuContactType.GROUP
                        is FriendMessageEvent -> ArukuContactType.FRIEND
                        is GroupTempMessageEvent -> ArukuContactType.TEMP
                        else -> error("UNKNOWN_MESSAGE_EVENT_TYPE")
                    }
                    val parcelMessageEvent = ArukuMessageEvent(
                        account = event.bot.id,
                        subject = event.subject.id,
                        sender = event.sender.id,
                        senderName = event.senderName,
                        message = ArukuMessage(
                            source = event.source,
                            messages = event.message
                        ),
                        type = messageType
                    )
                    database {
                        // insert message event to records
                        messageRecords().insert(parcelMessageEvent.into())
                        // update message preview
                        val messagePreviewDao = messagePreview()
                        val messagePreview =
                            messagePreviewDao.getExactMessagePreview(event.bot.id, event.subject.id, messageType)
                        if (messagePreview.isEmpty()) {
                            messagePreviewDao.insert(
                                MessagePreviewEntity(
                                    event.bot.id,
                                    event.subject.id,
                                    messageType,
                                    event.time.toLong(),
                                    event.sender.nameCardOrNick + ": " + event.message.contentToString()
                                )
                            )
                        } else {
                            messagePreviewDao.update(messagePreview.single().apply p@{
                                this@p.time = event.time.toLong()
                                this@p.previewContent =
                                    event.sender.nameCardOrNick + ": " + event.message.contentToString()
                            })
                        }

                    }

                    messageConsumers[event.bot.id]?.let { consumers ->
                        consumers.forEach { (_, consumer) ->
                            consumer.consume(parcelMessageEvent)
                        }
                    }

                    return@subscribe ListeningStatus.LISTENING
                }

            // subscribe contact changes
            bot.eventChannel.parentScope(lifecycleScope).subscribe<BotEvent> { event ->
                if (!this@ArukuMiraiService.lifecycleScope.isActive) return@subscribe ListeningStatus.STOPPED
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

                        val friendDao = database { friends() }
                        val existing = friendDao.getFriend(event.bot.id, friend.id)
                        if (existing.isEmpty()) {
                            friendDao.insert(friend.toFriendEntity())
                        } else {
                            friendDao.update(existing.single().apply e@{
                                this@e.name = friend.nick
                                this@e.group = friend.friendGroup.id
                            })
                        }
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "friend added via event, friend=${friend}"
                        )
                    }

                    is FriendDeleteEvent -> {
                        val friendDao = database { friends() }
                        friendDao.deleteViaId(event.bot.id, event.friend.id)
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "friend deleted via event, friend=${event.friend}"
                        )
                    }

                    is BotJoinGroupEvent -> {
                        val groupDao = database { groups() }
                        val existing = groupDao.getGroup(event.bot.id, event.groupId)
                        if (existing.isEmpty()) {
                            groupDao.insert(event.group.toGroupEntity())
                        } else {
                            groupDao.update(existing.single().apply e@{
                                this@e.name = event.group.name
                            })
                        }
                        Log.i(
                            this@ArukuMiraiService.tag("Contact"),
                            "group added via event, friend=${event.group}"
                        )
                    }

                    is BotLeaveEvent -> {
                        val groupDao = database { groups() }
                        groupDao.deleteViaId(event.bot.id, event.groupId)
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

    private fun removeBot(accountNo: Long, notifyPost: Boolean = true): Boolean {
        val bot = bots.value?.get(accountNo)
        val botJob = botJobs[accountNo]
        return bot?.run {
            lifecycleScope.launch {
                bot.closeAndJoin()
                botJob?.cancelAndJoin()
            }
            bots.value?.remove(accountNo)
            if (notifyPost) bots.notifyPost() else bots.notify()
            botJobs.remove(accountNo)
            true
        } ?: false
    }

    private fun createBot(accountInfo: AccountInfo, pushToDatabase: Boolean = false): Bot {
        val miraiWorkingDir = ArukuApplication.INSTANCE.filesDir.resolve("mirai/")
        if (!miraiWorkingDir.exists()) miraiWorkingDir.mkdirs()

        if (pushToDatabase) lifecycleScope.launch(Dispatchers.IO) {
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
            workingDir = miraiWorkingDir
            parentCoroutineContext = lifecycleScope.coroutineContext
            protocol = MiraiProtocol.valueOf(accountInfo.protocol)
            autoReconnectOnForceOffline = accountInfo.autoReconnect
            heartbeatStrategy = HeartbeatStrategy.valueOf(accountInfo.heartbeatStrategy)
            heartbeatPeriodMillis = accountInfo.heartbeatPeriodMillis
            heartbeatTimeoutMillis = accountInfo.heartbeatTimeoutMillis
            reconnectionRetryTimes = accountInfo.reconnectionRetryTimes
            statHeartbeatPeriodMillis = accountInfo.statHeartbeatPeriodMillis

            botLoggerSupplier = { ArukuMiraiLogger("Bot.${it.id}", true) }
            networkLoggerSupplier = { ArukuMiraiLogger("Net.${it.id}", true) }

            val deviceInfoFile = miraiWorkingDir.resolve("device-${accountInfo.accountNo}.json")
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
}