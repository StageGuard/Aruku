package me.stageguard.aruku.common.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.DeadObjectException
import android.os.IBinder
import androidx.annotation.DrawableRes
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import me.stageguard.aruku.common.R
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge_Stub
import kotlin.coroutines.CoroutineContext

abstract class BaseArukuBackend: LifecycleService(), ArukuBackendBridge, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext

    private var frontendConnected = false

    abstract val serviceName: String
    abstract val notificationId: Int
    abstract val notificationChannelId: String
    @get:DrawableRes abstract val notificationIcon: Int

    override fun onCreate() {
        super.onCreate()
        onCreate0()
    }
    override fun onDestroy() {
        super.onDestroy()
        onDestroy0()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("stop", false) == true) {
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        }
        pushNotification()
        return START_STICKY
    }
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        frontendConnected = true
        pushNotification()
        return ArukuBackendBridge_Stub(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        onFrontendDisconnected()
        frontendConnected = false
        pushNotification()
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        frontendConnected = true
        pushNotification()
    }

    private fun pushNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val existingNotification =
            notificationManager.activeNotifications.find { it.id == notificationId }

        if (existingNotification == null) {
            var channel = notificationManager.getNotificationChannel(notificationChannelId)

            if (channel == null) {
                channel = NotificationChannel(
                    notificationChannelId,
                    serviceName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }

                notificationManager.createNotificationChannel(channel)
            }

            val notification = createNotification(channel.id)
            startForeground(notificationId, notification)
        } else {
            notificationManager.notify(notificationId, createNotification(notificationChannelId))
        }
    }

    private fun createNotification(channelId: String): Notification {
        return Notification.Builder(this, channelId)
            .apply {
                val content = if (frontendConnected) {
                    getString(R.string.service_attached_desc, serviceName)
                } else {
                    getString(R.string.service_detached_desc, serviceName)
                }

                setContentTitle(serviceName)
                setContentText(content)
                setSmallIcon(notificationIcon)
                setTicker(content)

                println("frontendConnected: $frontendConnected")
                if (!frontendConnected) {
                    val service = this@BaseArukuBackend
                    val stopIntent = PendingIntent.getService(
                        service,
                        0,
                        Intent(service, service::class.java).apply {
                            putExtra("stop", true)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    addAction(
                        Notification.Action.Builder(
                            notificationIcon,
                            getString(R.string.service_close_button),
                            stopIntent
                        ).build()
                    )
                } else {
                    setActions()
                }
            }.build()
    }

    fun tryOrMarkAsDead(block: () -> Unit, onDied: (() -> Unit)? = null) {
        try {
            block()
        } catch (re: RuntimeException) {
            if (re.cause is DeadObjectException) {
                onFrontendDisconnected()
                frontendConnected = false
                pushNotification()
                onDied?.invoke()
                return
            }
            throw re
        }
    }

    abstract fun onCreate0()

    abstract fun onDestroy0()

    open fun onFrontendDisconnected() { }
}