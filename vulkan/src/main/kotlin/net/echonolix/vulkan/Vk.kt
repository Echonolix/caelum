package net.echonolix.vulkan

import net.echonolix.ktffi.*
import net.echonolix.vulkan.enums.VkResult
import net.echonolix.vulkan.functions.*
import net.echonolix.vulkan.handles.VkInstance
import net.echonolix.vulkan.handles.value
import net.echonolix.vulkan.structs.VkAllocationCallbacks
import net.echonolix.vulkan.structs.VkExtensionProperties
import net.echonolix.vulkan.structs.VkInstanceCreateInfo
import net.echonolix.vulkan.structs.VkLayerProperties

public object Vk {
    init {
        System.loadLibrary("vulkan-1")
    }

    private val nullInstance = VkInstance.Impl(0L)

    private val vkGetInstanceProcAddr: VkFuncGetInstanceProcAddr =
        VkFuncGetInstanceProcAddr.fromNativeData(APIHelper.findSymbol("vkGetInstanceProcAddr"))

    private val vkCreateInstance: VkFuncCreateInstance =
        getInstanceFunc(nullInstance, VkFuncCreateInstance)
    private val vkEnumerateInstanceExtensionProperties: VkFuncEnumerateInstanceExtensionProperties =
        getInstanceFunc(nullInstance, VkFuncEnumerateInstanceExtensionProperties)
    private val vkEnumerateInstanceLayerProperties: VkFuncEnumerateInstanceLayerProperties =
        getInstanceFunc(nullInstance, VkFuncEnumerateInstanceLayerProperties)
    private val vkEnumerateInstanceVersion: VkFuncEnumerateInstanceVersion =
        getInstanceFunc(nullInstance, VkFuncEnumerateInstanceVersion)

    public fun <T : VkFunction> getInstanceFunc(
        instance: VkInstance,
        funcDescriptor: VkFunction.TypeDescriptorImpl<T>
    ): T {
        return MemoryStack {
            @Suppress("UNCHECKED_CAST")
            funcDescriptor.fromNativeData(
                vkGetInstanceProcAddr(instance, funcDescriptor.name.c_str()) as NativePointer<T>
            )
        }
    }

    public fun createInstance(
        @CTypeName("VkInstanceCreateInfo*") pCreateInfo: NativePointer<VkInstanceCreateInfo>,
        @CTypeName("VkAllocationCallbacks*") pAllocator: NativePointer<VkAllocationCallbacks>?
    ): Result<VkInstance> {
        return MemoryStack {
            val instanceV = VkInstance.malloc()
            when (val result = vkCreateInstance(pCreateInfo, pAllocator, instanceV.ptr())) {
                VkResult.VK_SUCCESS -> Result.success(VkInstance.fromNativeData(instanceV.value))
                VkResult.VK_ERROR_OUT_OF_HOST_MEMORY,
                VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY,
                VkResult.VK_ERROR_INITIALIZATION_FAILED,
                VkResult.VK_ERROR_LAYER_NOT_PRESENT,
                VkResult.VK_ERROR_EXTENSION_NOT_PRESENT,
                VkResult.VK_ERROR_INCOMPATIBLE_DRIVER -> Result.failure(VkException(result))
                else -> error("Unexpected result from vkCreateInstance: $result")
            }
        }
    }

    public fun enumerateInstanceExtensionProperties(
        @CTypeName("char*") pLayerName: NativePointer<NativeChar>?,
        @CTypeName("uint32_t*") pPropertyCount: NativePointer<NativeUInt32>,
        @CTypeName("VkExtensionProperties*") pProperties: NativePointer<VkExtensionProperties>?,
    ): Result<Unit> {
        return when (val result = vkEnumerateInstanceExtensionProperties(pLayerName, pPropertyCount, pProperties)) {
            VkResult.VK_SUCCESS,
            VkResult.VK_INCOMPLETE -> Result.success(Unit)
            VkResult.VK_ERROR_OUT_OF_HOST_MEMORY,
            VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY,
            VkResult.VK_ERROR_LAYER_NOT_PRESENT -> Result.failure(VkException(result))
            else -> error("Unexpected result from vkEnumerateInstanceExtensionProperties: $result")
        }
    }
    public fun enumerateInstanceLayerProperties(
        @CTypeName("uint32_t*") pPropertyCount: NativePointer<NativeUInt32>,
        @CTypeName("VkLayerProperties*") pProperties: NativePointer<VkLayerProperties>?
    ): Result<Unit> {
        return when (val result = vkEnumerateInstanceLayerProperties(pPropertyCount, pProperties)) {
            VkResult.VK_SUCCESS,
            VkResult.VK_INCOMPLETE -> Result.success(Unit)
            VkResult.VK_ERROR_OUT_OF_HOST_MEMORY,
            VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY -> Result.failure(VkException(result))
            else -> error("Unexpected result from vkEnumerateInstanceLayerProperties: $result")
        }
    }

    public fun enumerateInstanceVersion(
        @CTypeName("uint32_t*") pApiVersion: NativePointer<NativeUInt32>
    ): Result<VkResult> {
        return when (val result = vkEnumerateInstanceVersion(pApiVersion)) {
            VkResult.VK_SUCCESS -> Result.success(result)
            VkResult.VK_ERROR_OUT_OF_HOST_MEMORY -> Result.failure(VkException(result))
            else -> error("Unexpected result from vkEnumerateInstanceVersion: $result")
        }
    }
}