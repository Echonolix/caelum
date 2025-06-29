package net.echonolix.caelum.vulkan

import net.echonolix.caelum.APIHelper
import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.c_str
import net.echonolix.caelum.vulkan.functions.VkFunction
import net.echonolix.caelum.vulkan.handles.VkDevice
import net.echonolix.caelum.vulkan.handles.VkDeviceFuncContainer
import net.echonolix.caelum.vulkan.handles.VkInstance
import net.echonolix.caelum.vulkan.handles.VkInstanceFuncContainer
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

internal fun VkInstanceFuncContainer.getInstanceFunc(
    name: String,
    funcDescriptor: FunctionDescriptor
): MethodHandle {
    val funcAddress = MemoryStack {
        vkGetInstanceProcAddr.invokeExact(
            handle,
            name.c_str()._address
        ) as Long
    }
    check(funcAddress != 0L) {
        "Vulkan function $name is not available on instance ${handle.toAddressHexString()}"
    }
    return APIHelper.downcallHandleOf(MemorySegment.ofAddress(funcAddress), funcDescriptor)!!
}

internal fun VkDeviceFuncContainer.getDeviceFunc(
    name: String,
    funcDescriptor: FunctionDescriptor
): MethodHandle {
    val funcAddress = MemoryStack {
        vkGetDeviceProcAddr.invokeExact(
            handle,
            name.c_str()._address
        ) as Long
    }
    check(funcAddress != 0L) {
        "Vulkan function $name is not available on device ${handle.toAddressHexString()}"
    }
    return APIHelper.downcallHandleOf(MemorySegment.ofAddress(funcAddress), funcDescriptor)!!
}

fun VkInstance.getInstanceFunc(name: String, funcDescriptor: FunctionDescriptor): MethodHandle =
    funcContainer.getInstanceFunc(name, funcDescriptor)

fun VkDevice.getDeviceFunc(name: String, funcDescriptor: FunctionDescriptor): MethodHandle =
    funcContainer.getDeviceFunc(name, funcDescriptor)
