package me.stageguard.aruku.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.account.AccountDao
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.ContactDao
import me.stageguard.aruku.database.contact.ContactEntity
import me.stageguard.aruku.database.message.AudioUrlDao
import me.stageguard.aruku.database.message.AudioUrlEntity
import me.stageguard.aruku.database.message.MessagePreviewDao
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordDao
import me.stageguard.aruku.database.message.MessageRecordEntity

@Database(
    entities = [
        AccountEntity::class,
        MessageRecordEntity::class,
        MessagePreviewEntity::class,
        ContactEntity::class,
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

    abstract fun contacts(): ContactDao

    abstract fun messagePreview(): MessagePreviewDao

    abstract fun audioUrls(): AudioUrlDao

    operator fun <T> invoke(block: ArukuDatabase.() -> T): T {
        return block(this@ArukuDatabase)
    }

    suspend fun <T> suspendIO(block: ArukuDatabase.() -> T): T {
        return this@ArukuDatabase.withTransaction { block(this) }
    }

    context(CoroutineScope) fun launchIO(block: suspend ArukuDatabase.() -> Unit) {
        launch(Dispatchers.IO) { block() }
    }

}