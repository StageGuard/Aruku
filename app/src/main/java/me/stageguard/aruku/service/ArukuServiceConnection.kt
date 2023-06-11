package me.stageguard.aruku.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.common.createAndroidLogger

class ArukuServiceConnection(
    private val context: Context
) : ServiceConnection, LifecycleEventObserver {
    private val logger = createAndroidLogger()

    var binder: ServiceBinder? = null
    val connected: MutableLiveData<Boolean> = MutableLiveData(false)

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        logger.d("service is connected: $name")
        if (service != null) {
            binder = ServiceBinder_Proxy(service)
        } else {
            logger.w("service binder is null while aruku service is connected.")
        }
        connected.value = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.d("service is disconnected: $name")
        binder = null
        connected.value = false
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                val bindResult = context.bindService(
                    Intent(context, ArukuService::class.java), this, Context.BIND_ABOVE_CLIENT
                )
                if (!bindResult) logger.e("Cannot bind ArukuService.")
            }

            Lifecycle.Event.ON_DESTROY -> {
                try {
                    context.unbindService(this)
                } catch (ex: IllegalArgumentException) {
                    logger.w("unable to unregister service.")
                }
            }

            else -> {}
        }
    }

    suspend fun awaitBinder(): ServiceBinder {
        return withContext(ArukuApplication.INSTANCE.binderAwaitContext) {
            suspendCancellableCoroutine { cont ->
                var binder0 = binder
                while (cont.isActive && binder0 == null) binder0 = binder

                if (binder0 != null) {
                    cont.resumeWith(Result.success(binder0))
                } else {
                    cont.resumeWith(Result.failure(Exception("await binder is null")))
                }
            }
        }
    }
}