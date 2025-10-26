package com.arc.launcher

import android.content.ComponentName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ComponentNameSerializer : KSerializer<ComponentName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ComponentName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ComponentName) {
        encoder.encodeString(value.flattenToString())
    }

    override fun deserialize(decoder: Decoder): ComponentName {
        return ComponentName.unflattenFromString(decoder.decodeString())!!
    }
}
