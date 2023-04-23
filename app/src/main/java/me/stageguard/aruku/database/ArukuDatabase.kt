package me.stageguard.aruku.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.stageguard.aruku.database.account.AccountDao
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendDao
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupDao
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.AudioUrlDao
import me.stageguard.aruku.database.message.AudioUrlEntity
import me.stageguard.aruku.database.message.MessagePreviewDao
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordDao
import me.stageguard.aruku.database.message.MessageRecordEntity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Database(
    entities = [
        AccountEntity::class,
        MessageRecordEntity::class,
        MessagePreviewEntity::class,
        GroupEntity::class,
        FriendEntity::class,
        AudioUrlEntity::class
    ],
    version = 1,
    autoMigrations = [],
    exportSchema = true
)
@TypeConverters(DBTypeConverters::class)
abstract class ArukuDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    abstract fun messageRecords(): MessageRecordDao

    abstract fun groups(): GroupDao

    abstract fun friends(): FriendDao

    abstract fun messagePreview(): MessagePreviewDao

    abstract fun audioUrls(): AudioUrlDao

    operator fun <T> invoke(block: ArukuDatabase.() -> T): T {
        return block(this@ArukuDatabase)
    }

    suspend fun <T> suspendIO(block: ArukuDatabase.() -> T): T {
        return withContext(Dispatchers.IO) {
            suspendCoroutine { it.resume(invoke(block)) }
        }
    }

}