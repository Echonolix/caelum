package net.echonolix.ktffi

import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

fun main() {
    val a = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE,
        MemoryLayout.paddingLayout(3),
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_BYTE
    )
    println(a.byteSize())
}