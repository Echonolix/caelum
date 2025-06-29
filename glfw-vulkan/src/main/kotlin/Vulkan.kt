package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.*
import net.echonolix.caelum.APIHelper.downcallHandleOf
import net.echonolix.caelum.APIHelper.functionDescriptorOf
import net.echonolix.caelum.glfw.structs.GLFWWindow
import net.echonolix.caelum.vulkan.VkException
import net.echonolix.caelum.vulkan.enums.VkResult
import net.echonolix.caelum.vulkan.functions.VkFuncPtrGetInstanceProcAddrLUNARG
import net.echonolix.caelum.vulkan.handles.VkInstance
import net.echonolix.caelum.vulkan.handles.VkInstanceHandle
import net.echonolix.caelum.vulkan.handles.VkPhysicalDeviceHandle
import net.echonolix.caelum.vulkan.handles.VkSurfaceKHR
import net.echonolix.caelum.vulkan.handles.VkSurfaceKHRHandle
import net.echonolix.caelum.vulkan.handles.value
import net.echonolix.caelum.vulkan.structs.VkAllocationCallbacks
import java.lang.foreign.FunctionDescriptor
import java.lang.invoke.MethodHandle

internal val _glfwCreateWindowSurface_fd: FunctionDescriptor = functionDescriptorOf(
    VkResult,
    VkInstanceHandle,
    NPointer,
    NPointer,
    NPointer
)

internal val _glfwGetInstanceProcAddress_fd: FunctionDescriptor = functionDescriptorOf(
    NPointer,
    NPointer,
    NPointer
)

internal val _glfwGetPhysicalDevicePresentationSupport_fd: FunctionDescriptor = functionDescriptorOf(
    NInt,
    NPointer,
    NPointer,
    NUInt32
)

internal val _glfwInitVulkanLoader_fd: FunctionDescriptor = functionDescriptorOf(
    null,
    NPointer
)


private val _glfwCreateWindowSurface_mh: MethodHandle =
    downcallHandleOf("glfwCreateWindowSurface", _glfwCreateWindowSurface_fd)!!

public fun glfwCreateWindowSurface(
    instance: VkInstance,
    window: NPointer<GLFWWindow>,
    allocator: NPointer<VkAllocationCallbacks>?,
): Result<VkSurfaceKHR> = MemoryStack {
    val handle114514 = VkSurfaceKHRHandle.malloc()
    when (val result69420 = VkResult.fromNativeData(
        _glfwCreateWindowSurface_mh.invokeExact(
            VkInstanceHandle.toNativeData(instance),
            NPointer.toNativeData(window),
            NPointer.toNativeData(allocator ?: nullptr()),
            NPointer.toNativeData(handle114514.ptr())
        ) as Int
    )) {
        VkResult.VK_SUCCESS -> Result.success(VkSurfaceKHR.fromNativeData(instance, handle114514.`value`))
        VkResult.VK_ERROR_OUT_OF_HOST_MEMORY,
        VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY -> Result.failure(VkException(result69420))
        else -> error("""Unexpected result from vkCreateDevice: $result69420""")
    }
}


private val _glfwGetInstanceProcAddress_mh: MethodHandle =
    downcallHandleOf("glfwGetInstanceProcAddress", _glfwGetInstanceProcAddress_fd)!!

public fun glfwGetInstanceProcAddress(
    @CTypeName("VkInstance") instance: VkInstanceHandle,
    @CTypeName("char*") procname: NPointer<NChar>,
): NPointer<GLFWFuncPtrVKProc> = NPointer.fromNativeData(
    _glfwGetInstanceProcAddress_mh.invokeExact(
        VkInstanceHandle.toNativeData(instance),
        NPointer.toNativeData(procname),
    ) as Long
)

private val _glfwGetPhysicalDevicePresentationSupport_mh: MethodHandle =
    downcallHandleOf("glfwGetPhysicalDevicePresentationSupport", _glfwGetPhysicalDevicePresentationSupport_fd)!!

public fun glfwGetPhysicalDevicePresentationSupport(
    @CTypeName("VkInstance") instance: VkInstanceHandle,
    @CTypeName("VkPhysicalDevice") device: VkPhysicalDeviceHandle,
    @CTypeName("uint32_t") queuefamily: UInt,
): Int = _glfwGetPhysicalDevicePresentationSupport_mh.invokeExact(
    VkInstanceHandle.toNativeData(instance),
    VkPhysicalDeviceHandle.toNativeData(device),
    NUInt32.toNativeData(queuefamily)
) as Int

private val _glfwInitVulkanLoader_mh: MethodHandle =
    downcallHandleOf("glfwInitVulkanLoader", _glfwInitVulkanLoader_fd)!!

public fun glfwInitVulkanLoader(
    @CTypeName("PFN_vkGetInstanceProcAddr") loader: NPointer<VkFuncPtrGetInstanceProcAddrLUNARG>,
): Unit = _glfwInitVulkanLoader_mh.invokeExact(
    NPointer.toNativeData(loader)
) as Unit