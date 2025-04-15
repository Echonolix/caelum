@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package net.echonolix.vulkan.handles

import net.echonolix.ktffi.*
import net.echonolix.vulkan.enums.VkObjectType
import java.lang.foreign.ValueLayout.JAVA_LONG

public interface VkHandle : NativeType {
    public val handle: Long

    public val objectType: VkObjectType

    public abstract class TypeDescriptor<T : VkHandle> : net.echonolix.ktffi.TypeDescriptor.Impl<T>(JAVA_LONG)
}

public inline val NativeValue<out VkHandle>.value: Long
    get() = (this as NativeValue<NativeInt64>).value

public inline fun NativePointer<out VkHandle>.get(index: Long): Long =
    (this as NativePointer<NativeInt64>)[index]

public inline fun NativePointer<out VkHandle>.get(index: Int): Long =
    (this as NativePointer<NativeInt64>)[index]

public inline fun NativeArray<out VkHandle>.get(index: Long): Long =
    (this as NativeArray<NativeInt64>)[index]

public inline fun NativeArray<out VkHandle>.get(index: Int): Long =
    (this as NativeArray<NativeInt64>)[index]

