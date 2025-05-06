package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.handles.VkInstanceHandle
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

public val glfwGetInstanceProcAddress: GLFWFuncGetInstanceProcAddress =
    GLFWFuncGetInstanceProcAddress.fromNativeData(APIHelper.findSymbol("glfwGetInstanceProcAddress"))

public fun interface GLFWFuncGetInstanceProcAddress : NFunction {
    override val typeDescriptor: NFunction.Descriptor<GLFWFuncGetInstanceProcAddress>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("VkInstance") instance: VkInstanceHandle,
        @CTypeName("char*") procname: NPointer<NativeChar>,
    ): NPointer<GLFWFuncPtrVKProc>

    public fun invokeNative(
        instance: Long,
        procname: Long,
    ): Long = NPointer.toNativeData(
        invoke(
            VkInstanceHandle.fromNativeData(instance),
            NPointer.fromNativeData(procname),
        )
    )

    public companion object TypeDescriptor : NFunction.Descriptor<GLFWFuncGetInstanceProcAddress>(
        "glfwGetInstanceProcAddress",
        GLFWFuncGetInstanceProcAddress::invokeNative,
        NPointer,
        NPointer,
        NPointer
    ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncGetInstanceProcAddress = Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NFunction.Impl(funcHandle),
            GLFWFuncGetInstanceProcAddress {
            override fun invoke(
                instance: VkInstanceHandle,
                procname: NPointer<NativeChar>,
            ): NPointer<GLFWFuncPtrVKProc> = NPointer.fromNativeData(
                invokeNative(
                    VkInstanceHandle.toNativeData(instance),
                    NPointer.toNativeData(procname),
                )
            )

            override fun invokeNative(
                instance: Long,
                procname: Long,
            ): Long = funcHandle.invokeExact(
                instance,
                procname
            ) as Long
        }
    }
}
