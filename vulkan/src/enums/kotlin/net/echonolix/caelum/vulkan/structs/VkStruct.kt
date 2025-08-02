package net.echonolix.caelum.vulkan.structs

import net.echonolix.caelum.*
import net.echonolix.caelum.APIHelper.POINTER_LAYOUT
import net.echonolix.caelum.APIHelper.`_$OMNI_SEGMENT$_`
import net.echonolix.caelum.vulkan.enums.VkStructureType
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.MemorySegment.copy
import java.lang.invoke.VarHandle
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

    companion object : VkStruct<Companion>(
        NInt32.layout.withName("sType"),
        POINTER_LAYOUT.withName("pNext"),
    ) {
        @JvmField
        internal val sType_valueVarHandle: VarHandle = layout.varHandle(groupElement("sType"))

        @JvmField
        internal val pNext_valueVarHandle: VarHandle = layout.varHandle(groupElement("pNext"))

        override val structType: VkStructureType?
            get() = null
    }
}

@StructAccessor
inline fun <T : VkStruct<T>> T.allocate(
    allocator: AllocateScope,
    block: @StructAccessor NValue<T>.() -> Unit
): NValue<T> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return allocate(allocator).apply(block)
}

@StructAccessor
context(allocator: AllocateScope)
inline fun <T : VkStruct<T>> T.allocate(block: @StructAccessor NValue<T>.() -> Unit): NValue<T> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return allocate(allocator).apply(block)
}
val NValue<out VkStruct<*>>.sType: VkStructureType
    get() = ptr().sType

val NPointer<out VkStruct<*>>.sType: VkStructureType
    get() = VkStructureType.fromNativeData(
        (VkStruct.sType_valueVarHandle.get(
            `_$OMNI_SEGMENT$_`,
            _address
        ) as Int)
    )

var NValue<out VkStruct<*>>.pNext: NPointer<out VkStruct<*>>
    get() = ptr().pNext
    set(`value`) {
        ptr().pNext = value
    }

var NPointer<out VkStruct<*>>.pNext: NPointer<out VkStruct<*>>
    get() = NPointer.fromNativeData((VkStruct.pNext_valueVarHandle.get(`_$OMNI_SEGMENT$_`, _address) as Long))
    set(`value`) {
        VkStruct.pNext_valueVarHandle.set(`_$OMNI_SEGMENT$_`, _address, NPointer.toNativeData(value))
    }