package net.echonolix.caelum.vulkan

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.handles.VkHandle

@OptIn(UnsafeAPI::class)
fun <B : VkHandle<*>, R : B> enumerate(
    func: (count: NPointer<NUInt32>, properties: NPointer<B>?) -> Result<*>,
    fromNativeData: (pointer: NPointer<B>, index: Int) -> R,
): List<R> = MemoryStack {
    val count = NUInt32.malloc()
    func(count.ptr(), null).getOrThrow()
    val arr = reinterpret_cast<B>(NPointer.malloc<NChar>(count.value))
    val ptr = arr.ptr()
    func(count.ptr(), ptr).getOrThrow()
    List(count.value.toInt()) { fromNativeData(ptr, it) }
}