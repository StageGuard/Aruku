package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountProfile
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import remoter.annotations.ParamIn
import remoter.annotations.Remoter

@Remoter
interface ServiceBridge {
    fun addBot(@ParamIn info: AccountLoginData?, alsoLogin: Boolean): Boolean
    fun removeBot(accountNo: Long): Boolean
    fun getBots(): LongArray
    fun loginAll()
    fun login(accountNo: Long): Boolean
    fun addBotListObserver(identity: String, @ParamIn observer: BotObserverBridge)
    fun removeBotListObserver(identity: String)
    fun addLoginSolver(bot: Long, @ParamIn solver: LoginSolverBridge)
    fun removeLoginSolver(bot: Long)

    fun queryAccountInfo(account: Long): AccountInfo?
    fun queryAccountProfile(account: Long): AccountProfile?
    fun getAvatarUrl(account: Long, @ParamIn contact: ArukuContact): String?
    fun getNickname(account: Long, @ParamIn contact: ArukuContact): String?
    fun getGroupMemberInfo(account: Long, groupId: Long, memberId: Long): GroupMemberInfo?
}