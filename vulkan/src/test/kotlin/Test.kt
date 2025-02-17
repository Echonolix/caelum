import org.echonolix.ktffi.calloc
import org.echonolix.ktffi.callocArr
import org.echonolix.vulkan.structs.*
import java.lang.foreign.Arena

fun main() {
    with(Arena.ofAuto()) {
        val arr = VkAabbPositionsKHR.callocArr(0)
        val b = VkAabbPositionsKHR.calloc()
        arr[0] = b
    }
}