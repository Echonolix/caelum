package net.echonolix.caelum.vulkan.functions

import net.echonolix.caelum.NFunction
import net.echonolix.caelum.TypeDescriptor
import kotlin.reflect.KFunction

interface VkFunction : NFunction {
    override val typeDescriptor: TypeDescriptorImpl<*>

    abstract class TypeDescriptorImpl<T : VkFunction>(
        name: String,
        invokeNativeFunc: KFunction<*>,
        returnType: TypeDescriptor<*>?,
        vararg parameters: TypeDescriptor<*>
    ) : NFunction.TypeDescriptorImpl<T>(name, invokeNativeFunc, returnType, *parameters) {
        final override val manager: NFunction.Manager
            get() = Companion
    }

    companion object : NFunction.Manager.Impl()
}