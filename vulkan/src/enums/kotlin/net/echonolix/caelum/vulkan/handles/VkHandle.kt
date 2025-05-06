@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "ReplaceGetOrSet")
@file:OptIn(UnsafeAPI::class)

package net.echonolix.caelum.vulkan.handles

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.enums.VkObjectType

interface VkHandle<T : VkHandle<T>> : NEnum<T, Long> {
    override val nType: NPrimitive<Long, Long> get() = NInt64
    override val value: Long
    val objectType: VkObjectType
}

val NValue<out VkHandle<*>>.value: Long
    get() = reinterpretCast<NInt64>(this).value

fun <T : VkHandle<*>> NValue<T>.set(handle: T) {
    reinterpretCast<NInt64>(this).value = handle.value
}

operator fun NPointer<out VkHandle<*>>.get(index: Long): Long =
    reinterpretCast<NInt64>(this)[index]

operator fun NPointer<out VkHandle<*>>.get(index: Int): Long =
    get(index.toLong())

operator fun NPointer<out VkHandle<*>>.get(index: ULong): Long =
    get(index.toLong())

operator fun NPointer<out VkHandle<*>>.get(index: UInt): Long =
    get(index.toLong())

operator fun NArray<out VkHandle<*>>.get(index: Long): Long =
    ptr().get(index)

operator fun NArray<out VkHandle<*>>.get(index: Int): Long =
    get(index.toLong())

operator fun NArray<out VkHandle<*>>.get(index: ULong): Long =
    get(index.toLong())

operator fun NArray<out VkHandle<*>>.get(index: UInt): Long =
    get(index.toLong())


operator fun NPointer<out VkHandle<*>>.set(index: Long, value: Long) {
    reinterpretCast<NInt64>(this)[index] = value
}

operator fun NPointer<out VkHandle<*>>.set(index: Int, value: Long): Unit =
    set(index.toLong(), value)

operator fun NPointer<out VkHandle<*>>.set(index: ULong, value: Long): Unit =
    set(index.toLong(), value)

operator fun NPointer<out VkHandle<*>>.set(index: UInt, value: Long): Unit =
    set(index.toLong(), value)

operator fun NArray<out VkHandle<*>>.set(index: Long, value: Long): Unit =
    ptr().set(index, value)

operator fun NArray<out VkHandle<*>>.set(index: Int, value: Long): Unit =
    set(index.toLong(), value)

operator fun NArray<out VkHandle<*>>.set(index: ULong, value: Long): Unit =
    set(index.toLong(), value)

operator fun NArray<out VkHandle<*>>.set(index: UInt, value: Long): Unit =
    set(index.toLong(), value)


operator fun <T : VkHandle<*>> NPointer<T>.set(index: Long, value: T) {
    reinterpretCast<NInt64>(this)[index] = value.value
}

operator fun <T : VkHandle<*>> NPointer<T>.set(index: Int, value: T): Unit =
    set(index.toLong(), value)

operator fun <T : VkHandle<*>> NPointer<T>.set(index: ULong, value: T): Unit =
    set(index.toLong(), value)

operator fun <T : VkHandle<*>> NPointer<T>.set(index: UInt, value: T): Unit =
    set(index.toLong(), value)

operator fun <T : VkHandle<*>> NArray<T>.set(index: Long, value: T): Unit =
    ptr().set(index, value)

operator fun <T : VkHandle<*>> NArray<T>.set(index: Int, value: T): Unit =
    set(index.toLong(), value)

operator fun <T : VkHandle<*>> NArray<T>.set(index: ULong, value: T): Unit =
    set(index.toLong(), value)

operator fun <T : VkHandle<*>> NArray<T>.set(index: UInt, value: T): Unit =
    set(index.toLong(), value)