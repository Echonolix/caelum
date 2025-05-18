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

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.valueOf(
    allocator: SegmentAllocator,
    value: K
): NValue<T> {
    val v = malloc(allocator)
    valueSetValue(v, toNativeData(value))
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.valueOf(value: K): NValue<T> =
    valueOf(allocator, value)



public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    allocator: SegmentAllocator,
    value1: K
): NArray<T> {
    val v = malloc(allocator, 1)
    arraySetElement(v, 0L, toNativeData(value1))
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(value1: K): NArray<T> =
    arrayOf(allocator, value1)

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    allocator: SegmentAllocator,
    value1: K,
    value2: K
): NArray<T> {
    val v = malloc(allocator, 2)
    arraySetElement(v, 0L, toNativeData(value1))
    arraySetElement(v, 1L, toNativeData(value2))
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    value1: K,
    value2: K
): NArray<T> = arrayOf(allocator, value1, value2)

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    allocator: SegmentAllocator,
    value1: K,
    value2: K,
    value3: K
): NArray<T> {
    val v = malloc(allocator, 3)
    arraySetElement(v, 0L, toNativeData(value1))
    arraySetElement(v, 1L, toNativeData(value2))
    arraySetElement(v, 2L, toNativeData(value3))
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    value1: K,
    value2: K,
    value3: K
): NArray<T> = arrayOf(allocator, value1, value2, value3)

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    allocator: SegmentAllocator,
    value1: K,
    value2: K,
    value3: K,
    value4: K
): NArray<T> {
    val v = malloc(allocator, 4)
    arraySetElement(v, 0L, toNativeData(value1))
    arraySetElement(v, 1L, toNativeData(value2))
    arraySetElement(v, 2L, toNativeData(value3))
    arraySetElement(v, 3L, toNativeData(value4))
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    value1: K,
    value2: K,
    value3: K,
    value4: K
): NArray<T> = arrayOf(allocator, value1, value2, value3, value4)

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    allocator: SegmentAllocator,
    value1: K,
    value2: K,
    value3: K,
    value4: K,
    value5: K
): NArray<T> {
    val v = malloc(allocator, 5)
    arraySetElement(v, 0L, toNativeData(value1))
    arraySetElement(v, 1L, toNativeData(value2))
    arraySetElement(v, 2L, toNativeData(value3))
    arraySetElement(v, 3L, toNativeData(value4))
    arraySetElement(v, 4L, toNativeData(value5))
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    value1: K,
    value2: K,
    value3: K,
    value4: K,
    value5: K
): NArray<T> = arrayOf(allocator, value1, value2, value3, value4, value5)

public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    allocator: SegmentAllocator,
    vararg values: K
): NArray<T> {
    val v = malloc(allocator, values.size)
    for (i in values.indices) {
        arraySetElement(v, i.toLong(), toNativeData(values[i]))
    }
    return v
}

context(allocator: MemoryStack)
public inline fun <T : NPrimitive<N, K>, N : Any, K : Any> NPrimitive.Descriptor<T, N, K>.arrayOf(
    vararg values: K
): NArray<T> = arrayOf(allocator, *values)
