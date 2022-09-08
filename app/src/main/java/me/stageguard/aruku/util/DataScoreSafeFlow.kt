package me.stageguard.aruku.util

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

fun <T> DataStore<T>.safeFlow(default: T): Flow<T> {
    return data.catch { exception ->
        if (exception is IOException) {
            Log.e(toLogTag(), "Error reading data flow.", exception)
            emit(default)
        } else {
            throw exception
        }
    }
}