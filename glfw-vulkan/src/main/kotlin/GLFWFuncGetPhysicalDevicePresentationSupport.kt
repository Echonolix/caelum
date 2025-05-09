package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.handles.VkInstanceHandle
import net.echonolix.caelum.vulkan.handles.VkPhysicalDeviceHandle
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

public val glfwGetPhysicalDevicePresentationSupport: GLFWFuncGetPhysicalDevicePresentationSupport =
    GLFWFuncGetPhysicalDevicePresentationSupport.fromNativeData(APIHelper.findSymbol("glfwGetPhysicalDevicePresentationSupport"))

public fun interface GLFWFuncGetPhysicalDevicePresentationSupport : NFunction {
    override val typeDescriptor: NFunction.Descriptor<GLFWFuncGetPhysicalDevicePresentationSupport>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("VkInstance") instance: VkInstanceHandle,
        @CTypeName("VkPhysicalDevice") device: VkPhysicalDeviceHandle,
        @CTypeName("uint32_t") queuefamily: UInt,
    ): Int

    public fun invokeNative(
        instance: Long,
        device: Long,
        queuefamily: Int,
    ): Int = invoke(
        VkInstanceHandle.fromNativeData(instance),
        VkPhysicalDeviceHandle.fromNativeData(device),
        NUInt32.fromNativeData(queuefamily),
    )

    public companion object TypeDescriptor :
        NFunction.Descriptor<GLFWFuncGetPhysicalDevicePresentationSupport>(
            "glfwGetPhysicalDevicePresentationSupport",
            GLFWFuncGetPhysicalDevicePresentationSupport::class.java,
            NInt,
            NPointer,
            NPointer,
            NUInt32
        ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncGetPhysicalDevicePresentationSupport =
            Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NFunction.Impl(funcHandle),
            GLFWFuncGetPhysicalDevicePresentationSupport {
            override fun invoke(
                instance: VkInstanceHandle,
                device: VkPhysicalDeviceHandle,
                queuefamily: UInt,
            ): Int = invokeNative(
                VkInstanceHandle.toNativeData(instance),
                VkPhysicalDeviceHandle.toNativeData(device),
                NUInt32.toNativeData(queuefamily)
            )

            override fun invokeNative(
                instance: Long,
                device: Long,
                queuefamily: Int,
            ): Int = funcHandle.invokeExact(
                instance,
                device,
                queuefamily
            ) as Int
        }
    }
}
