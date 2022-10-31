package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.util.Into

@Parcelize
data class AccountInfo(
    var accountNo: Long,
    var passwordMd5: String,
    var protocol: String,
    var heartbeatStrategy: String,
    var heartbeatPeriodMillis: Long,
    var heartbeatTimeoutMillis: Long,
    var statHeartbeatPeriodMillis: Long,
    var autoReconnect: Boolean,
    var reconnectionRetryTimes: Int
) : Parcelable, Into<AccountEntity> {
    override fun into(): AccountEntity {
        return AccountEntity(
            accountNo = this@AccountInfo.accountNo,
            passwordMd5 = this@AccountInfo.passwordMd5,
            loginProtocol = this@AccountInfo.protocol,
            heartbeatStrategy = this@AccountInfo.heartbeatStrategy,
            heartbeatPeriodMillis = this@AccountInfo.heartbeatPeriodMillis,
            heartbeatTimeoutMillis = this@AccountInfo.heartbeatTimeoutMillis,
            statHeartbeatPeriodMillis = this@AccountInfo.statHeartbeatPeriodMillis,
            autoReconnect = this@AccountInfo.autoReconnect,
            reconnectionRetryTimes = this@AccountInfo.reconnectionRetryTimes,
            isOfflineManually = false,
        )
    }
}