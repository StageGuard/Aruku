package me.stageguard.aruku.service.parcel

import android.os.IBinder
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * id represents package name of backend impl
 */
sealed class BackendState(open val id: String) {
    @Parcelize
    class Connected(override val id: String, val bridge: IBinder) : BackendState(id), Parcelable

    @Parcelize
    class Disconnected(override val id: String) : BackendState(id), Parcelable

    @Parcelize
    class ConnectFailed(override val id: String, val reason: FailureReason) : BackendState(id), Parcelable

    @Parcelize
    enum class FailureReason : Parcelable {
        BIND_SERVICE_FAILED, NO_BACKEND_IMPL
    }
}