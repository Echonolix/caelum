package net.echonolix.caelum.vulkan.functions

import net.echonolix.caelum.NativeFunction
import net.echonolix.caelum.TypeDescriptor
import kotlin.reflect.KFunction

interface VkFunction : NativeFunction {
    override val typeDescriptor: TypeDescriptorImpl<*>

    abstract class TypeDescriptorImpl<T : VkFunction>(
        name: String,
        invokeNativeFunc: KFunction<*>,
        returnType: TypeDescriptor<*>?,
        vararg parameters: TypeDescriptor<*>
    ) : NativeFunction.TypeDescriptorImpl<T>(name, invokeNativeFunc, returnType, *parameters) {
        final override val manager: NativeFunction.Manager
            get() = Companion
    }

    companion object : NativeFunction.Manager.Impl()
}