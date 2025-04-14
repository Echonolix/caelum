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
    public fun mallocArr(count: Long): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun mallocArr(count: Int): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun callocArr(count: Long): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun callocArr(count: Int): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")


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

    private fun MemorySegment.init(): MemorySegment {
        this.fill(0)
        structType?.let {
            this.set(ValueLayout.JAVA_INT, 0L, it.value)
        }
        return this
    }

    context(allocator: SegmentAllocator)
    public fun allocate(count: Long): NativeArray<T> = NativeArray(allocator.allocate(layout, count).apply {
        fill(0)
        structType?.let { structType ->
            for (i in 0..<count) {
                this.set(ValueLayout.JAVA_INT, i * layout.byteSize(), structType.value)
            }
        }
    })

    context(allocator: SegmentAllocator)
    public fun allocate(count: Int): NativeArray<T> = NativeArray(allocator.allocate(layout, count.toLong()).apply {
        fill(0)
        structType?.let { structType ->
            for (i in 0..<count) {
                this.set(ValueLayout.JAVA_INT, i * layout.byteSize(), structType.value)
            }
        }
    })

    context(allocator: SegmentAllocator)
    public fun allocate(): NativeValue<T> = NativeValue(allocator.allocate(layout).apply {
        fill(0)
        structType?.let {
            this.set(ValueLayout.JAVA_INT, 0L, it.value)
        }
    })
}