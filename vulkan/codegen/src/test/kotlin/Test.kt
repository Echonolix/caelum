import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

fun main() {
    val xdd = MemoryLayout.sequenceLayout(
        1114,
        MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_DOUBLE
        )
    )
    val a = xdd.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
    println(a.invokeExact(1L, 5L) as Long)
}