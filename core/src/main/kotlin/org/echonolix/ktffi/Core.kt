package org.echonolix.ktffi

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout

sealed class Type(val layout: MemoryLayout) {
    val arrayLayout: MemoryLayout = run {
        if (layout is StructLayout) {
            val alignment = layout.byteAlignment()
            val size = layout.byteSize()
            val roundedSize = (size + alignment - 1) / alignment * alignment
            if (roundedSize == size) {
                layout
            } else {
                MemoryLayout.structLayout(
                    *layout.memberLayouts().toTypedArray(),
                    MemoryLayout.paddingLayout(roundedSize - size)
                )
            }
        } else {
            layout
        }
    }
}

abstract class Struct(layout: MemoryLayout) : Type(layout)
abstract class Union(layout: MemoryLayout) : Type(layout)

@JvmInline
value class Pointer<T : Type>(
    val address: Long,
)

@JvmInline
value class Value<T : Type>(
    val segment: MemorySegment,
) {
    fun ptr(): Pointer<T> = Pointer(segment.address())
}

fun <T : Type> T.malloc(allocator: SegmentAllocator): Value<T> = Value(allocator.allocate(layout))

context(allocator: SegmentAllocator)
fun <T : Type> T.malloc(): Value<T> = Value(allocator.allocate(layout))

fun <T : Type> T.calloc(allocator: SegmentAllocator): Value<T> = Value(allocator.allocate(layout).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : Type> T.calloc(): Value<T> = Value(allocator.allocate(layout).apply { fill(0) })

@JvmInline
value class Array<T : Type>(
    val segment: MemorySegment,
) {
    fun ptr(): Pointer<T> = Pointer(segment.address())
}


fun mallocArr(byteSize: Long, allocator: SegmentAllocator): Array<*> =
    Array<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize))

context(allocator: SegmentAllocator)
fun mallocArr(byteSize: Long): Array<*> = Array<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize))

fun callocArr(byteSize: Long, allocator: SegmentAllocator): Array<*> =
    Array<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize).apply { fill(0) })

context(allocator: SegmentAllocator)
fun callocArr(byteSize: Long): Array<*> =
    Array<int8_t>(allocator.allocate(ValueLayout.JAVA_BYTE, byteSize).apply { fill(0) })


fun <T : Type> T.mallocArr(count: Long, allocator: SegmentAllocator): Array<T> =
    Array(allocator.allocate(layout, count))

fun <T : Type> T.mallocArr(count: Int, allocator: SegmentAllocator): Array<T> =
    Array(allocator.allocate(layout, count.toLong()))

context(allocator: SegmentAllocator)
fun <T : Type> T.mallocArr(count: Long): Array<T> = Array(allocator.allocate(layout, count))

context(allocator: SegmentAllocator)
fun <T : Type> T.mallocArr(count: Int): Array<T> = Array(allocator.allocate(layout, count.toLong()))

fun <T : Type> T.callocArr(count: Long, allocator: SegmentAllocator): Array<T> =
    Array(allocator.allocate(layout, count).apply { fill(0) })

fun <T : Type> T.callocArr(count: Int, allocator: SegmentAllocator): Array<T> =
    Array(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : Type> T.callocArr(count: Long): Array<T> = Array(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : Type> T.callocArr(count: Int): Array<T> = Array(allocator.allocate(layout, count.toLong()).apply { fill(0) })

val `_$OMNI_SEGMENT$_` = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE)

typealias char = int8_t
typealias size_t = int64_t
