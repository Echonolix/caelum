package net.echonolix.caelum.vulkan

import net.echonolix.caelum.vulkan.enums.VkResult

public class VkException(public val result: VkResult) : RuntimeException() {
    override val message: String
        get() = result.name
}