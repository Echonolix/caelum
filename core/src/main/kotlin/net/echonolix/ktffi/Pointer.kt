package net.echonolix.ktffi

import java.lang.foreign.MemoryLayout
import java.lang.invoke.MethodHandle

@JvmInline
value class NativePointer<T : NativeType>(
    val _address: Long,
) : NativeType {
    override val layout get() = Companion.layout
    override val arrayByteOffsetHandle get() = Companion.arrayByteOffsetHandle

    inline operator fun invoke(block: NativePointer<T>.() -> Unit): NativePointer<T> {
        this.block()
        return this
    }

    companion object {
        val layout = APIHelper.pointerLayout
        val arrayByteOffsetHandle: MethodHandle =
            layout.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
    }
}

val nullptr: NativePointer<*> = NativePointer<uint8_t>(0L)