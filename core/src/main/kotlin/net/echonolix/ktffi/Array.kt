package net.echonolix.ktffi

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout

@JvmInline
public value class NativeArray<T : NativeType>(
    public val _segment: MemorySegment,
) {
    public fun ptr(): NativePointer<T> = NativePointer(_segment.address())
}


public fun mallocArr(byteSize: Long, allocator: SegmentAllocator): NativeArray<*> =
    NativeArray<NativeChar>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize))

context(allocator: SegmentAllocator)
public fun mallocArr(byteSize: Long): NativeArray<*> =
    NativeArray<NativeChar>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize))

public fun callocArr(byteSize: Long, allocator: SegmentAllocator): NativeArray<*> =
    NativeArray<NativeChar>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun callocArr(byteSize: Long): NativeArray<*> =
    NativeArray<NativeChar>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize).apply { fill(0) })


public fun <T : NativeType> TypeDescriptor<T>.mallocArr(count: Long, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count))

public fun <T : NativeType> TypeDescriptor<T>.mallocArr(count: Int, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.mallocArr(count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count))

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.mallocArr(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

public fun <T : NativeType> TypeDescriptor<T>.callocArr(count: Long, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count).apply { fill(0) })

public fun <T : NativeType> TypeDescriptor<T>.callocArr(count: Int, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.callocArr(count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: SegmentAllocator)
public fun <T : NativeType> TypeDescriptor<T>.callocArr(count: Int): NativeArray<T> =
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