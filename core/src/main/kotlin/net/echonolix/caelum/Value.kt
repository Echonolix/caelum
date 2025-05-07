@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator

@JvmInline
public value class NValue<T : NType>(
    public val segment: MemorySegment,
) {
    public inline fun ptr(): NPointer<T> = NPointer(segment.address())
}

context(allocator: MemoryStack.Frame)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.valueOf(value: K): NValue<T> {
    val v = malloc()
    valueSetValue(v, toNativeData(value))
    return v
}

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.valueOf(
    allocator: SegmentAllocator,
    value: K
): NValue<T> {
    val v = malloc(allocator)
    valueSetValue(v, toNativeData(value))
    return v
}