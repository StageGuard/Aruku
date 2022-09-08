package me.stageguard.aruku.service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.preference.accountStore
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts
import me.stageguard.aruku.preference.safeFlow
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.network.CustomLoginFailedException
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.properties.ReadOnlyProperty
import kotlin.random.Random
import kotlin.reflect.KProperty
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.AccountInfo as AccountInfoProto
import me.stageguard.aruku.service.parcel.AccountInfo as AccountInfoParcel

class ArukuMiraiService : LifecycleService() {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 72
        const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuMiraiService"
    }

    private val bots: MutableLiveData<MutableMap<Long, Bot>> = MutableLiveData(mutableMapOf())
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private val botListObservers: MutableMap<String, Observer<Map<Long, Bot>>> = mutableMapOf()
    private val loginSolvers: MutableMap<Long, ILoginSolver> = mutableMapOf()

    private val binderInterface = object : IArukuMiraiInterface.Stub() {
        override fun addBot(info: AccountInfoParcel?, alsoLogin: Boolean): Boolean {
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
            if (identity != null && observer != null) {
                val lifecycleObserver = Observer { new: Map<Long, Bot> ->
                    observer.onChange(new.keys.toLongArray())
                }
                botListObservers[identity] = lifecycleObserver
                lifecycleScope.launch(Dispatchers.Main) {
                    this@ArukuMiraiService.bots.observe(this@ArukuMiraiService, lifecycleObserver)
                }
            } else {
                Log.w(this@ArukuMiraiService.toLogTag(), "Empty identity or observer for botListObservers.")
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
    }

    override fun onCreate() {
        super.onCreate()
        if (ArukuApplication.initialized.get()) {
            val context = ArukuApplication.INSTANCE.applicationContext
            lifecycleScope.launch {
                context.accountStore.safeFlow.collect {
                    it.accountMap.forEach { (id, info) ->
                        Log.i(toLogTag(), "reading account data $info")
                        bots.value?.set(id, createBot(info))
                        bots.notifyPost()
                        login(id)
                    }
                }
                Log.i(toLogTag(), "read completed")
            }
            Log.i(toLogTag(), "ArukuMiraiService is created.")
        } else {
            Log.e(toLogTag(), "ArukuApplication is not initialized yet.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

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

        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binderInterface
    }

    override fun onDestroy() {
        bots.value?.forEach { (_, bot) -> removeBot(bot.id, false) }
        super.onDestroy()
    }

    private fun addBot(accountInfo: AccountInfoParcel, notifyPost: Boolean = false): Boolean {
        if (bots.value?.get(accountInfo.accountNo) != null) {
            removeBot(accountInfo.accountNo, notifyPost)
        }
        bots.value?.set(accountInfo.accountNo, createBot(accountInfo.into()))
        if (notifyPost) bots.notifyPost() else bots.notify()
        return true
    }

    private fun login(accountNo: Long): Boolean {
        val targetBot = bots.value?.get(accountNo)
        return if (targetBot != null) {
            if (!targetBot.isOnline) {
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
                    targetBot.login()
                    loginSolvers[accountNo]?.onLoginSuccess(accountNo)
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

    private fun removeBot(accountNo: Long, notifyPost: Boolean = true): Boolean {
        val bot = bots.value?.get(accountNo)
        val botJob = botJobs[accountNo]
        return bot?.run {
            lifecycleScope.launch(Dispatchers.Main) {
                bot.closeAndJoin()
                botJob?.cancelAndJoin()
            }
            bots.value?.remove(accountNo)
            if (notifyPost) bots.notifyPost() else bots.notify()
            botJobs.remove(accountNo)
            true
        } ?: false
    }

    private fun createBot(accountInfo: AccountInfoProto): Bot {
        val miraiWorkingDir = ArukuApplication.INSTANCE.filesDir.resolve("mirai/")
        if (!miraiWorkingDir.exists()) miraiWorkingDir.mkdirs()

        return accountInfo.run {
            BotFactory.newBot(accountNo, passwordMd5) {
                workingDir = miraiWorkingDir
                parentCoroutineContext = lifecycleScope.coroutineContext
                protocol = when (getProtocol()) {
                    Accounts.login_protocol.ANDROID_PHONE -> MiraiProtocol.ANDROID_PHONE
                    Accounts.login_protocol.ANDROID_PAD -> MiraiProtocol.ANDROID_PAD
                    Accounts.login_protocol.ANDROID_WATCH -> MiraiProtocol.ANDROID_WATCH
                    Accounts.login_protocol.MACOS -> MiraiProtocol.MACOS
                    Accounts.login_protocol.IPAD -> MiraiProtocol.IPAD

                    Accounts.login_protocol.UNRECOGNIZED, null -> MiraiProtocol.ANDROID_PHONE
                }
                autoReconnectOnForceOffline = autoReconnect
                heartbeatStrategy = when (getHeartbeatStrategy()) {
                    Accounts.heartbeat_strategy.STAT_HB -> HeartbeatStrategy.STAT_HB
                    Accounts.heartbeat_strategy.REGISTER -> HeartbeatStrategy.REGISTER
                    Accounts.heartbeat_strategy.NONE -> HeartbeatStrategy.NONE

                    Accounts.heartbeat_strategy.UNRECOGNIZED, null -> HeartbeatStrategy.STAT_HB
                }
                heartbeatPeriodMillis = getHeartbeatPeriodMillis()
                heartbeatTimeoutMillis = getHeartbeatTimeoutMillis()
                reconnectionRetryTimes = getReconnectionRetryTimes()
                statHeartbeatPeriodMillis = getStatHeartbeatPeriodMillis()

                botLoggerSupplier = { ArukuMiraiLogger("Bot.${it.id}", true) }
                networkLoggerSupplier = { ArukuMiraiLogger("Net.${it.id}", true) }

                val deviceInfoFile = miraiWorkingDir.resolve("device-${accountInfo.accountNo}.json")
                if (deviceInfoFile.exists()) {
                    fileBasedDeviceInfo(deviceInfoFile.absolutePath)
                } else {
                    deviceInfo = {
                        DeviceInfo(
                            display = Build.DISPLAY.toByteArray(),
                            product = Build.PRODUCT.toByteArray(),
                            device = Build.DEVICE.toByteArray(),
                            board = Build.BOARD.toByteArray(),
                            brand = Build.BRAND.toByteArray(),
                            model = Build.MODEL.toByteArray(),
                            bootloader = Build.BOOTLOADER.toByteArray(),
                            fingerprint = Build.FINGERPRINT.toByteArray(),
                            bootId = generateUUID(getRandomByteArray(16, Random.Default).md5()).toByteArray(),
                            procVersion = byteArrayOf(),
                            baseBand = byteArrayOf(),
                            version = DeviceInfo.Version(
                                release = Build.VERSION.RELEASE.toByteArray(),
                                codename = Build.VERSION.CODENAME.toByteArray(),
                                sdk = Build.VERSION.SDK_INT
                            ),
                            simInfo = "T-Mobile".toByteArray(),
                            osType = "android".toByteArray(),
                            macAddress = "02:00:00:00:00:00".toByteArray(),
                            wifiBSSID = "02:00:00:00:00:00".toByteArray(),
                            wifiSSID = "<unknown ssid>".toByteArray(),
                            imsiMd5 = getRandomByteArray(16, Random.Default).md5(),
                            imei = ArukuApplication.INSTANCE.applicationContext.imei,
                            apn = "wifi".toByteArray()
                        ).also {
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

                    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String {
                        return loginSolvers[bot.id]?.onSolveUnsafeDeviceLoginVerify(bot.id, url) ?: throw object :
                            CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                    }
                }
            }
        }
    }

    class Connector(
        private val context: Context
    ) : ServiceConnection, LifecycleEventObserver, ReadOnlyProperty<Any?, IArukuMiraiInterface> {
        private lateinit var _delegate: IArukuMiraiInterface
        val connected: MutableLiveData<Boolean> = MutableLiveData(false)

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(toLogTag(), "service is connected: $name")
            _delegate = IArukuMiraiInterface.Stub.asInterface(service)
            connected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(toLogTag(), "service is disconnected: $name")
            connected.value = false
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