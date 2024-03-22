package name.tachenov.flakardia.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import name.tachenov.flakardia.data.Word
import java.time.Instant

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

object WordSerializer : KSerializer<Word> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Word", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Word) {
        encoder.encodeString(value.value.trim())
    }

    override fun deserialize(decoder: Decoder): Word = Word(decoder.decodeString())
}
