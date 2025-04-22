package net.echonolix.caelum

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

    MemoryStack {
        val hexFormat = HexFormat {
            upperCase = true
            number {
                prefix = "0x"
            }
        }

        val a = NativeUInt32.malloc().ptr()
        val b = reinterpretCast<NativeFloat>(a)
        val c = reinterpretCast<NativeUInt16>(b)

        a[0] = 0x43d25852u
        println(a[0].toHexString(hexFormat))
        println(b[0])
        println(c[0].toHexString(hexFormat))
        println(c[1].toHexString(hexFormat))
        println()

        b[0] = 0.5f
        println(a[0].toHexString(hexFormat))
        println(b[0])
        println(c[0].toHexString(hexFormat))
        println(c[1].toHexString(hexFormat))
        println()

        a[0] = 0x42e5072bu
        println(a[0].toHexString(hexFormat))
        println(b[0])
        println(c[0].toHexString(hexFormat))
        println(c[1].toHexString(hexFormat))
        println()
    }

    MemoryStack.checkEmpty()

    MemoryStack {
        val f1 = this
        val a1 = NativeUInt32.malloc(1024)
        var add1 = a1._segment.address()
        var add2 = -1L
        var add3 = -1L
        println(add1)
        println(f1)
        MemoryStack {
            val f2 = this
            val a2 = NativeUInt32.malloc(1024)
            add2 = a2._segment.address()

            println(add2)
            println(f2)
        }
        MemoryStack {
            val f3 = this
            val a3 = NativeUInt32.malloc(1024)
            add3 = a3._segment.address()
            println(add3)
            println(f3)
        }

        val a4 = NativeUInt32.malloc(1024)
        var add4 = a4._segment.address()
        println(add4)
    }
}