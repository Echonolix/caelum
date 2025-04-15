package net.echonolix.vulkan

import net.echonolix.ktffi.APIHelper
import net.echonolix.ktffi.MemoryStack
import net.echonolix.ktffi.NativePointer
import net.echonolix.ktffi.c_str
import net.echonolix.vulkan.functions.*
import net.echonolix.vulkan.handles.VkInstance

@Suppress("UNCHECKED_CAST")
public object VkGlobalCommands {
    init {
        System.loadLibrary("vulkan-1")
    }

    private val nullInstance = VkInstance.Impl(0L)
    public val vkGetInstanceProcAddr: VkFuncGetInstanceProcAddr =
        VkFuncGetInstanceProcAddr.fromNativeData(APIHelper.findSymbol("vkGetInstanceProcAddr"))
    public val vkCreateInstance: VkFuncCreateInstance = MemoryStack {
        VkFuncCreateInstance.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkCreateInstance".c_str()
            ) as NativePointer<VkFuncCreateInstance>
        )
    }
    public val vkEnumerateInstanceExtensionProperties: VkFuncEnumerateInstanceExtensionProperties = MemoryStack {
        VkFuncEnumerateInstanceExtensionProperties.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkEnumerateInstanceExtensionProperties".c_str()
            ) as NativePointer<VkFuncEnumerateInstanceExtensionProperties>
        )
    }
    public val vkEnumerateInstanceLayerProperties: VkFuncEnumerateInstanceLayerProperties = MemoryStack {
        VkFuncEnumerateInstanceLayerProperties.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkEnumerateInstanceLayerProperties".c_str()
            ) as NativePointer<VkFuncEnumerateInstanceLayerProperties>
        )
    }
    public val vkEnumerateInstanceVersion: VkFuncEnumerateInstanceVersion = MemoryStack {
        VkFuncEnumerateInstanceVersion.fromNativeData(
            vkGetInstanceProcAddr(
                nullInstance,
                "vkEnumerateInstanceVersion".c_str()
            ) as NativePointer<VkFuncEnumerateInstanceVersion>
        )
    }
}