package net.echonolix.caelum.vulkan.functions

import net.echonolix.caelum.NFunction
import net.echonolix.caelum.NType

interface VkFunction : NFunction {
    override val typeDescriptor: Descriptor<*>

    abstract class Descriptor<T : VkFunction>(
        name: String,
        funInterfaceClass: Class<out VkFunction>,
        returnType: NType.Descriptor<*>?,
        vararg parameters: NType.Descriptor<*>
    ) : NFunction.Descriptor<T>(name, funInterfaceClass, returnType, *parameters) {
        final override val manager: NFunction.Manager
            get() = Companion
    }

    companion object : NFunction.Manager.Impl()
}