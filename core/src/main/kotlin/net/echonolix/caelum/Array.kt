@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator

@JvmInline
public value class NArray<T : NType>(
    public val segment: MemorySegment,
) {
    public inline fun ptr(): NPointer<T> = NPointer(segment.address())
}

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count))

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.malloc(count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.malloc(count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.malloc(count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count))

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.malloc(count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.calloc(count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.calloc(count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.calloc(count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.calloc(count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

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