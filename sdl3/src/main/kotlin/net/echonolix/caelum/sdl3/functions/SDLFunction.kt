package net.echonolix.caelum.sdl3.functions

import net.echonolix.caelum.NFunction
import net.echonolix.caelum.NType

public interface SDLFunction : NFunction {
    override val typeDescriptor: Descriptor<*>

    public abstract class Descriptor<T : SDLFunction>(
        name: String,
        funInterfaceClass: Class<out SDLFunction>,
        returnType: NType.Descriptor<*>?,
        vararg parameters: NType.Descriptor<*>,
    ) : NFunction.Descriptor<T>(name, funInterfaceClass, returnType, *parameters) {
        final override val manager: NFunction.Manager
            get() = Companion
    }

    public companion object : NFunction.Manager.Impl()
}
