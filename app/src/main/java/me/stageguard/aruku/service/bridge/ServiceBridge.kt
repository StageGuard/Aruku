package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.*
import remoter.annotations.ParamIn
import remoter.annotations.Remoter

@Remoter
interface ServiceBridge {
    fun addBot(@ParamIn info: AccountLoginData?, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun deleteBot(accountNo: Long): Boolean
    fun getBots(): List<Long>
    fun loginAll()
    fun login(accountNo: Long): Boolean
    fun logout(accountNo: Long): Boolean
    fun attachBotStateObserver(identity: String, @ParamIn observer: BotStateObserver)
    fun detachBotStateObserver()

    fun getLastBotState(): Map<Long, AccountState>

    fun attachLoginSolver(@ParamIn solver: LoginSolverBridge)
    fun detachLoginSolver()

    fun openRoamingQuery(account: Long, @ParamIn contact: ArukuContact): RoamingQueryBridge?

    fun getAccountOnlineState(account: Long): Boolean
    fun queryAccountInfo(account: Long): AccountInfo?
    fun queryAccountProfile(account: Long): AccountProfile?
    fun getAvatarUrl(account: Long, @ParamIn contact: ArukuContact): String?
    fun getNickname(account: Long, @ParamIn contact: ArukuContact): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?
}