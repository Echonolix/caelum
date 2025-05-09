package net.echonolix.caelum.vulkan.structs

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.enums.VkStructureType
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout

abstract class VkStruct<T : VkStruct<T>>(
    vararg members: MemoryLayout,
) : NStruct.Impl<T>(*members), CustomAllocateOnly<T> {
    abstract val structType: VkStructureType?

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

    override fun allocate(allocator: SegmentAllocator): NValue<T> =
        NValue(allocator.allocate(layout).initValue())

    override fun allocate(allocator: SegmentAllocator, count: Long): NArray<T> =
        NArray(allocator.allocate(layout, count).initArray(count))
}

inline fun <T : VkStruct<T>> T.allocate(allocator: SegmentAllocator, block: NValue<T>.() -> Unit): NValue<T> =
    allocate(allocator).apply(block)

context(allocator: MemoryStack.Frame)
inline fun <T : VkStruct<T>> T.allocate(block: NValue<T>.() -> Unit): NValue<T> =
    allocate(allocator).apply(block)