package net.echonolix.caelum.assimp.functions

import net.echonolix.caelum.NFunction
import net.echonolix.caelum.NType

/** Base type for callbacks declared by the Assimp C API. */
public interface AssimpFunction : NFunction {
    override val typeDescriptor: Descriptor<*>

    public abstract class Descriptor<T : AssimpFunction>(
        name: String,
        funInterfaceClass: Class<out AssimpFunction>,
        returnType: NType.Descriptor<*>?,
        vararg parameters: NType.Descriptor<*>,
    ) : NFunction.Descriptor<T>(name, funInterfaceClass, returnType, *parameters) {
        final override val manager: NFunction.Manager
            get() = Companion
    }

    public companion object : NFunction.Manager.Impl()
}
