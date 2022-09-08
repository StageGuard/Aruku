package me.stageguard.aruku.preference.serializer

import androidx.datastore.core.CorruptionException
import com.google.protobuf.InvalidProtocolBufferException
import me.stageguard.aruku.preference.proto.PerfOuterClass
import java.io.InputStream
import java.io.OutputStream
import androidx.datastore.core.Serializer as ProtoSerializer

object PerfProtoSerializer : ProtoSerializer<PerfOuterClass.Perf> {
    override val defaultValue: PerfOuterClass.Perf
        get() = PerfOuterClass.Perf.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PerfOuterClass.Perf {
        try {
            return PerfOuterClass.Perf.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto Perf.", exception)
        }
    }

    override suspend fun writeTo(t: PerfOuterClass.Perf, output: OutputStream) {
        t.writeTo(output)
    }
}