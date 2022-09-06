package me.stageguard.aruku.preference

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.google.protobuf.MessageLiteOrBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import me.stageguard.aruku.util.toLogTag
import java.io.IOException
import kotlin.properties.ReadOnlyProperty

private val unitProp = Unit
fun <T : MessageLiteOrBuilder> Context.dataStoreRepositoryDelegate(
    ds: ReadOnlyProperty<Context, DataStore<T>>, default: Lazy<T>
) : DataStoreRepositoryDelegate<T> {
    // DataStoreSingletonDelegate.getValue doesn't use `property` value parameter.
    return DataStoreRepositoryDelegate(ds.getValue(this, ::unitProp), default)
}

class DataStoreRepositoryDelegate<T>(
    private val _delegate: DataStore<T>,
    private val lazyDefault: Lazy<T>,
) : Flow<T> by (_delegate.data.catch { ex ->
    if (ex is IOException) {
        Log.e(toLogTag("DataStore"), ex.toString())
        emit(lazyDefault.value)
    } else {
        throw ex
    }
}) {
    suspend fun CoroutineScope.update(block: suspend (T) -> T) {
        _delegate.updateData(block)
    }
}