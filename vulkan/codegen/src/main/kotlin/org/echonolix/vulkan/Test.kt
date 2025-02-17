package org.echonolix.vulkan

@JvmInline
value class Pointer<T>(val address: Long)

val Pointer<Int>.x get() = 1
val Pointer<Long>.x get() = 2L



fun main() {
val a = Pointer<Int>(1)
val b = Pointer<Long>(2)
    a.x
    b.x
}