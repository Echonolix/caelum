package net.echonolix.caelum.openal.functions

import net.echonolix.caelum.NFunction
import net.echonolix.caelum.NType

/** Base type for OpenAL AL and ALC function-pointer descriptors. */
public interface OpenALFunction : NFunction {
    override val typeDescriptor: Descriptor<*>

    public abstract class Descriptor<T : OpenALFunction>(
        name: String,
        funInterfaceClass: Class<out OpenALFunction>,
        returnType: NType.Descriptor<*>?,
        vararg parameters: NType.Descriptor<*>
    ) : NFunction.Descriptor<T>(name, funInterfaceClass, returnType, *parameters) {
        final override val manager: NFunction.Manager
            get() = Companion
    }

    public companion object : NFunction.Manager.Impl()
}
