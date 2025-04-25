package net.echonolix.caelum.vulkan.functions

import net.echonolix.caelum.NativeFunction
import net.echonolix.caelum.TypeDescriptor
import java.lang.invoke.MethodHandle

public sealed interface VkFunction : NativeFunction {
    override val typeDescriptor: TypeDescriptorImpl<*>

    public abstract class TypeDescriptorImpl<T : VkFunction>(
        name: String,
        upcallHandle: MethodHandle,
        returnType: TypeDescriptor<*>?,
        vararg parameters: TypeDescriptor<*>
    ) : NativeFunction.TypeDescriptorImpl<T>(name, upcallHandle, returnType, *parameters) {
        final override val manager: NativeFunction.Manager
            get() = Companion
    }

    public companion object : NativeFunction.Manager()
}