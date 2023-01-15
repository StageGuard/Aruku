package me.stageguard.aruku

import android.util.Log
import androidx.paging.PagingSource
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.IBotListObserver
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.service.parcel.GroupMemberInfo
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl

class MainRepositoryImpl(
    private val binder: IArukuMiraiInterface?,
    private val database: ArukuDatabase
) : MainRepository {
    override fun addBot(info: AccountLoginData, alsoLogin: Boolean): Boolean {
        return binder?.addBot(info, alsoLogin) ?: false
    }

    override fun removeBot(accountNo: Long): Boolean {
        return binder?.removeBot(accountNo) ?: false
    }

    override fun getBots(): List<Long> {
        return binder?.bots?.asList() ?: listOf()
    }

    override fun loginAll() {
        binder?.loginAll()
    }

    override fun login(accountNo: Long): Boolean {
        return binder?.login(accountNo) ?: false
    }

    override fun addBotListObserver(identity: String, observer: IBotListObserver) {
        binder?.addBotListObserver(identity, observer)
    }

    override fun removeBotListObserver(identity: String) {
        binder?.removeBotListObserver(identity)
    }

    override fun addLoginSolver(bot: Long, solver: ILoginSolver) {
        binder?.addLoginSolver(bot, solver)
    }

    override fun removeLoginSolver(bot: Long) {
        binder?.removeLoginSolver(bot)
    }

    override fun queryAccountProfile(account: Long): AccountInfo? {
        return try {
            binder?.queryAccountInfo(account)
        } catch (ex: Exception) {
            Log.w(tag(), "cannot get account profile of $account: $ex", ex)
            null
        }
    }

    override fun getAvatarUrl(account: Long, contact: ArukuContact): String? {
        return try {
            binder?.getAvatarUrl(account, contact)
        } catch (ex: Exception) {
            Log.w(tag(), "cannot get avatar url on $contact of bot $account: $ex", ex)
            null
        }
    }

    override fun getNickname(account: Long, contact: ArukuContact): String? {
        return try {
            binder?.getNickname(account, contact)
        } catch (ex: Exception) {
            Log.w(tag(), "cannot get nickname on $contact of bot $account: $ex", ex)
            null
        }
    }

    override fun getGroupMemberInfo(
        account: Long,
        groupId: Long,
        memberId: Long
    ): GroupMemberInfo? {
        return try {
            binder?.getGroupMemberInfo(account, groupId, memberId)
        } catch (ex: Exception) {
            Log.w(
                tag(),
                "cannot get member id on group $groupId, member $memberId of bot $account: $ex",
                ex
            )
            null
        }
    }

    override fun getAccount(account: Long): AccountEntity? {
        val result = database.accounts()[account]
        return if (result.isEmpty()) null else (result.singleOrNull() ?: result.first().also {
            Log.w(tag(), "get account $account has multiple results, peaking first.")
        })
    }

    override fun setAccountOfflineManually(account: Long) {
        val dao = database.accounts()
        val results = dao[account]
        results.forEach { it.isOfflineManually = true }
        dao.update(*results.toTypedArray())
    }

    override fun getMessagePreview(account: Long): PagingSource<Int, MessagePreviewEntity> {
        return database.messagePreview().getMessagesPaging(account)
    }

    override fun getGroups(account: Long): PagingSource<Int, GroupEntity> {
        return database.groups().getGroupsPaging(account)
    }

    override fun getFriends(account: Long): PagingSource<Int, FriendEntity> {
        return database.friends().getFriendsPaging(account)
    }

    override fun getMessageRecords(
        account: Long,
        subject: Long,
        type: ArukuContactType
    ): PagingSource<Int, MessageRecordEntity> {
        return database.messageRecords().getMessagesPaging(account, subject, type)
    }

    override suspend fun queryImageUrl(image: Image): String? {
        return try {
            image.queryUrl()
        } catch (ile: IllegalStateException) {
            null
        }
    }

    override fun clearUnreadCount(account: Long, contact: ArukuContact) {
        val dao = database.messagePreview()
        val preview = dao.getExactMessagePreview(account, contact.subject, contact.type)

        preview.forEach {
            dao.update(it.apply { it.unreadCount = 0 })
        }
    }
}