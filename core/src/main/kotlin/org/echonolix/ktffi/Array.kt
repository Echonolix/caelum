package org.echonolix.ktffi

import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout

@JvmInline
value class NativeArray<T : NativeType>(
    val _segment: MemorySegment,
) {
    fun ptr(): NativePointer<T> = NativePointer(_segment.address())
}


fun mallocArr(byteSize: Long, allocator: SegmentAllocator): NativeArray<*> =
    NativeArray<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize))

context(allocator: SegmentAllocator)
fun mallocArr(byteSize: Long): NativeArray<*> = NativeArray<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize))

fun callocArr(byteSize: Long, allocator: SegmentAllocator): NativeArray<*> =
    NativeArray<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize).apply { fill(0) })

context(allocator: SegmentAllocator)
fun callocArr(byteSize: Long): NativeArray<*> =
    NativeArray<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize).apply { fill(0) })


fun <T : NativeType> T.mallocArr(count: Long, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count))

fun <T : NativeType> T.mallocArr(count: Int, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
fun <T : NativeType> T.mallocArr(count: Long): NativeArray<T> = NativeArray(allocator.allocate(layout, count))

context(allocator: SegmentAllocator)
fun <T : NativeType> T.mallocArr(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()))

fun <T : NativeType> T.callocArr(count: Long, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count).apply { fill(0) })

fun <T : NativeType> T.callocArr(count: Int, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : NativeType> T.callocArr(count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : NativeType> T.callocArr(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

var NativeArray<char>.string: String
    get() = _segment.getString(0L)
    set(value) {
        _segment.setString(0L, value)
    }

var NativePointer<char>.string: String
    get() = APIHelper.`_$OMNI_SEGMENT$_`.getString(_address)
    set(value) {
        APIHelper.`_$OMNI_SEGMENT$_`.setString(_address, value)
    }