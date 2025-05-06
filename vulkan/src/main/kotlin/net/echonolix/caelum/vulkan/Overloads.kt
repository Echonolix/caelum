package net.echonolix.caelum.vulkan

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.handles.VkHandle

@OptIn(UnsafeAPI::class)
fun <B : VkHandle, R : B> enumerate(
    func: (count: NativePointer<NativeUInt32>, properties: NativePointer<B>?) -> Result<*>,
    fromNativeData: (pointer: NativePointer<B>, index: Int) -> R,
): List<R> =
    MemoryStack {
        val count = NativeUInt32.malloc()
        func(count.ptr(), null).getOrThrow()
        val arr = reinterpretCast<B>(NativePointer.malloc<NativeChar>(count.value))
        val ptr = arr.ptr()
        func(count.ptr(), ptr).getOrThrow()
        List(count.value.toInt()) { fromNativeData(ptr, it) }
    }