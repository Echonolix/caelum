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
import java.lang.invoke.MethodHandle

object Vk {
    init {
        System.loadLibrary("vulkan-1")
    }

    val vkGetInstanceProcAddr: MethodHandle =
        APIHelper.downcallHandleOf(
            APIHelper.findSymbol("vkGetInstanceProcAddr"),
            _vkGetInstanceProcAddr_fd
        )!!

    val vkCreateInstance: MethodHandle =
        APIHelper.downcallHandleOf(
            APIHelper.findSymbol("vkCreateInstance"),
            _vkCreateInstance_fd
        )!!
    val vkEnumerateInstanceExtensionProperties: MethodHandle =
        APIHelper.downcallHandleOf(
            APIHelper.findSymbol("vkEnumerateInstanceExtensionProperties"),
            _vkEnumerateInstanceExtensionProperties_fd
        )!!
    val vkEnumerateInstanceLayerProperties: MethodHandle =
        APIHelper.downcallHandleOf(
            APIHelper.findSymbol("vkEnumerateInstanceLayerProperties"),
            _vkEnumerateInstanceLayerProperties_fd
        )!!
    val vkEnumerateInstanceVersion: MethodHandle =
        APIHelper.downcallHandleOf(
            APIHelper.findSymbol("vkEnumerateInstanceVersion"),
            _vkEnumerateInstanceVersion_fd
        )!!

    fun createInstance(
        @CTypeName("VkInstanceCreateInfo*") pCreateInfo: NPointer<VkInstanceCreateInfo>,
        @CTypeName("VkAllocationCallbacks*") pAllocator: NPointer<VkAllocationCallbacks>?
    ): Result<VkInstance> {
        return MemoryStack {
            val instanceV = VkInstance.malloc()
            val result = VkResult.fromNativeData(vkCreateInstance.invokeExact(
                NPointer.toNativeData(pCreateInfo),
                NPointer.toNativeData(pAllocator),
                NPointer.toNativeData(instanceV.ptr())
            ) as Int)
            when (result) {
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
        val result = VkResult.fromNativeData(vkEnumerateInstanceExtensionProperties.invokeExact(
            NPointer.toNativeData(pLayerName),
            NPointer.toNativeData(pPropertyCount),
            NPointer.toNativeData(pProperties)
        ) as Int)
        return when (result) {
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
        val result = VkResult.fromNativeData(vkEnumerateInstanceLayerProperties.invokeExact(
            NPointer.toNativeData(pPropertyCount),
            NPointer.toNativeData(pProperties)
        ) as Int)
        return when (result) {
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
        val result = VkResult.fromNativeData(vkEnumerateInstanceVersion.invokeExact(
            NPointer.toNativeData(pApiVersion)
        ) as Int)
        return when (result) {
            VkResult.VK_SUCCESS -> Result.success(result)
            VkResult.VK_ERROR_OUT_OF_HOST_MEMORY -> Result.failure(VkException(result))
            else -> error("Unexpected result from vkEnumerateInstanceVersion: $result")
        }
    }
}