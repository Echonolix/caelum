package net.echonolix.ktffi

import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

@OptIn(UnsafeAPI::class, ExperimentalStdlibApi::class)
fun main() {
    val a = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y"),
        ValueLayout.JAVA_INT.withName("z"),
        ValueLayout.JAVA_INT.withName("w")
    )
    val b = MemoryLayout.structLayout(
        a.withName("a"),
        ValueLayout.JAVA_INT.withName("b")
    )

    println(b.select(MemoryLayout.PathElement.groupElement("a")))
    println(b.select(MemoryLayout.PathElement.groupElement("b")))

//    MemoryStack {
//        val hexFormat = HexFormat {
//            upperCase = true
//            number {
//                prefix = "0x"
//            }
//        }
//
//        val a = NativeUInt32.malloc().ptr()
//        val b = reinterpretCast<NativeFloat>(a)
//        val c = reinterpretCast<NativeUInt16>(b)
//
//        a[0] = 0x43d25852u
//        println(a[0].toHexString(hexFormat))
//        println(b[0])
//        println(c[0].toHexString(hexFormat))
//        println(c[1].toHexString(hexFormat))
//        println()
//
//        b[0] = 0.5f
//        println(a[0].toHexString(hexFormat))
//        println(b[0])
//        println(c[0].toHexString(hexFormat))
//        println(c[1].toHexString(hexFormat))
//        println()
//
//        a[0] = 0x42e5072bu
//        println(a[0].toHexString(hexFormat))
//        println(b[0])
//        println(c[0].toHexString(hexFormat))
//        println(c[1].toHexString(hexFormat))
//        println()
//    }
}