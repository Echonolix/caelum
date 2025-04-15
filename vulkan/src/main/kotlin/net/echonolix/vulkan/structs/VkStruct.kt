package net.echonolix.vulkan.structs

import net.echonolix.ktffi.NativeArray
import net.echonolix.ktffi.NativeStruct
import net.echonolix.ktffi.NativeValue
import net.echonolix.vulkan.enums.VkStructureType
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout

public sealed class VkStruct<T : VkStruct<T>>(
    vararg members: MemoryLayout,
) : NativeStruct<T>(*members) {
    public open val structType: VkStructureType?
        get() = null

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: Long): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: Int): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: Long): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: Int): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: Long): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: Int): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Long): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Int): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    private fun MemorySegment.initValue(): MemorySegment {
        this.fill(0)
        structType?.let {
            this.set(ValueLayout.JAVA_INT, 0L, it.value)
        }
        return this
    }

    private fun MemorySegment.initArray(count: Long): MemorySegment {
        this.fill(0)
        structType?.let { structType ->
            for (i in 0..<count) {
                this.set(ValueLayout.JAVA_INT, i * layout.byteSize(), structType.value)
            }
        }
        return this
    }

    @JvmName("allocate")
    public fun allocate(allocator: SegmentAllocator, count: Long): NativeArray<T> =
        NativeArray(allocator.allocate(layout, count).initArray(count))

    @JvmName("allocate")
    public fun allocate(allocator: SegmentAllocator, count: Int): NativeArray<T> =
        NativeArray(allocator.allocate(layout, count.toLong()).initArray(count.toLong()))

    context(allocator: SegmentAllocator)
    @JvmName("allocate-114")
    public fun allocate(count: Long): NativeArray<T> =
        NativeArray(allocator.allocate(layout, count).initArray(count))

    context(allocator: SegmentAllocator)
    @JvmName("allocate-514")
    public fun allocate(count: Int): NativeArray<T> =
        NativeArray(allocator.allocate(layout, count.toLong()).initArray(count.toLong()))

    @JvmName("allocate")
    public fun allocate(allocator: SegmentAllocator): NativeValue<T> =
        NativeValue(allocator.allocate(layout).initValue())

    context(allocator: SegmentAllocator)
    @JvmName("allocate-420")
    public fun allocate(): NativeValue<T> =
        NativeValue(allocator.allocate(layout).initValue())
}