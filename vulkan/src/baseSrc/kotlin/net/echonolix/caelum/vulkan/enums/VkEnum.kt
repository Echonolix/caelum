package net.echonolix.caelum.vulkan.enums

import net.echonolix.caelum.NInt32
import net.echonolix.caelum.NPrimitive
import net.echonolix.caelum.vulkan.VkEnumBase

interface VkEnum<T : VkEnum<T>> : VkEnumBase<T, Int> {
    override val nType: NPrimitive<Int, Int> get() = NInt32
    override val value: Int
}