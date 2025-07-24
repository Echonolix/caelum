@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "ReplaceGetOrSet")
@file:OptIn(UnsafeAPI::class)

package net.echonolix.caelum.vulkan.handles

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.enums.VkObjectType

interface VkHandle<T : VkHandle<T>> : NEnum<T, Long> {
    override val nType: NPrimitive<Long, Long> get() = NInt64
    override val value: Long
    val objectType: VkObjectType


    abstract class Impl<T : VkHandle<T>>(): VkHandle<T> {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Impl<*>) return false

            if (objectType != other.objectType) return false
            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int {
            var result = objectType.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }
    }
}

val NValue<out VkHandle<*>>.value: Long
    get() = reinterpret_cast<NInt64>(this).value

fun <T : VkHandle<*>> NValue<T>.set(handle: T) {
    reinterpret_cast<NInt64>(this).value = handle.value
}

operator fun NPointer<out VkHandle<*>>.get(index: Long): Long =
    reinterpret_cast<NInt64>(this)[index]

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
    reinterpret_cast<NInt64>(this)[index] = value
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
    reinterpret_cast<NInt64>(this)[index] = value.value
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