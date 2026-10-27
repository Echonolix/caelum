package acceptance

import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*
import kotlin.math.abs

/** Depends only on published Caelum artifacts, never the repository source tree. */
public object JoltConsumer {
    @JvmStatic public fun main(args: Array<String>) {
        val resources = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime -> runtime.createWorld().use { world ->
            runtime.createBox(JoltVector(10f, 0.5f, 10f)).use { shape -> MemoryStack {
                world.bodies.createAndAdd(JoltBodyCreateInfo.allocate {
                    position.set(0.0, -0.5, 0.0); motion = JoltMotionType.CJ_STATIC; layer = JoltObjectLayer.of(0)
                }, shape, false)
            } }
            val ball = runtime.createSphere(0.5f).use { shape -> MemoryStack {
                world.bodies.createAndAdd(JoltBodyCreateInfo.allocate {
                    position.set(0.0, 2.0, 0.0); motion = JoltMotionType.CJ_DYNAMIC; layer = JoltObjectLayer.of(1)
                }, shape)
            } }
            repeat(300) { world.update(1f / 60f).throwIfFailed() }
            check(abs(world.bodies.transform(ball).position.y - 0.5) < 0.03)
        } }
        check(JoltRuntime.liveNativeResources() == resources)
        println("CAELUM_JOLT_MAVEN_OK ${JoltRuntime.JOLT_COMMIT}")
    }
}
