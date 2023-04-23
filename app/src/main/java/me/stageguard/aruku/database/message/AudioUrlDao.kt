package me.stageguard.aruku.database.message

import androidx.room.Dao
import androidx.room.Query
import me.stageguard.aruku.database.BaseDao

@Dao
abstract class AudioUrlDao : BaseDao<AudioUrlEntity> {
    @Query("select * from audio_url where file_md5=:fileMd5")
    abstract fun getAudioUrl(fileMd5: String): List<AudioUrlEntity>
}