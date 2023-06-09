package me.stageguard.aruku.common.service.bridge

import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.common.service.parcel.AccountProfile
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.GroupMemberInfo
import me.stageguard.aruku.common.service.parcel.RemoteFile
import remoter.annotations.ParamIn
import remoter.annotations.Remoter

@Remoter
interface ArukuBackendBridge {
    fun addBot(@ParamIn info: AccountLoginData?, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun getBots(): List<Long>
    fun login(accountNo: Long): Boolean
    fun logout(accountNo: Long): Boolean
    fun attachBotStateObserver(@ParamIn observer: BotStateObserver): DisposableBridge
    fun getLastBotState(): Map<Long, AccountState>
    fun attachLoginSolver(@ParamIn solver: LoginSolverBridge): DisposableBridge
    fun openRoamingQuery(account: Long, @ParamIn contact: ContactId): RoamingQueryBridge?
    fun getAccountOnlineState(account: Long): Boolean
    fun queryAccountInfo(account: Long): AccountInfo?
    fun queryAccountProfile(account: Long): AccountProfile?
    fun getAvatarUrl(account: Long, @ParamIn contact: ContactId): String?
    fun getNickname(account: Long, @ParamIn contact: ContactId): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?
    fun queryFile(account: Long, @ParamIn contact: ContactId, fileId: String): RemoteFile?
    fun attachContactSyncer(@ParamIn bridge: ContactSyncBridge): DisposableBridge
    fun subscribeMessages(@ParamIn bridge: MessageSubscriber): DisposableBridge
}