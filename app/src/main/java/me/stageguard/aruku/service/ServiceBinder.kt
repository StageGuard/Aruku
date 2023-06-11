package me.stageguard.aruku.service

import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.service.bridge.BackendStateListener
import me.stageguard.aruku.service.bridge.DelegateBackendBridge
import remoter.annotations.Remoter

@Remoter
interface ServiceBinder {
    fun registerBackendStateListener(listener: BackendStateListener): DisposableBridge

    fun bindBackendService(packageName: String)

    fun getBackendBridge(packageName: String): DelegateBackendBridge?
}