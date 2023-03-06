package me.stageguard.aruku.database.account

import androidx.room.Dao
import androidx.room.Query
import me.stageguard.aruku.database.BaseDao

@Dao
abstract class AccountDao : BaseDao<AccountEntity> {
    @Query("select * from account")
    abstract fun getAll(): List<AccountEntity>

    @Query("select * from account where account_no=:accountNo")
    abstract operator fun get(accountNo: Long): List<AccountEntity>

    fun setManuallyOffline(account: Long, v: Boolean) {
        val accountInfo = get(account).singleOrNull()
        if (accountInfo != null) {
            update(accountInfo.apply { isOfflineManually = v })
        }
    }
}