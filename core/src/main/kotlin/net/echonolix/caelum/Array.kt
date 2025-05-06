@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import java.lang.foreign.MemorySegment

@JvmInline
public value class NArray<T : NType>(
    public val segment: MemorySegment,
) {
    public inline fun ptr(): NPointer<T> = NPointer(segment.address())
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.get(index: Long): T {
    @Suppress("UNCHECKED_CAST")
    return NPointer<E>(NPointer.arrayVarHandle.get(segment, 0L, index) as Long) as T
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.set(index: Long, value: T) {
    NPointer.arrayVarHandle.set(segment, 0L, index, value._address)
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.get(index: Int): T {
    @Suppress("UNCHECKED_CAST")
    return NPointer<E>(NPointer.arrayVarHandle.get(segment, 0L, index.toLong()) as Long) as T
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.set(index: Int, value: T) {
    NPointer.arrayVarHandle.set(segment, 0L, index.toLong(), value._address)
}