package org.echonolix.ktffi

import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import kotlin.jvm.optionals.getOrElse

interface NativeType {
    val layout: MemoryLayout
    val arrayLayout: MemoryLayout
    val arrayByteOffsetHandle: MethodHandle
}

sealed class NativeTypeImpl(override val layout: MemoryLayout) : NativeType {
    final override val arrayLayout: MemoryLayout = run {
        val layout = layout
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
    }.withName("${layout.name().get()}[]")

    final override val arrayByteOffsetHandle: MethodHandle = arrayLayout.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
}

abstract class NativeStruct(override val layout: StructLayout) : NativeTypeImpl(layout)
abstract class NativeUnion(override val layout: UnionLayout) : NativeTypeImpl(layout)

@JvmInline
value class NativePointer<T : NativeType>(
    val _address: Long,
) {
    inline operator fun invoke(block: NativePointer<T>.() -> Unit): NativePointer<T> {
        this.block()
        return this
    }
}

val nullptr: NativePointer<*> = NativePointer<uint8_t>(0L)

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
    NativeArray(allocator.allocate(arrayLayout, count))

fun <T : NativeType> T.mallocArr(count: Int, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(arrayLayout, count.toLong()))

context(allocator: SegmentAllocator)
fun <T : NativeType> T.mallocArr(count: Long): NativeArray<T> = NativeArray(allocator.allocate(arrayLayout, count))

context(allocator: SegmentAllocator)
fun <T : NativeType> T.mallocArr(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(arrayLayout, count.toLong()))

fun <T : NativeType> T.callocArr(count: Long, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(arrayLayout, count).apply { fill(0) })

fun <T : NativeType> T.callocArr(count: Int, allocator: SegmentAllocator): NativeArray<T> =
    NativeArray(allocator.allocate(arrayLayout, count.toLong()).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : NativeType> T.callocArr(count: Long): NativeArray<T> =
    NativeArray(allocator.allocate(arrayLayout, count).apply { fill(0) })

context(allocator: SegmentAllocator)
fun <T : NativeType> T.callocArr(count: Int): NativeArray<T> =
    NativeArray(allocator.allocate(arrayLayout, count.toLong()).apply { fill(0) })

val `_$OMNI_SEGMENT$_` = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE)

typealias char = int8_t
typealias size_t = int64_t

var NativeArray<char>.string: String
    get() = _segment.getString(0L)
    set(value) {
        _segment.setString(0L, value)
    }

var NativePointer<char>.string: String
    get() = `_$OMNI_SEGMENT$_`.getString(_address)
    set(value) {
        `_$OMNI_SEGMENT$_`.setString(_address, value)
    }

fun String.c_str(allocator: SegmentAllocator): NativeArray<char> = NativeArray(allocator.allocateFrom(this))

context(allocator: SegmentAllocator)
fun String.c_str(): NativeArray<char> = NativeArray(allocator.allocateFrom(this))


object `_$Helper$_` {
    @JvmField
    val linker: Linker = Linker.nativeLinker()

    @JvmField
    val loaderLookup: SymbolLookup = SymbolLookup.loaderLookup()

    @JvmField
    val pointerLayout: AddressLayout =
        ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE))

    @JvmField
    val symbolLookup: SymbolLookup = SymbolLookup { name ->
        val inLoader = loaderLookup.find(name)
        if (inLoader.isEmpty) {
            loaderLookup.find(name)
        } else {
            inLoader
        }
    }

    @JvmStatic
    fun pointerLayout(elementLayout: MemoryLayout): AddressLayout {
        return ValueLayout.ADDRESS.withTargetLayout(
            MemoryLayout.sequenceLayout(
                Long.MAX_VALUE / elementLayout.byteSize(),
                elementLayout
            )
        )
    }

    @JvmStatic
    public fun findSymbol(symbol: String): MemorySegment {
        return symbolLookup.find(symbol).getOrElse { error("unable to find symbol $symbol") }
    }
}
