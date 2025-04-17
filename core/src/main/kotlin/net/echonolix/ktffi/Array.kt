@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.ktffi

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator

@JvmInline
public value class NativeArray<T : NativeType>(
    public val _segment: MemorySegment,
) {
    public fun ptr(): NativePointer<T> = NativePointer(_segment.address())
}

public fun <T : NativeType> TypeDescriptor<T>.malloc(allocator: SegmentAllocator, count: ULong): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

public fun <T : NativeType> TypeDescriptor<T>.malloc(allocator: SegmentAllocator, count: UInt): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

public fun <T : NativeType> TypeDescriptor<T>.malloc(allocator: SegmentAllocator, count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count))

public fun <T : NativeType> TypeDescriptor<T>.malloc(allocator: SegmentAllocator, count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.malloc(count: ULong): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.malloc(count: UInt): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.malloc(count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.malloc(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

public fun <T : NativeType> TypeDescriptor<T>.calloc(allocator: SegmentAllocator, count: ULong): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public fun <T : NativeType> TypeDescriptor<T>.calloc(allocator: SegmentAllocator, count: UInt): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public fun <T : NativeType> TypeDescriptor<T>.calloc(allocator: SegmentAllocator, count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count).apply { fill(0) })

public fun <T : NativeType> TypeDescriptor<T>.calloc(allocator: SegmentAllocator, count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.calloc(count: ULong): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.calloc(count: UInt): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.calloc(count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.calloc(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public var NativeArray<NativeChar>.string: String
    get() = _segment.getString(0L)
    set(value) {
        _segment.setString(0L, value)
    }

public var NativePointer<NativeChar>.string: String
    get() = APIHelper.`_$OMNI_SEGMENT$_`.getString(_address)
    set(value) {
        APIHelper.`_$OMNI_SEGMENT$_`.setString(_address, value)
    }

public operator fun <E : NativeType, T : NativePointer<E>> NativeArray<T>.get(index: Long): T {
    @Suppress("UNCHECKED_CAST")
    return NativePointer<E>(NativePointer.arrayVarHandle.get(_segment, 0L, index) as Long) as T
}

public operator fun <E : NativeType, T : NativePointer<E>> NativeArray<T>.set(index: Long, value: T) {
    NativePointer.arrayVarHandle.set(_segment, 0L, index, value._address)
}

public operator fun <E : NativeType, T : NativePointer<E>> NativeArray<T>.get(index: Int): T {
    @Suppress("UNCHECKED_CAST")
    return NativePointer<E>(NativePointer.arrayVarHandle.get(_segment, 0L, index.toLong()) as Long) as T
}

public operator fun <E : NativeType, T : NativePointer<E>> NativeArray<T>.set(index: Int, value: T) {
    NativePointer.arrayVarHandle.set(_segment, 0L, index.toLong(), value._address)
}