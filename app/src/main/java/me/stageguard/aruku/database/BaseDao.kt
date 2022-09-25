package me.stageguard.aruku.database

import androidx.room.Delete
import androidx.room.Insert

interface BaseDao<T> {
    @Insert
    fun insert(vararg entities: T)

    @Delete
    fun delete(entity: T)
}