package me.stageguard.aruku.preference.serializer

import androidx.datastore.core.CorruptionException
import com.google.protobuf.InvalidProtocolBufferException
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts
import java.io.InputStream
import java.io.OutputStream
import androidx.datastore.core.Serializer as ProtoSerializer

object AccountsProtoSerializer : ProtoSerializer<Accounts> {
    override val defaultValue: Accounts
        get() = Accounts.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Accounts {
        try {
            return Accounts.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: Accounts, output: OutputStream) {
        t.writeTo(output)
    }
}