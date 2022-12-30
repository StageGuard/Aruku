package me.stageguard.aruku.service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.ArukuMessage
import me.stageguard.aruku.service.parcel.ArukuMessageEvent
import me.stageguard.aruku.service.parcel.ArukuMessageType
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.network.CustomLoginFailedException
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver
import org.koin.android.ext.android.inject
import xyz.cssxsh.mirai.device.MiraiDeviceGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ArukuMiraiService : LifecycleService() {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 72
        const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuMiraiService"
    }

    private val _botFactory: BotFactory by inject()
    private val database: ArukuDatabase by inject()

    private val bots: MutableLiveData<MutableMap<Long, Bot>> = MutableLiveData(mutableMapOf())
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private val botListObservers: MutableMap<String, Observer<Map<Long, Bot>>> = mutableMapOf()
    private val loginSolvers: MutableMap<Long, ILoginSolver> = mutableMapOf()
    private val messageConsumers: MutableMap<Long, MutableMap<String, IMessageConsumer>> = mutableMapOf()

    private val binderInterface = object : IArukuMiraiInterface.Stub() {
        override fun addBot(info: AccountInfo?, alsoLogin: Boolean): Boolean {
            Log.i(toLogTag(), "addBot(info=$info)")
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
                Log.w(this@ArukuMiraiService.toLogTag(), "Empty identity or observer for botListObservers.")
                return

            }
            val lifecycleObserver = Observer { new: Map<Long, Bot> ->
                observer.onChange(new.keys.toLongArray())
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
                Log.w(this@ArukuMiraiService.toLogTag(), "Empty login solver for loginSolvers.")
            }
        }

        override fun removeLoginSolver(bot: Long) {
            loginSolvers.remove(bot)
        }

        override fun addMessageEventConsumer(bot: Long, identity: String?, consumer: IMessageConsumer?) {
            if (identity == null || consumer == null) {
                Log.w(this@ArukuMiraiService.toLogTag(), "Empty identity or consumer for messageConsumers.")
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

        override fun getAvatar(account: Long, type: Int, subject: Long): String? {
            val bot = Bot.getInstanceOrNull(account)
            return if (bot != null) when (enumValues<ArukuMessageType>()[type]) {
                ArukuMessageType.GROUP -> bot.getGroup(subject)?.avatarUrl
                ArukuMessageType.FRIEND -> bot.getFriend(subject)?.avatarUrl
                ArukuMessageType.TEMP -> bot.getStranger(subject)?.avatarUrl
            } else null
        }

        override fun getNickname(account: Long, type: Int, subject: Long): String? {
            val bot = Bot.getInstanceOrNull(account)
            return if (bot != null) when (enumValues<ArukuMessageType>()[type]) {
                ArukuMessageType.GROUP -> bot.getGroup(subject)?.name
                ArukuMessageType.FRIEND -> bot.getFriend(subject)?.nick
                ArukuMessageType.TEMP -> bot.getStranger(subject)?.nick
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
                    Log.i(toLogTag(), "reading account data $account")
                    val existingBot = bots.value?.get(account.accountNo)
                    if (existingBot == null) {
                        bots.value?.set(account.accountNo, createBot(account.into(), pushToDatabase = false))
                        bots.notifyPost()
                        login(account.accountNo)
                    }
                }
            }
            Log.i(toLogTag(), "ArukuMiraiService is created.")
        } else {
            Log.e(toLogTag(), "ArukuApplication is not initialized yet.")
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
                val job = lifecycleScope.launch(context = CoroutineExceptionHandler { _, throwable ->
                    if (throwable is LoginFailedException) {
                        Log.e(toLogTag(), "login $accountNo failed.", throwable)
                        loginSolvers[accountNo]?.onLoginFailed(accountNo, throwable.killBot, throwable.message)
                    } else {
                        Log.e(toLogTag(), "uncaught exception while logging $accountNo.", throwable)
                        loginSolvers[accountNo]?.onLoginFailed(accountNo, true, throwable.message)
                        removeBot(accountNo)
                    }
                }) {
                    val eventChannel = targetBot.eventChannel.parentScope(this)

                    eventChannel.subscribeOnce<BotOnlineEvent> {
                        withContext(this@ArukuMiraiService.lifecycleScope.coroutineContext) {
                            Log.i(this@ArukuMiraiService.toLogTag(), "login success")
                            loginSolvers[accountNo]?.onLoginSuccess(accountNo)
                        }

                    }
                    targetBot.login()
                    doInitAfterLogin(targetBot)
                    targetBot.join()
                }
                val existingBotJob = botJobs[accountNo]
                if (existingBotJob != null) {
                    existingBotJob.cancel()
                    botJobs.remove(accountNo, existingBotJob)
                }
                botJobs[accountNo] = job
            }
            true
        } else false
    }

    private fun doInitAfterLogin(bot: Bot) {

        lifecycleScope.launch(Dispatchers.IO) {
            // cache contacts
            kotlin.run {
                val (groupDao, friendDao) = database { groups() to friends() }

                val (onlineGroups, onlineGroupsIds) = bot.groups.run { this to this.map { it.id } }
                val (cachedGroups, cachedGroupsIds) = groupDao.getGroups(bot.id).run { this to this.map { it.id } }

                val newGroups = onlineGroups.filter { it.id !in cachedGroupsIds }
                val deletedGroups = cachedGroups.filter { it.id !in onlineGroupsIds }

                groupDao.delete(*deletedGroups.toTypedArray())
                groupDao.update(*(onlineGroups - newGroups.toSet()).map { GroupEntity(bot.id, it.id, it.name) }
                    .toTypedArray())
                groupDao.insert(*newGroups.map { GroupEntity(bot.id, it.id, it.name) }.toTypedArray())

                val (onlineFriends, onlineFriendsIds) = bot.friends.run { this to this.map { it.id } }
                val (cachedFriends, cachedFriendsIds) = friendDao.getFriends(bot.id).run { this to this.map { it.id } }

                val newFriends = onlineFriends.filter { it.id !in cachedFriendsIds }
                val deletedFriends = cachedFriends.filter { it.id !in onlineFriendsIds }

                friendDao.delete(*deletedFriends.toTypedArray())
                friendDao.update(*(onlineFriends - newFriends.toSet()).map {
                    FriendEntity(bot.id, it.id, it.nick, it.friendGroup.id)
                }.toTypedArray())
                friendDao.insert(*newFriends.map { FriendEntity(bot.id, it.id, it.nick, it.friendGroup.id) }
                    .toTypedArray())
            }

            // sync latest history messages
            kotlin.run {
                val groups = bot.groups
                //TODO: roaming supported for group

            }
        }
        lifecycleScope.launch {
            // subscribe messages
            bot.eventChannel.subscribe<MessageEvent>(coroutineContext = CoroutineExceptionHandler { _, throwable ->
                if (throwable is IllegalStateException && throwable.toString()
                        .matches(Regex("UNKNOWN_MESSAGE_EVENT_TYPE"))
                ) {
                    Log.w(
                        this@ArukuMiraiService.toLogTag(),
                        "Unknown message type while listening ${bot.id}"
                    )
                } else {
                    Log.e(
                        this@ArukuMiraiService.toLogTag(),
                        "Error while subscribing message of ${bot.id}",
                        throwable
                    )
                }
            }) { ev ->
                if (!this@ArukuMiraiService.lifecycleScope.isActive) return@subscribe ListeningStatus.STOPPED

                val messageType = when (ev) {
                    is GroupMessageEvent -> ArukuMessageType.GROUP
                    is FriendMessageEvent -> ArukuMessageType.FRIEND
                    is GroupTempMessageEvent -> ArukuMessageType.TEMP
                    else -> error("UNKNOWN_MESSAGE_EVENT_TYPE")
                }
                val parcelMessageEvent = ArukuMessageEvent(
                    account = ev.bot.id,
                    subject = ev.subject.id,
                    sender = ev.sender.id,
                    senderName = ev.senderName,
                    message = ArukuMessage(
                        source = ev.source,
                        messages = ev.message
                    ),
                    type = messageType
                )
                database {
                    // insert message event to records
                    messageRecords().insert(parcelMessageEvent.into())
                    // update message preview
                    val messagePreviewDao = messagePreview()
                    val messagePreview = messagePreviewDao.getExactMessagePreview(ev.bot.id, ev.subject.id, messageType)
                    if (messagePreview.isEmpty()) {
                        messagePreviewDao.insert(
                            MessagePreviewEntity(
                                ev.bot.id,
                                ev.subject.id,
                                messageType,
                                ev.time.toLong(),
                                ev.sender.remark + ": " + ev.message.contentToString()
                            )
                        )
                    } else {
                        messagePreviewDao.update(messagePreview.single().apply p@{
                            this@p.time = ev.time.toLong()
                            this@p.previewContent = ev.sender.remark + ": " + ev.message.contentToString()
                        })
                    }

                }

                messageConsumers[ev.bot.id]?.let { consumers ->
                    consumers.forEach { (_, consumer) ->
                        consumer.consume(parcelMessageEvent)
                    }
                }

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

        return _botFactory.newBot(accountInfo.accountNo, accountInfo.passwordMd5) {
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
                    MiraiDeviceGenerator().load(it).also {
                        deviceInfoFile.createNewFile()
                        deviceInfoFile.writeText(Json.encodeToString(it))
                    }
                }
            }

            loginSolver = object : LoginSolver() {
                override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String {
                    return loginSolvers[bot.id]?.onSolvePicCaptcha(bot.id, data) ?: throw object :
                        CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                }

                override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String {
                    return loginSolvers[bot.id]?.onSolveSliderCaptcha(bot.id, url) ?: throw object :
                        CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                }

                @Deprecated(
                    "Please use onSolveDeviceVerification instead",
                    replaceWith = ReplaceWith("onSolveDeviceVerification(bot, url, null)"),
                    level = DeprecationLevel.WARNING
                )
                override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String {
                    return loginSolvers[bot.id]?.onSolveUnsafeDeviceLoginVerify(bot.id, url) ?: throw object :
                        CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                }

                override suspend fun onSolveDeviceVerification(
                    bot: Bot,
                    requests: DeviceVerificationRequests
                ): DeviceVerificationResult {
                    val sms = requests.sms
                    return if (sms != null) {
                        sms.requestSms()
                        val result = loginSolvers[bot.id]?.onSolveSMSRequest(bot.id, sms.countryCode + sms.phoneNumber)
                            ?: throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                        sms.solved(result)
                    } else {
                        val fallback = requests.fallback!!
                        loginSolvers[bot.id]?.onSolveUnsafeDeviceLoginVerify(bot.id, fallback.url)
                            ?: throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                        fallback.solved()
                    }
                }

                override val isSliderCaptchaSupported: Boolean
                    get() = true
            }
        }
    }

    class Connector(
        private val context: Context
    ) : ServiceConnection, LifecycleEventObserver, ReadOnlyProperty<Any?, IArukuMiraiInterface> {
        private lateinit var _delegate: IArukuMiraiInterface
        val connected: MutableLiveData<Boolean> = MutableLiveData(false)

        private val _bots: MutableList<Long> = mutableListOf()
        private val _botsLiveData: MutableLiveData<List<Long>> = MutableLiveData()

        val bots: LiveData<List<Long>> = _botsLiveData

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(toLogTag(), "service is connected: $name")
            _delegate = IArukuMiraiInterface.Stub.asInterface(service)
            connected.value = true

            _delegate.addBotListObserver(toString(), object : IBotListObserver.Stub() {
                override fun onChange(newList: LongArray?) {
                    val l = newList?.toList() ?: listOf()
                    _bots.removeIf { it !in l }
                    l.forEach { if (it !in _bots) _bots.add(it) }
                    _botsLiveData.value = _bots
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(toLogTag(), "service is disconnected: $name")
            connected.value = false
            _delegate.removeBotListObserver(toString())
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    val bindResult = context.bindService(
                        Intent(context, ArukuMiraiService::class.java), this, Context.BIND_ABOVE_CLIENT
                    )
                    if (!bindResult) Log.e(toLogTag(), "Cannot bind ArukuMiraiService.")
                }

                Lifecycle.Event.ON_DESTROY -> {
                    context.unbindService(this)
                }

                else -> {}
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): IArukuMiraiInterface {
            return _delegate
        }
    }
}