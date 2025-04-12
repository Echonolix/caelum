package net.echonolix.vulkan

import net.echonolix.ktffi.malloc
import net.echonolix.vulkan.enums.*
import java.lang.foreign.Arena

fun main() {
    @Suppress("UNCHECKED_CAST")
    with(Arena.ofAuto()) {
        var a by VkObjectType.malloc()
        a = VkObjectType.VK_OBJECT_TYPE_EVENT
        println(a)
        a = VkObjectType.VK_OBJECT_TYPE_IMAGE
        println(a)
    }
}