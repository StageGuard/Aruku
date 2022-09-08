package me.stageguard.aruku.util

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

inline fun <reified T> DataStore<T>.safeFlow(): Flow<T> {
    return data.catch { exception ->
        if (exception is IOException) {
            Log.e(toLogTag(), "Error reading data flow.", exception)

            val defaultInstanceMethod = T::class.java.getMethod("getDefaultInstance")
            emit(defaultInstanceMethod.invoke(null) as T)
        } else {
            throw exception
        }
    }
}