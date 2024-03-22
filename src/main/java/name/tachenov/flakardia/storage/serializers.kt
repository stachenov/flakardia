package name.tachenov.flakardia.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import name.tachenov.flakardia.data.Word
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

val SERIALIZER = Json {
    prettyPrint = true
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Duration", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeDouble(value.toSeconds().toDouble() / 86400.0)
    }

    override fun deserialize(decoder: Decoder): Duration = Duration.ofSeconds((decoder.decodeDouble() * 86400.0).roundToLong())
}

object WordSerializer : KSerializer<Word> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Word", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Word) {
        encoder.encodeString(value.value.trim())
    }

    override fun deserialize(decoder: Decoder): Word = Word(decoder.decodeString())
}
