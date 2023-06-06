package me.stageguard.aruku.database.account

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "account_no") val accountNo: Long,
    @ColumnInfo(name = "nickname", defaultValue = "") var nickname: String,
    @ColumnInfo(name = "avatar_url", defaultValue = "") var avatarUrl: String,
    @ColumnInfo(name = "password_md5") var passwordMd5: String,
    @ColumnInfo(name = "login_protocol") var loginProtocol: String,
    @ColumnInfo(name = "heartbeat_strategy") var heartbeatStrategy: String,
    @ColumnInfo(name = "heartbeat_period_millis") var heartbeatPeriodMillis: Long,
    @ColumnInfo(name = "heartbeat_timeout_millis") var heartbeatTimeoutMillis: Long,
    @ColumnInfo(name = "stat_heartbeat_period_millis") var statHeartbeatPeriodMillis: Long,
    @ColumnInfo(name = "auto_reconnect") var autoReconnect: Boolean,
    @ColumnInfo(name = "reconnection_retry_times") var reconnectionRetryTimes: Int,
    @ColumnInfo(name = "is_offline_manually") var isOfflineManually: Boolean,
)

fun AccountEntity.toLoginData() = me.stageguard.aruku.common.service.parcel.AccountLoginData(
    accountNo = this@AccountEntity.accountNo,
    passwordMd5 = this@AccountEntity.passwordMd5,
    protocol = this@AccountEntity.loginProtocol,
    heartbeatStrategy = this@AccountEntity.heartbeatStrategy,
    heartbeatPeriodMillis = this@AccountEntity.heartbeatPeriodMillis,
    heartbeatTimeoutMillis = this@AccountEntity.heartbeatTimeoutMillis,
    statHeartbeatPeriodMillis = this@AccountEntity.statHeartbeatPeriodMillis,
    autoReconnect = this@AccountEntity.autoReconnect,
    reconnectionRetryTimes = this@AccountEntity.reconnectionRetryTimes,
)

fun me.stageguard.aruku.common.service.parcel.AccountLoginData.toEntity() = AccountEntity(
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