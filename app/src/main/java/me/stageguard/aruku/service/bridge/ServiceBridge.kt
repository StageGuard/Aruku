package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.AccountState
import me.stageguard.aruku.service.parcel.AudioStatusListener
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import remoter.annotations.ParamIn
import remoter.annotations.Remoter

@Remoter
interface ServiceBridge {
    fun addBot(@ParamIn info: AccountLoginData?, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun getBots(): List<Long>
    fun loginAll()
    fun login(accountNo: Long): Boolean
    fun logout(accountNo: Long): Boolean
    fun attachBotStateObserver(identity: String, @ParamIn observer: BotStateObserver)
    fun detachBotStateObserver()

    fun getLastBotState(): Map<Long, AccountState>

    fun attachLoginSolver(@ParamIn solver: LoginSolverBridge)
    fun detachLoginSolver()

    fun openRoamingQuery(account: Long, @ParamIn contact: ContactId): RoamingQueryBridge?

    fun getAccountOnlineState(account: Long): Boolean
    fun queryAccountInfo(account: Long): AccountInfo?
    fun queryAccountProfile(account: Long): AccountProfile?
    fun getAvatarUrl(account: Long, @ParamIn contact: ContactId): String?
    fun getNickname(account: Long, @ParamIn contact: ContactId): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?

    fun setAudioUrlQueryBridge(@ParamIn bridge: AudioUrlQueryBridge)
    fun attachAudioStatusListener(audioFileMd5: String, listener: AudioStatusListener)
    fun detachAudioStatusListener(audioFileMd5: String)

    fun attachContactSyncer(@ParamIn bridge: ContactSyncBridge)
    fun subscribeMessages(@ParamIn bridge: MessageSubscriber)
}