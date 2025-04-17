package net.echonolix.vulkan

import net.echonolix.ktffi.*
import net.echonolix.vulkan.handles.VkHandle

@OptIn(UnsafeAPI::class)
public fun <R : VkHandle> enumerate(
    func: (count: NativePointer<NativeUInt32>, properties: NativePointer<R>?) -> Result<*>,
    fromNativeData: (pointer: NativePointer<R>, index: Int) -> R,
): List<R> =
    MemoryStack {
        val count = NativeUInt32.malloc()
        func(count.ptr(), null).getOrThrow()
        val arr = reinterpretCast<R>(NativePointer.malloc<NativeChar>(count.value))
        val ptr = arr.ptr()
        func(count.ptr(), ptr).getOrThrow()
        val list = mutableListOf<R>()
        repeat(count.value.toInt()) {
            fromNativeData(ptr, it)
        }
        list
    }