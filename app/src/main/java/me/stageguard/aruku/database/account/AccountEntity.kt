package me.stageguard.aruku.database.account

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.util.Into

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "account_no") val accountNo: Long,
    @ColumnInfo(name = "password_md5") val passwordMd5: String,
    @ColumnInfo(name = "login_protocol") val loginProtocol: String,
    @ColumnInfo(name = "heartbeat_strategy") val heartbeatStrategy: String,
    @ColumnInfo(name = "heartbeat_period_millis") val heartbeatPeriodMillis: Long,
    @ColumnInfo(name = "heartbeat_timeout_millis") val heartbeatTimeoutMillis: Long,
    @ColumnInfo(name = "stat_heartbeat_period_millis") val statHeartbeatPeriodMillis: Long,
    @ColumnInfo(name = "auto_reconnect") val autoReconnect: Boolean,
    @ColumnInfo(name = "reconnection_retry_times") val reconnectionRetryTimes: Int
) : Into<AccountInfo> {
    override fun into(): AccountInfo {
        return AccountInfo(
            accountNo = this@AccountEntity.accountNo,
            passwordMd5 = this@AccountEntity.passwordMd5,
            protocol = this@AccountEntity.loginProtocol,
            heartbeatStrategy = this@AccountEntity.heartbeatStrategy,
            heartbeatPeriodMillis = this@AccountEntity.heartbeatPeriodMillis,
            heartbeatTimeoutMillis = this@AccountEntity.heartbeatTimeoutMillis,
            statHeartbeatPeriodMillis = this@AccountEntity.statHeartbeatPeriodMillis,
            autoReconnect = this@AccountEntity.autoReconnect,
            reconnectionRetryTimes = this@AccountEntity.reconnectionRetryTimes
        )
    }
}