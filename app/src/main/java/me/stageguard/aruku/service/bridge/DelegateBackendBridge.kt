package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import remoter.annotations.Remoter

/**
 * Delegate backend service bridge,
 * calling methods from [ArukuBackendBridge] which returns [DisposableBridge] will do nothing.
 */
@Remoter
interface DelegateBackendBridge : ArukuBackendBridge {
    fun attachAudioListener(fileMd5: String, observer: AudioStateListener): DisposableBridge
}