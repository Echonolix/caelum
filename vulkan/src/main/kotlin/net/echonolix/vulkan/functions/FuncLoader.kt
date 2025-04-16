package net.echonolix.vulkan.functions

import net.echonolix.ktffi.MemoryStack
import net.echonolix.ktffi.NativePointer
import net.echonolix.ktffi.c_str
import net.echonolix.vulkan.handles.VkDevice
import net.echonolix.vulkan.handles.VkInstance

public fun <T : VkFunction> VkInstance.getInstanceFunc(
    funcDescriptor: VkFunction.TypeDescriptorImpl<T>
): T = MemoryStack {
    funcDescriptor.fromNativeData(
        NativePointer(
            this@getInstanceFunc.vkGetInstanceProcAddr.invokeNative(
                this@getInstanceFunc.handle,
                funcDescriptor.name.c_str()._address
            )
        )
    )
}

public fun <T : VkFunction> VkDevice.getDeviceFunc(
    funcDescriptor: VkFunction.TypeDescriptorImpl<T>
): T = MemoryStack {
    funcDescriptor.fromNativeData(
        NativePointer(
            this@getDeviceFunc.vkGetDeviceProcAddr.invokeNative(
                this@getDeviceFunc.handle,
                funcDescriptor.name.c_str()._address
            )
        )
    )
}