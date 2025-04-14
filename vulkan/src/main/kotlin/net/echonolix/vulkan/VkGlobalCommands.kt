package net.echonolix.vulkan

import net.echonolix.ktffi.APIHelper
import net.echonolix.ktffi.MemoryStack
import net.echonolix.ktffi.NativePointer
import net.echonolix.ktffi.c_str
import net.echonolix.vulkan.functions.*
import net.echonolix.vulkan.handles.VkInstance

@Suppress("UNCHECKED_CAST")
public object VkGlobalCommands {
    private val nullInstance = VkInstance.Impl(0L)
    public val vkGetInstanceProcAddr: VkCmdGetInstanceProcAddr =
        VkCmdGetInstanceProcAddr.fromNativeData(APIHelper.findSymbol("vkGetInstanceProcAddr"))
    public val vkCreateInstance: VkCmdCreateInstance = MemoryStack {
        VkCmdCreateInstance.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkCreateInstance".c_str()
            ) as NativePointer<VkCmdCreateInstance>
        )
    }
    public val vkEnumerateInstanceExtensionProperties: VkCmdEnumerateInstanceExtensionProperties = MemoryStack {
        VkCmdEnumerateInstanceExtensionProperties.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkEnumerateInstanceExtensionProperties".c_str()
            ) as NativePointer<VkCmdEnumerateInstanceExtensionProperties>
        )
    }
    public val vkEnumerateInstanceLayerProperties: VkCmdEnumerateInstanceLayerProperties = MemoryStack {
        VkCmdEnumerateInstanceLayerProperties.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkEnumerateInstanceLayerProperties".c_str()
            ) as NativePointer<VkCmdEnumerateInstanceLayerProperties>
        )
    }
    public val vkEnumerateInstanceVersion: VkCmdEnumerateInstanceVersion = MemoryStack {
        VkCmdEnumerateInstanceVersion.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkEnumerateInstanceVersion".c_str()
            ) as NativePointer<VkCmdEnumerateInstanceVersion>
        )
    }
}