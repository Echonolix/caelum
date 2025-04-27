@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "ReplaceGetOrSet")
@file:OptIn(UnsafeAPI::class)

package net.echonolix.caelum.vulkan.handles

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.enums.VkObjectType
import java.lang.foreign.ValueLayout.JAVA_LONG

public interface VkHandle : NativeType {
    public val handle: Long

    public val objectType: VkObjectType

    public abstract class TypeDescriptor<T : VkHandle> : net.echonolix.caelum.TypeDescriptor.Impl<T>(JAVA_LONG)
}

public val NativeValue<out VkHandle>.value: Long
    get() = reinterpretCast<NativeInt64>(this).value

public operator fun NativePointer<out VkHandle>.get(index: Long): Long =
    reinterpretCast<NativeInt64>(this)[index]

public operator fun NativePointer<out VkHandle>.get(index: Int): Long =
    get(index.toLong())

public operator fun NativePointer<out VkHandle>.get(index: ULong): Long =
    get(index.toLong())

public operator fun NativePointer<out VkHandle>.get(index: UInt): Long =
    get(index.toLong())

public operator fun NativeArray<out VkHandle>.get(index: Long): Long =
    ptr().get(index)

public operator fun NativeArray<out VkHandle>.get(index: Int): Long =
    get(index.toLong())

public operator fun NativeArray<out VkHandle>.get(index: ULong): Long =
    get(index.toLong())

public operator fun NativeArray<out VkHandle>.get(index: UInt): Long =
    get(index.toLong())


public operator fun NativePointer<out VkHandle>.set(index: Long, value: Long) {
    reinterpretCast<NativeInt64>(this)[index] = value
}

public operator fun NativePointer<out VkHandle>.set(index: Int, value: Long): Unit =
    set(index.toLong(), value)

public operator fun NativePointer<out VkHandle>.set(index: ULong, value: Long): Unit =
    set(index.toLong(), value)

public operator fun NativePointer<out VkHandle>.set(index: UInt, value: Long): Unit =
    set(index.toLong(), value)

public operator fun NativeArray<out VkHandle>.set(index: Long, value: Long): Unit =
    ptr().set(index, value)

public operator fun NativeArray<out VkHandle>.set(index: Int, value: Long): Unit =
    set(index.toLong(), value)

public operator fun NativeArray<out VkHandle>.set(index: ULong, value: Long): Unit =
    set(index.toLong(), value)

public operator fun NativeArray<out VkHandle>.set(index: UInt, value: Long): Unit =
    set(index.toLong(), value)


public operator fun <T : VkHandle> NativePointer<T>.set(index: Long, value: T) {
    reinterpretCast<NativeInt64>(this)[index] = value.handle
}

public operator fun <T : VkHandle> NativePointer<T>.set(index: Int, value: T): Unit =
    set(index.toLong(), value)

public operator fun <T : VkHandle> NativePointer<T>.set(index: ULong, value: T): Unit =
    set(index.toLong(), value)

public operator fun <T : VkHandle> NativePointer<T>.set(index: UInt, value: T): Unit =
    set(index.toLong(), value)

public operator fun <T : VkHandle> NativeArray<T>.set(index: Long, value: T): Unit =
    ptr().set(index, value)

public operator fun <T : VkHandle> NativeArray<T>.set(index: Int, value: T): Unit =
    set(index.toLong(), value)

public operator fun <T : VkHandle> NativeArray<T>.set(index: ULong, value: T): Unit =
    set(index.toLong(), value)

public operator fun <T : VkHandle> NativeArray<T>.set(index: UInt, value: T): Unit =
    set(index.toLong(), value)