package me.stageguard.aruku.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.stageguard.aruku.database.account.AccountDao
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.database.contact.FriendDao
import me.stageguard.aruku.database.contact.FriendEntity
import me.stageguard.aruku.database.contact.GroupDao
import me.stageguard.aruku.database.contact.GroupEntity
import me.stageguard.aruku.database.message.MessagePreviewDao
import me.stageguard.aruku.database.message.MessagePreviewEntity
import me.stageguard.aruku.database.message.MessageRecordDao
import me.stageguard.aruku.database.message.MessageRecordEntity

@Database(
    entities = [
        AccountEntity::class,
        MessageRecordEntity::class,
        MessagePreviewEntity::class,
        GroupEntity::class,
        FriendEntity::class
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
    exportSchema = true
)
@TypeConverters(DBTypeConverters::class)
abstract class ArukuDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    abstract fun messageRecords(): MessageRecordDao

    abstract fun groups(): GroupDao

    abstract fun friends(): FriendDao

    abstract fun messagePreview(): MessagePreviewDao

    operator fun <T> invoke(block: ArukuDatabase.() -> T): T {
        return block(this@ArukuDatabase)
    }
}