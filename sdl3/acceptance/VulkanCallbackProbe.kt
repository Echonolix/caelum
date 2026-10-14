import net.echonolix.caelum.vulkan.functions.VkFuncPtrVoidFunction
import net.echonolix.caelum.vulkan.functions.VkFunction

// Compile with the production VkFunction and the freshly generated callback.
// No Vulkan loader, window, instance or device is used.
fun main() {
    var calls = 0
    val callback = VkFuncPtrVoidFunction { calls++ }
    try {
        val first = VkFuncPtrVoidFunction.toPointer(callback)
        check(first._address == VkFuncPtrVoidFunction.toPointer(callback)._address)
        VkFuncPtrVoidFunction.fromNativeData(first._address)()
        check(calls == 1)
        val oldLifetime = VkFunction.stubAllocator
        VkFunction.freeFunctionStubs()
        check(!oldLifetime.scope().isAlive)
        VkFuncPtrVoidFunction.fromNativeData(VkFuncPtrVoidFunction.toPointer(callback)._address)()
        check(calls == 2)
        println("VULKAN_CALLBACK_CPU_OK")
    } finally {
        VkFunction.freeFunctionStubs()
    }
}
