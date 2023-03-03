package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.util.Into

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
) : Parcelable, Into<AccountEntity> {
    override fun into(): AccountEntity {
        return AccountEntity(
            accountNo = this@AccountLoginData.accountNo,
            nickname = "",
            avatarUrl = "",
            passwordMd5 = this@AccountLoginData.passwordMd5,
            loginProtocol = this@AccountLoginData.protocol,
            heartbeatStrategy = this@AccountLoginData.heartbeatStrategy,
            heartbeatPeriodMillis = this@AccountLoginData.heartbeatPeriodMillis,
            heartbeatTimeoutMillis = this@AccountLoginData.heartbeatTimeoutMillis,
            statHeartbeatPeriodMillis = this@AccountLoginData.statHeartbeatPeriodMillis,
            autoReconnect = this@AccountLoginData.autoReconnect,
            reconnectionRetryTimes = this@AccountLoginData.reconnectionRetryTimes,
            isOfflineManually = false,
        )
    }
}