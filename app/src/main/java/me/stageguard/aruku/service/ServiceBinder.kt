package me.stageguard.aruku.service

import android.os.IBinder
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.service.bridge.BackendStateListener
import remoter.annotations.Remoter

@Remoter
interface ServiceBinder {
    fun registerBackendStateListener(listener: BackendStateListener): DisposableBridge

    fun bindBackendService(packageName: String)

    fun getBackendBridge(packageName: String): IBinder?
}