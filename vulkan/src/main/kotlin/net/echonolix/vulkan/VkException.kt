package net.echonolix.vulkan

import net.echonolix.vulkan.enums.VkResult

public class VkException(public val result: VkResult) : RuntimeException() {
    override val message: String
        get() = result.name
}