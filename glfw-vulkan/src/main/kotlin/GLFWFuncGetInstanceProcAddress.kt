package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.handles.VkInstanceHandle
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

public val glfwGetInstanceProcAddress: GLFWFuncGetInstanceProcAddress =
    GLFWFuncGetInstanceProcAddress.fromNativeData(APIHelper.findSymbol("glfwGetInstanceProcAddress"))

public fun interface GLFWFuncGetInstanceProcAddress : NativeFunction {
    override val typeDescriptor: NativeFunction.TypeDescriptorImpl<GLFWFuncGetInstanceProcAddress>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("VkInstance") instance: VkInstanceHandle,
        @CTypeName("char*") procname: NativePointer<NativeChar>,
    ): NativePointer<GLFWFuncPtrVKProc>

    public fun invokeNative(
        instance: Long,
        procname: Long,
    ): Long = NativePointer.toNativeData(
        invoke(
            VkInstanceHandle.fromNativeData(instance),
            NativePointer.fromNativeData(procname),
        )
    )

    public companion object TypeDescriptor : NativeFunction.TypeDescriptorImpl<GLFWFuncGetInstanceProcAddress>(
        "glfwGetInstanceProcAddress",
        GLFWFuncGetInstanceProcAddress::invokeNative,
        NativePointer,
        NativePointer,
        NativePointer
    ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncGetInstanceProcAddress = Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NativeFunction.Impl(funcHandle),
            GLFWFuncGetInstanceProcAddress {
            override fun invoke(
                instance: VkInstanceHandle,
                procname: NativePointer<NativeChar>,
            ): NativePointer<GLFWFuncPtrVKProc> = NativePointer.fromNativeData(
                invokeNative(
                    VkInstanceHandle.toNativeData(instance),
                    NativePointer.toNativeData(procname),
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
