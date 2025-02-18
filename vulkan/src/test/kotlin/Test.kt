import org.echonolix.ktffi.calloc
import org.echonolix.ktffi.callocArr
import org.echonolix.vulkan.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

fun main() {
    val layout = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE,
        ValueLayout.JAVA_SHORT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG
    )
    println(layout.byteSize())
}