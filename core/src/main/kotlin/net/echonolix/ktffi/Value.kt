package net.echonolix.ktffi

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator

@JvmInline
public value class NativeValue<T : NativeType>(
    public val _segment: MemorySegment,
) {
    public fun ptr(): NativePointer<T> = NativePointer(_segment.address())

    public inline operator fun invoke(block: NativeValue<T>.() -> Unit): NativeValue<T> {
        this.block()
        return this
    }
}

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.malloc(): NativeValue<T> = NativeValue(allocator.allocate(layout))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.calloc(): NativeValue<T> =
    NativeValue(allocator.allocate(layout).apply { fill(0) })