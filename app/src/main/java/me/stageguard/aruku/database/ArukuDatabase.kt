package me.stageguard.aruku.database

import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import me.stageguard.aruku.database.account.AccountDao
import me.stageguard.aruku.database.account.AccountEntity

@OptIn(DelicateCoroutinesApi::class)
private val databaseScope by lazy { CoroutineScope(newSingleThreadContext("ArukuDB")) }

@Database(entities = [AccountEntity::class], version = 1)
abstract class ArukuDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    operator fun invoke(block: ArukuDatabase.() -> Unit) {
        databaseScope.launch { block(this@ArukuDatabase) }
    }
}