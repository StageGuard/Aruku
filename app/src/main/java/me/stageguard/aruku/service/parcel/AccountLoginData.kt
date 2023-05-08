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
) : Parcelable