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
import kotlinx.coroutines.SupervisorJob
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.service.bridge.BackendStateListener
import me.stageguard.aruku.service.parcel.BackendState
import me.stageguard.aruku.service.parcel.BackendState.FailureReason
import me.stageguard.aruku.ui.activity.MainActivity
import me.stageguard.aruku.util.stringRes
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

/**
 * service for holding connection of backend implementation service.
 */
class ArukuService : LifecycleService(), CoroutineScope, ServiceConnection {
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 72
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuService"

        private const val SERVICE_IMPL = "me.stageguard.aruku.BACKEND_SERVICE_IMPLEMENTATION"
    }

    private val logger = createAndroidLogger()

    // key is listener hash
    private val listeners: ConcurrentHashMap<Int, BackendStateListener> = ConcurrentHashMap()
    // key is package name
    private var registeredBackend: List<ResolveInfo> by Delegates.notNull()
    private val backendBridgeHolder: ConcurrentHashMap<String, IBinder> = ConcurrentHashMap()

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

        override fun getBackendBridge(packageName: String): IBinder? {
            return backendBridgeHolder[packageName]
        }
    }

    override fun onCreate() {
        super.onCreate()

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
                dispatchState(BackendState.ConnectFailed(packageName, FailureReason.NO_BACKEND_IMPL))
                return
            }

        val result = bindService(
            Intent(SERVICE_IMPL).apply {
               component = serviceResolution.serviceInfo.run { ComponentName(this.packageName, name) }
            }, this, Context.BIND_ABOVE_CLIENT)
        if (!result) dispatchState(BackendState.ConnectFailed(packageName, FailureReason.BIND_SERVICE_FAILED))
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val packageName = name?.packageName
        if (packageName != null && registeredBackend.find { it.serviceInfo.packageName == packageName } != null) {
            if (service == null || !service.isBinderAlive) {
                logger.w("registered backend is connected but bridge binder is null or died.")
                return
            }

            backendBridgeHolder[packageName] = service
            logger.i("backend $packageName is connected.")
            dispatchState(BackendState.Connected(packageName, service))
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        val packageName = name?.packageName
        if (packageName != null && registeredBackend.find { it.serviceInfo.packageName == packageName } != null) {

            backendBridgeHolder.remove(packageName)
            logger.i("backend $packageName is disconnected.")
            dispatchState(BackendState.Disconnected(packageName))
        }
    }

    @Suppress("JavaMapForEach")
    private fun dispatchState(state: BackendState) {
        listeners.forEach { _, listener -> listener.onState(state) }
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
    }
}