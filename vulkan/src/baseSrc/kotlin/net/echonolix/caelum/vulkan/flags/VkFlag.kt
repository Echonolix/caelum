package net.echonolix.caelum.vulkan.flags

import net.echonolix.caelum.NInt32
import net.echonolix.caelum.NInt64
import net.echonolix.caelum.NPrimitive
import net.echonolix.caelum.vulkan.VkEnumBase

interface VkFlags32<T : VkFlags32<T>> : VkEnumBase<T, Int> {
    override val nType: NPrimitive<Int, Int> get() = NInt32
    override val value: Int
}

interface VkFlags64<T : VkFlags64<T>> : VkEnumBase<T, Long> {
    override val nType: NPrimitive<Long, Long> get() = NInt64
    override val value: Long
}