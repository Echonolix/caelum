package net.echonolix.caelum.opengl

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

public class GLCapabilities internal constructor(
    internal val functions: Array<MethodHandle?>,
)

internal fun glHandleArbFunctionDescriptor(
    descriptor: FunctionDescriptor,
    mask: Long,
    macOs: Boolean,
): FunctionDescriptor {
    if (mask == 0L) return descriptor

    val rawHandleLayout = if (macOs) ValueLayout.ADDRESS else ValueLayout.JAVA_INT
    val parameters = descriptor.argumentLayouts().mapIndexed { index, layout ->
        if (mask and (1L shl (index + 1)) != 0L) rawHandleLayout else layout
    }.toTypedArray()
    val returnLayout = if (mask and 1L != 0L) {
        rawHandleLayout
    } else {
        descriptor.returnLayout().orElse(null)
    }
    return if (returnLayout == null) {
        FunctionDescriptor.ofVoid(*parameters)
    } else {
        FunctionDescriptor.of(returnLayout, *parameters)
    }
}

internal fun adaptGlHandleArbFunction(
    function: MethodHandle,
    mask: Long,
    macOs: Boolean,
): MethodHandle {
    if (mask == 0L) return function

    var adapted = function
    if (macOs) {
        function.type().parameterList().indices.forEach { index ->
            if (mask and (1L shl (index + 1)) != 0L) {
                adapted = MethodHandles.filterArguments(adapted, index, GLHandleArbAbi.longToAddress)
            }
        }
        if (mask and 1L != 0L) {
            adapted = MethodHandles.filterReturnValue(adapted, GLHandleArbAbi.addressToLong)
        }
        return adapted
    }

    var publicType = function.type()
    function.type().parameterList().indices.forEach { index ->
        if (mask and (1L shl (index + 1)) != 0L) {
            publicType = publicType.changeParameterType(index, java.lang.Long.TYPE)
        }
    }
    if (mask and 1L != 0L) {
        publicType = publicType.changeReturnType(java.lang.Long.TYPE)
    }
    return MethodHandles.explicitCastArguments(function, publicType)
}

internal fun isMacOs(osName: String = System.getProperty("os.name")): Boolean =
    osName.startsWith("Mac", ignoreCase = true)

private object GLHandleArbAbi {
    private val lookup = MethodHandles.lookup()

    val longToAddress: MethodHandle = lookup.findStatic(
        GLHandleArbAbi::class.java,
        "longToAddress",
        MethodType.methodType(MemorySegment::class.java, java.lang.Long.TYPE),
    )
    val addressToLong: MethodHandle = lookup.findStatic(
        GLHandleArbAbi::class.java,
        "addressToLong",
        MethodType.methodType(java.lang.Long.TYPE, MemorySegment::class.java),
    )

    @JvmStatic
    private fun longToAddress(address: Long): MemorySegment = MemorySegment.ofAddress(address)

    @JvmStatic
    private fun addressToLong(address: MemorySegment): Long = address.address()
}
