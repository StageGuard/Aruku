package me.stageguard.aruku.database.account

import androidx.room.Dao
import androidx.room.Query
import me.stageguard.aruku.database.BaseDao

@Dao
interface AccountDao : BaseDao<AccountEntity> {
    @Query("select * from account")
    fun getAll(): List<AccountEntity>

    @Query("select * from account where account_no=:accountNo")
    operator fun get(accountNo: Long): List<AccountEntity>
}