package me.stageguard.aruku.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Database(
    entities = [
        AccountEntity::class,
        MessageRecordEntity::class,
        MessagePreviewEntity::class,
        GroupEntity::class,
        FriendEntity::class
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = MigrationV3toV4::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = MigrationV5toV6::class),
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

    suspend fun <T> suspendIO(block: ArukuDatabase.() -> T): T {
        return withContext(Dispatchers.IO) {
            suspendCoroutine { it.resume(invoke(block)) }
        }
    }

}

@DeleteColumn.Entries(
    DeleteColumn(tableName = "message_preview", columnName = "_prim_key")
)
private class MigrationV3toV4 : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(tableName = "friend", columnName = "_prim_key"),
    DeleteColumn(tableName = "group", columnName = "_prim_key")
)
private class MigrationV5toV6 : AutoMigrationSpec