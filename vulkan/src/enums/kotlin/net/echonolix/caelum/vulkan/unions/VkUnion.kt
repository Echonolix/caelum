package net.echonolix.caelum.vulkan.unions

import net.echonolix.caelum.NativeUnion
import java.lang.foreign.MemoryLayout

public abstract class VkUnion<T : VkUnion<T>>(
    vararg members: MemoryLayout
) : NativeUnion<T>(*members)