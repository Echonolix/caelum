package net.echonolix.caelum

import java.lang.invoke.MethodHandles
import kotlin.reflect.jvm.javaMethod

fun foo(a: Int, b: Float): Double {
    return a.toDouble() * b.toDouble()
}

val mhg = MethodHandles.lookup().unreflect(::foo.javaMethod)

fun bar(a: Int, b: Float): Double {
    return mhg.invokeExact(a, b) as Double
}

inline fun <A, B, C> baz(a: A, b: B): C {
    return mhg.invoke(a, b) as C
}

@OptIn(UnsafeAPI::class, ExperimentalStdlibApi::class)
fun main() {
    val mh = MethodHandles.lookup().unreflect(::foo.javaMethod)
    val a = 111
    val b = 3.0f
    val c1 = mh.invokeExact(a, b) as Double
    println(c1)
    val c2 = bar(a, b)
    println(c2)
    val c3 = baz<Int, Float, Double>(a, b)
    println(c3)
}