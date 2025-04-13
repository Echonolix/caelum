package net.echonolix.vulkan

import net.echonolix.ktffi.NativeStruct
import net.echonolix.ktffi.malloc
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout

object A : NativeStruct<A>(
    ValueLayout.JAVA_BYTE,
    ValueLayout.JAVA_BYTE,
    ValueLayout.JAVA_BYTE,
    ValueLayout.JAVA_LONG,
)

object B : NativeStruct<B>(
    ValueLayout.JAVA_BYTE.withName("a"), A.layout, ValueLayout.JAVA_LONG
)

fun main() {
    @Suppress("UNCHECKED_CAST") with(Arena.ofAuto()) {
        println(A.layout.byteSize())
        println(A.layout.byteAlignment())
        val a = B.malloc()
        println(B.layout.byteSize())
        println(B.layout.byteAlignment())
    }
}