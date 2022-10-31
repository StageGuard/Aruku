package me.stageguard.aruku.database

import androidx.room.TypeConverter
import me.stageguard.aruku.service.parcel.ArukuMessageType

class ArukuMessageTypeConverter {
    companion object {
        private val messageTypeEnumValues by lazy { enumValues<ArukuMessageType>() }
    }

    @TypeConverter
    fun toMessageType(value: Int) = messageTypeEnumValues[value]

    @TypeConverter
    fun fromMessageType(value: ArukuMessageType) = value.ordinal
}