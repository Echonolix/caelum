package net.echonolix.ktffi

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator

@JvmInline
value class NativeValue<T : NativeType>(
    val _segment: MemorySegment,
) {
    fun ptr(): NativePointer<T> = NativePointer(_segment.address())

    inline operator fun invoke(block: NativeValue<T>.() -> Unit): NativeValue<T> {
        this.block()
        return this
    }
}

fun <T : NativeType> T.malloc(allocator: SegmentAllocator): NativeValue<T> = NativeValue(allocator.allocate(layout))

context(allocator: SegmentAllocator)
fun <T : NativeType> T.malloc(): NativeValue<T> = NativeValue(allocator.allocate(layout))

fun <T : NativeType> T.calloc(allocator: SegmentAllocator): NativeValue<T> =
    NativeValue(allocator.allocate(layout).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : NativeType> T.calloc(): NativeValue<T> = NativeValue(allocator.allocate(layout).apply { fill(0) })