package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.APIHelper
import net.echonolix.caelum.CTypeName
import net.echonolix.caelum.NFunction
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.vulkan.functions.VkFuncPtrGetInstanceProcAddrLUNARG
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

public val glfwInitVulkanLoader: GLFWFuncInitVulkanLoader =
    GLFWFuncInitVulkanLoader.fromNativeData(APIHelper.findSymbol("glfwInitVulkanLoader"))

public fun interface GLFWFuncInitVulkanLoader : NFunction {
    override val typeDescriptor: NFunction.TypeDescriptorImpl<GLFWFuncInitVulkanLoader>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("PFN_vkGetInstanceProcAddr") loader: NPointer<VkFuncPtrGetInstanceProcAddrLUNARG>,
    )

    public fun invokeNative(loader: Long) {
        invoke(NPointer.fromNativeData(loader))
    }

    public companion object TypeDescriptor : NFunction.TypeDescriptorImpl<GLFWFuncInitVulkanLoader>(
        "glfwInitVulkanLoader",
        GLFWFuncInitVulkanLoader::invokeNative,
        NPointer,
    ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncInitVulkanLoader = Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NFunction.Impl(funcHandle),
            GLFWFuncInitVulkanLoader {
            override fun invoke(loader: NPointer<VkFuncPtrGetInstanceProcAddrLUNARG>) {
                invokeNative(NPointer.toNativeData(loader))
            }

            override fun invokeNative(loader: Long) {
                funcHandle.invokeExact(loader)
            }
        }
    }
}
