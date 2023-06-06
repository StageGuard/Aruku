package me.stageguard.aruku.common.service.bridge

import me.stageguard.aruku.common.service.parcel.AccountState
import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface BotStateObserver {
    fun onDispatch(@ParamOut state: AccountState)
}

fun BotStateObserver(block: (state: AccountState) -> Unit) = object : BotStateObserver {
    override fun onDispatch(state: AccountState) {
        block(state)
    }
}