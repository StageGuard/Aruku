package me.stageguard.aruku.database

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Upsert

interface BaseDao<T> {
    @Insert
    fun insert(vararg entities: T)

    @Delete
    fun delete(vararg entities: T)

    @Update
    fun update(vararg entities: T)

    @Upsert
    fun upsert(vararg entities: T)
}