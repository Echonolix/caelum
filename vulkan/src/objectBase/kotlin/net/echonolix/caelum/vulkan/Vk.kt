package net.echonolix.caelum.vulkan

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.enums.VkResult
import net.echonolix.caelum.vulkan.functions.*
import net.echonolix.caelum.vulkan.handles.VkInstance
import net.echonolix.caelum.vulkan.handles.value
import net.echonolix.caelum.vulkan.structs.VkAllocationCallbacks
import net.echonolix.caelum.vulkan.structs.VkExtensionProperties
import net.echonolix.caelum.vulkan.structs.VkInstanceCreateInfo
import net.echonolix.caelum.vulkan.structs.VkLayerProperties

object Vk {
    init {
        System.loadLibrary("vulkan-1")
    }

    val vkGetInstanceProcAddr: VkFuncGetInstanceProcAddr =
        VkFuncGetInstanceProcAddr.fromNativeData(APIHelper.findSymbol("vkGetInstanceProcAddr"))

    val vkCreateInstance: VkFuncCreateInstance =
        getGlobalFunc(VkFuncCreateInstance)
    val vkEnumerateInstanceExtensionProperties: VkFuncEnumerateInstanceExtensionProperties =
        getGlobalFunc(VkFuncEnumerateInstanceExtensionProperties)
    val vkEnumerateInstanceLayerProperties: VkFuncEnumerateInstanceLayerProperties =
        getGlobalFunc(VkFuncEnumerateInstanceLayerProperties)
    val vkEnumerateInstanceVersion: VkFuncEnumerateInstanceVersion =
        getGlobalFunc(VkFuncEnumerateInstanceVersion)

    private fun <T : VkFunction> getGlobalFunc(
        funcDescriptor: VkFunction.Descriptor<T>
    ): T = MemoryStack {
        funcDescriptor.fromNativeData(
            NPointer(
                vkGetInstanceProcAddr.invokeNative(
                    0L,
                    funcDescriptor.name.c_str()._address
                )
            )
        )
    }

    fun createInstance(
        @CTypeName("VkInstanceCreateInfo*") pCreateInfo: NPointer<VkInstanceCreateInfo>,
        @CTypeName("VkAllocationCallbacks*") pAllocator: NPointer<VkAllocationCallbacks>?
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

    fun enumerateInstanceExtensionProperties(
        @CTypeName("char*") pLayerName: NPointer<NChar>?,
        @CTypeName("uint32_t*") pPropertyCount: NPointer<NUInt32>,
        @CTypeName("VkExtensionProperties*") pProperties: NPointer<VkExtensionProperties>?,
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

    fun enumerateInstanceLayerProperties(
        @CTypeName("uint32_t*") pPropertyCount: NPointer<NUInt32>,
        @CTypeName("VkLayerProperties*") pProperties: NPointer<VkLayerProperties>?
    ): Result<Unit> {
        return when (val result = vkEnumerateInstanceLayerProperties(pPropertyCount, pProperties)) {
            VkResult.VK_SUCCESS,
            VkResult.VK_INCOMPLETE -> Result.success(Unit)
            VkResult.VK_ERROR_OUT_OF_HOST_MEMORY,
            VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY -> Result.failure(VkException(result))
            else -> error("Unexpected result from vkEnumerateInstanceLayerProperties: $result")
        }
    }

    fun enumerateInstanceVersion(
        @CTypeName("uint32_t*") pApiVersion: NPointer<NUInt32>
    ): Result<VkResult> {
        return when (val result = vkEnumerateInstanceVersion(pApiVersion)) {
            VkResult.VK_SUCCESS -> Result.success(result)
            VkResult.VK_ERROR_OUT_OF_HOST_MEMORY -> Result.failure(VkException(result))
            else -> error("Unexpected result from vkEnumerateInstanceVersion: $result")
        }
    }
}