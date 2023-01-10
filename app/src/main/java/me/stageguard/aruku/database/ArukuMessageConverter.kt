package me.stageguard.aruku.database

import androidx.room.TypeConverter
import me.stageguard.aruku.service.parcel.ArukuContactType

class ArukuMessageTypeConverter {
    companion object {
        private val messageTypeEnumValues by lazy { enumValues<ArukuContactType>() }
    }

    @TypeConverter
    fun toMessageType(value: Int) = messageTypeEnumValues[value]

    @TypeConverter
    fun fromMessageType(value: ArukuContactType) = value.ordinal
}