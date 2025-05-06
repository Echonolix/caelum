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

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator): NValue<T> =
    NValue(allocator.allocate(layout))

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.malloc(): NValue<T> =
    NValue(allocator.allocate(layout))

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator): NValue<T> =
    NValue(allocator.allocate(layout).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NType> NType.Descriptor<T>.calloc(): NValue<T> =
    NValue(allocator.allocate(layout).apply { fill(0) })