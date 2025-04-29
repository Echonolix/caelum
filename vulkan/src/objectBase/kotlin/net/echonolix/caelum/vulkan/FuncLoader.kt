package net.echonolix.caelum.vulkan

import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.NativePointer
import net.echonolix.caelum.c_str
import net.echonolix.caelum.vulkan.functions.VkFunction
import net.echonolix.caelum.vulkan.handles.VkDeviceObject
import net.echonolix.caelum.vulkan.handles.VkInstanceObject

public fun <T : VkFunction> VkInstanceObject.getInstanceFunc(
    funcDescriptor: VkFunction.TypeDescriptorImpl<T>
): T {
    val funcAddress = MemoryStack {
        vkGetInstanceProcAddr.invokeNative(
            handle,
            funcDescriptor.name.c_str()._address
        )
    }
    check(funcAddress != 0L) {
        "Vulkan function ${funcDescriptor.name} is not available on instance ${handle.toAddressHexString()}"
    }
    return funcDescriptor.fromNativeData(NativePointer(funcAddress))
}

public fun <T : VkFunction> VkDeviceObject.getDeviceFunc(
    funcDescriptor: VkFunction.TypeDescriptorImpl<T>
): T {
    val funcAddress = MemoryStack {
        vkGetDeviceProcAddr.invokeNative(
            handle,
            funcDescriptor.name.c_str()._address
        )
    }
    check(funcAddress != 0L) {
        "Vulkan function ${funcDescriptor.name} is not available on device ${handle.toAddressHexString()}"
    }
    return funcDescriptor.fromNativeData(NativePointer(funcAddress))
}