package com.arc.launcher

import android.graphics.drawable.Drawable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object DrawableSerializer : KSerializer<Drawable?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Drawable", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Drawable?) {
        // We don't serialize Drawables
        encoder.encodeString("")
    }

    override fun deserialize(decoder: Decoder): Drawable? {
        // We don't deserialize Drawables
        return null
    }
}
