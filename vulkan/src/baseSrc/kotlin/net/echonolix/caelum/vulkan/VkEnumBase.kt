package net.echonolix.caelum.vulkan

import net.echonolix.caelum.NativeType

public interface VkEnumBase<T> : NativeType {
    public val value: T
    public val nativeType: NativeType
}