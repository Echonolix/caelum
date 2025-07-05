package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.NFunction
import net.echonolix.caelum.NType

public interface GLFWFunction : NFunction {
    override val typeDescriptor: Descriptor<*>

    public abstract class Descriptor<T : GLFWFunction>(
        name: String,
        funInterfaceClass: Class<out GLFWFunction>,
        returnType: NType.Descriptor<*>?,
        vararg parameters: NType.Descriptor<*>
    ) : NFunction.Descriptor<T>(name, funInterfaceClass, returnType, *parameters) {
        final override val manager: NFunction.Manager
            get() = Companion
    }

    public companion object : NFunction.Manager.Impl()
}