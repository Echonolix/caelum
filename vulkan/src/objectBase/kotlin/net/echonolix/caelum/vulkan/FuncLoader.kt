package net.echonolix.caelum.vulkan

import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.NativePointer
import net.echonolix.caelum.c_str
import net.echonolix.caelum.vulkan.functions.VkFunction
import net.echonolix.caelum.vulkan.handles.VkDevice
import net.echonolix.caelum.vulkan.handles.VkInstance

public fun <T : VkFunction> VkInstance.getInstanceFunc(
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

public fun <T : VkFunction> VkDevice.getDeviceFunc(
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