package me.stageguard.aruku.database

import androidx.room.TypeConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import me.stageguard.aruku.domain.data.MessageElement

@OptIn(ExperimentalSerializationApi::class)
class DBTypeConverters {
    @TypeConverter
    fun fromEncodedData(value: String): List<MessageElement> {
        return protobuf.decodeFromHexString(value)
    }

    @TypeConverter
    fun encodeToData(value: List<MessageElement>): String {
        return protobuf.encodeToHexString(value)
    }

    companion object {
        private val protobuf = ProtoBuf { }
    }
}