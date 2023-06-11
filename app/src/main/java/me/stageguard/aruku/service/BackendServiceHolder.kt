package me.stageguard.aruku.service

import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge
import me.stageguard.aruku.common.service.bridge.BotStateObserver
import me.stageguard.aruku.common.service.bridge.ContactSyncBridge
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.common.service.bridge.LoginSolverBridge
import me.stageguard.aruku.common.service.bridge.MessageSubscriber
import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.ContactInfo
import me.stageguard.aruku.common.service.parcel.ContactSyncOp
import me.stageguard.aruku.common.service.parcel.Message
import me.stageguard.aruku.service.parcel.BackendState
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

/**
 * holds [ArukuBackendBridge] and setup singleton disposable bridges.
 */
class BackendServiceHolder(
    val packageName: String,
    val bridge: ArukuBackendBridge,
    private val stateConsumer: Consumer<AccountState>,
    private val onSyncContact: (ContactSyncOp, Long, List<ContactInfo>) -> Unit,
    private val onUpdateAccountInfo: (AccountInfo) -> Unit,
    private val onMessage: (Message) -> Unit
) : DisposableBridge {
    var state: BackendState = BackendState.Disconnected(packageName)

    /**
     * singleton disposables
     *
     * there several singleton bridges from common module:
     * * [MessageSubscriber]
     * * [LoginSolverBridge]
     * * [ContactSyncBridge]
     * * [BotStateObserver]
     *
     * singleton bridge only has one instance at the whole application(exclude service) lifecycle.
     * it is only instantiated in [ArukuService] and disposed on service destroy.
     *
     */
    private val disposables: ConcurrentLinkedQueue<DisposableBridge> = ConcurrentLinkedQueue()

    init {
        disposables.offer(bridge.attachBotStateObserver(BotStateObserver(stateConsumer::accept)))
        disposables.offer(bridge.attachContactSyncer(object : ContactSyncBridge {
            override fun onSyncContact(op: ContactSyncOp, account: Long, contacts: List<ContactInfo>) {
                this@BackendServiceHolder.onSyncContact(op, account, contacts)
            }

            override fun onUpdateAccountInfo(info: AccountInfo) {
                this@BackendServiceHolder.onUpdateAccountInfo(info)
            }
        }))
        disposables.offer(bridge.subscribeMessages(object : MessageSubscriber {
            override fun onMessage(message: Message) {
                this@BackendServiceHolder.onMessage(message)
            }
        }))
    }

    override fun dispose() {
        disposables.forEach(DisposableBridge::dispose)
        disposables.clear()
    }
}