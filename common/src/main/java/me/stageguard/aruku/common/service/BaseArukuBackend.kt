package me.stageguard.aruku.common.service

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge_Stub
import kotlin.coroutines.CoroutineContext

abstract class BaseArukuBackend: LifecycleService(), ArukuBackendBridge, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = lifecycleScope.coroutineContext

    abstract override fun onCreate()
    abstract override fun onDestroy()

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return ArukuBackendBridge_Stub(this)
    }
}