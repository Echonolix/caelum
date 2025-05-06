package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.APIHelper
import net.echonolix.caelum.CTypeName
import net.echonolix.caelum.NativeFunction
import net.echonolix.caelum.NativePointer
import net.echonolix.caelum.vulkan.functions.VkFuncPtrGetInstanceProcAddrLUNARG
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

public val glfwInitVulkanLoader: GLFWFuncInitVulkanLoader =
    GLFWFuncInitVulkanLoader.fromNativeData(APIHelper.findSymbol("glfwInitVulkanLoader"))

public fun interface GLFWFuncInitVulkanLoader : NativeFunction {
    override val typeDescriptor: NativeFunction.TypeDescriptorImpl<GLFWFuncInitVulkanLoader>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("PFN_vkGetInstanceProcAddr") loader: NativePointer<VkFuncPtrGetInstanceProcAddrLUNARG>,
    )

    public fun invokeNative(loader: Long) {
        invoke(NativePointer.fromNativeData(loader))
    }

    public companion object TypeDescriptor : NativeFunction.TypeDescriptorImpl<GLFWFuncInitVulkanLoader>(
        "glfwInitVulkanLoader",
        GLFWFuncInitVulkanLoader::invokeNative,
        NativePointer,
    ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncInitVulkanLoader = Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NativeFunction.Impl(funcHandle),
            GLFWFuncInitVulkanLoader {
            override fun invoke(loader: NativePointer<VkFuncPtrGetInstanceProcAddrLUNARG>) {
                invokeNative(NativePointer.toNativeData(loader))
            }

            override fun invokeNative(loader: Long) {
                funcHandle.invokeExact(loader)
            }
        }
    }
}
