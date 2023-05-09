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
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.bridge.ServiceBridge_Proxy
import me.stageguard.aruku.util.createAndroidLogger

class ServiceConnector(
    private val context: Context
) : ServiceConnection, LifecycleEventObserver {
    private val logger = createAndroidLogger("ServiceConnector")

    private var _delegate: ServiceBridge? = null
    val connected: MutableLiveData<Boolean> = MutableLiveData(false)

    val binder get() = _delegate

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        logger.d("service is connected: $name")
        if (service != null) {
            _delegate = ServiceBridge_Proxy(service)
        } else {
            logger.w("service binder is null while aruku service is connected.")
        }
        connected.value = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.d("service is disconnected: $name")
        _delegate = null
        connected.value = false
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                val bindResult = context.bindService(
                    Intent(context, ArukuMiraiService::class.java), this, Context.BIND_ABOVE_CLIENT
                )
                if (!bindResult) logger.e("Cannot bind ArukuMiraiService.")
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
}