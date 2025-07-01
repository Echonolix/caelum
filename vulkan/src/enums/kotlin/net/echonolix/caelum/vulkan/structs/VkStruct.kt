package net.echonolix.caelum.vulkan.structs

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.enums.VkStructureType
import java.lang.foreign.MemoryLayout

abstract class VkStruct<T : VkStruct<T>>(
    vararg members: MemoryLayout,
) : NStruct.Impl<T>(*members), CustomAllocateOnly<T> {
    abstract val structType: VkStructureType?

    override fun allocate(allocator: AllocateScope): NValue<T> {
        val value = allocator.calloc(this)
        val sType = structType
        if (sType != null) {
            UnsafeUtil.UNSAFE_PUT_INT_NATIVE.invokeExact(value._address, sType.value)
        }
        return value
    }

    override fun allocate(allocator: AllocateScope, count: Long): NArray<T> {
        val array = allocator.calloc(this, count)
        val sType = structType
        if (sType != null) {
            val byteSize = layout.byteSize()
            for (i in 0L..<count) {
                UnsafeUtil.UNSAFE_PUT_INT_NATIVE.invokeExact(array._address + i * byteSize, sType.value)
            }
        }
        return array
    }
}

@StructAccessor
inline fun <T : VkStruct<T>> T.allocate(allocator: AllocateScope, block: @StructAccessor NValue<T>.() -> Unit): NValue<T> =
    allocate(allocator).apply(block)

@StructAccessor
context(allocator: AllocateScope)
inline fun <T : VkStruct<T>> T.allocate(block: @StructAccessor NValue<T>.() -> Unit): NValue<T> =
    allocate(allocator).apply(block)