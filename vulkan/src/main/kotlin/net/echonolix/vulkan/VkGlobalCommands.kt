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

    public val vkCreateInstance: VkFuncCreateInstance =
        vkGetInstanceFunc(nullInstance, VkFuncCreateInstance)
    public val vkEnumerateInstanceExtensionProperties: VkFuncEnumerateInstanceExtensionProperties =
        vkGetInstanceFunc(nullInstance, VkFuncEnumerateInstanceExtensionProperties)
    public val vkEnumerateInstanceLayerProperties: VkFuncEnumerateInstanceLayerProperties =
        vkGetInstanceFunc(nullInstance, VkFuncEnumerateInstanceLayerProperties)
    public val vkEnumerateInstanceVersion: VkFuncEnumerateInstanceVersion =
        vkGetInstanceFunc(nullInstance, VkFuncEnumerateInstanceVersion)
}

public fun <T : VkFunction> vkGetInstanceFunc(
    instance: VkInstance,
    funcDescriptor: VkFunction.TypeDescriptorImpl<T>
): T {
    return MemoryStack {
        @Suppress("UNCHECKED_CAST")
        funcDescriptor.fromNativeData(
            VkGlobalCommands.vkGetInstanceProcAddr(instance, funcDescriptor.name.c_str()) as NativePointer<T>
        )
    }
}