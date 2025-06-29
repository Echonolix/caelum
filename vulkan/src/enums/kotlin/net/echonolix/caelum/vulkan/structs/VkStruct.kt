package net.echonolix.caelum.vulkan.structs

import net.echonolix.caelum.*
import net.echonolix.caelum.APIHelper.`_$OMNI_SEGMENT$_`
import net.echonolix.caelum.vulkan.enums.VkStructureType
import java.lang.foreign.MemoryLayout

abstract class VkStruct<T : VkStruct<T>>(
    vararg members: MemoryLayout,
) : NStruct.Impl<T>(*members), CustomAllocateOnly<T> {
    abstract val structType: VkStructureType?

    override fun allocate(allocator: AllocateScope): NValue<T> {
        val value = allocator.calloc(this)
        structType?.let {
            NInt.valueVarHandle.set(`_$OMNI_SEGMENT$_`, value._address, it.value)
        }
        return value
    }

    override fun allocate(allocator: AllocateScope, count: Long): NArray<T> {
        val array = allocator.calloc(this, count)
        structType?.let { structType ->
            for (i in 0L..<count) {
                NInt.valueVarHandle.set(`_$OMNI_SEGMENT$_`, arrayByteOffsetHandle.invokeExact(array._address, i) as Long, structType.value)
            }
        }
        return array
    }
}

inline fun <T : VkStruct<T>> T.allocate(allocator: AllocateScope, block: NValue<T>.() -> Unit): NValue<T> =
    allocate(allocator).apply(block)

context(allocator: AllocateScope)
inline fun <T : VkStruct<T>> T.allocate(block: NValue<T>.() -> Unit): NValue<T> =
    allocate(allocator).apply(block)