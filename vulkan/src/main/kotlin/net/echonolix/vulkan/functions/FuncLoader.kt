package net.echonolix.vulkan.functions

import net.echonolix.ktffi.MemoryStack
import net.echonolix.ktffi.NativePointer
import net.echonolix.ktffi.c_str
import net.echonolix.vulkan.handles.VkDevice
import net.echonolix.vulkan.handles.VkInstance
import net.echonolix.vulkan.toAddressHexString

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