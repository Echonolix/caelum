package net.echonolix.vulkan

import net.echonolix.ktffi.NativeStruct
import net.echonolix.ktffi.malloc
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout

object Foo : NativeStruct(MemoryLayout.structLayout())

fun main() {
//    @Suppress("UNCHECKED_CAST")
//    with(Arena.global()) {
//        val a = VkPipelineShaderStageCreateInfo.malloc()
//        val b = VkShaderModuleCreateInfo.malloc()
//        val foo = Foo.malloc()
//        a.pNext = b.ptr()
//        a.pNext = foo.ptr()
//    }
}