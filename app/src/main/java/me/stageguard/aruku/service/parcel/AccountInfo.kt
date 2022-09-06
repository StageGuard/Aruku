package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.AccountInfo as AccountInfoProto
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.login_protocol
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.heartbeat_strategy
import me.stageguard.aruku.service.ParcelInto

@Parcelize
data class AccountInfo(
    val accountNo: Long,
    val passwordMd5: String,
    val protocol: login_protocol,
    val heartbeatStrategy: heartbeat_strategy,
    val heartbeatPeriodMillis: Long,
    val heartbeatTimeoutMillis: Long,
    val statHeartbeatPeriodMillis: Long,
    val autoReconnect: Boolean,
    val reconnectionRetryTimes: Int
) : Parcelable, ParcelInto<AccountInfoProto> {
    override fun into(): AccountInfoProto {
        return AccountInfoProto.newBuilder().apply proto@ {
            this@proto.accountNo = this@AccountInfo.accountNo
            this@proto.passwordMd5 = this@AccountInfo.passwordMd5
            this@proto.protocol = this@AccountInfo.protocol
            this@proto.heartbeatStrategy = this@AccountInfo.heartbeatStrategy
            this@proto.heartbeatPeriodMillis = this@AccountInfo.heartbeatPeriodMillis
            this@proto.heartbeatTimeoutMillis = this@AccountInfo.heartbeatTimeoutMillis
            this@proto.statHeartbeatPeriodMillis = this@AccountInfo.statHeartbeatPeriodMillis
            this@proto.autoReconnect = this@AccountInfo.autoReconnect
            this@proto.reconnectionRetryTimes = this@AccountInfo.reconnectionRetryTimes
        }.build()
    }
}