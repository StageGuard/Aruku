package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AccountLoginData(
    var accountNo: Long,
    var passwordMd5: String,
    var protocol: String,
    var heartbeatStrategy: String,
    var heartbeatPeriodMillis: Long,
    var heartbeatTimeoutMillis: Long,
    var statHeartbeatPeriodMillis: Long,
    var autoReconnect: Boolean,
    var reconnectionRetryTimes: Int
) : Parcelable {
    override fun toString(): String {
        return buildString {
           append("AccountLoginData(")
           append("accountNo=$accountNo, ")
           append("passwordMd5=<masked>, ")
           append("protocol=$protocol, ")
           append("heartbeatStrategy=$heartbeatStrategy, ")
           append("heartbeatPeriodMillis=$heartbeatPeriodMillis, ")
           append("heartbeatTimeoutMillis=$heartbeatTimeoutMillis,")
           append("statHeartbeatPeriodMillis=$statHeartbeatPeriodMillis,")
           append("autoReconnect=$autoReconnect,")
           append("reconnectionRetryTimes=$reconnectionRetryTimes)")
        }
    }
}