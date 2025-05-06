package net.echonolix.caelum.vulkan.unions

import net.echonolix.caelum.NUnion
import java.lang.foreign.MemoryLayout

abstract class VkUnion<T : VkUnion<T>>(
    vararg members: MemoryLayout
) : NUnion<T>(*members)