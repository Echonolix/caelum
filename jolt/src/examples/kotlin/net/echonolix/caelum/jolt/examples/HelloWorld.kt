package net.echonolix.caelum.jolt.examples

import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*
import kotlin.math.abs

/** Original Caelum example following the floor/ball scenario in Jolt and jolt-jni. */
public object HelloWorld {
    @JvmStatic public fun main(args: Array<String>) {
        val workers = args.firstOrNull()?.toInt() ?: 0
        val resourcesBefore = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime -> runtime.createWorld(workers = workers).use { world ->
            runtime.createBox(JoltVector(20f, 0.5f, 20f)).use { floor -> MemoryStack {
                val info = JoltBodyCreateInfo.allocate {
                    position.set(0.0, -0.5, 0.0)
                    motion = JoltMotionType.CJ_STATIC; layer = JoltObjectLayer.of(0)
                }
                world.bodies.createAndAdd(info, floor, activate = false)
            } }
            val ball = runtime.createSphere(0.5f).use { sphere -> MemoryStack {
                val info = JoltBodyCreateInfo.allocate {
                    position.set(0.0, 2.0, 0.0)
                    motion = JoltMotionType.CJ_DYNAMIC; layer = JoltObjectLayer.of(1)
                }
                world.bodies.createAndAdd(info, sphere)
            } }
            // Body-held shape references outlive the application shape wrappers.
            world.optimizeBroadPhase()
            println("frame,y,velocityY,active")
            repeat(300) { frame ->
                world.update(1f / 60f).throwIfFailed()
                if ((frame + 1) % 60 == 0) {
                    val state = world.bodies.transform(ball)
                    println("${frame + 1},${state.position.y},${state.linearVelocity.y},${state.active}")
                }
            }
            val final = world.bodies.transform(ball)
            check(abs(final.position.y - 0.5) < 0.03 && abs(final.linearVelocity.y) < 0.1f) { "Ball did not settle: $final" }
            val hit = checkNotNull(world.query.castRay(JoltPosition(0.0, 3.0, 0.0), JoltVector(0f, -6f, 0f)))
            check(hit.bodyId == ball)
        } }
        check(JoltRuntime.liveNativeResources() == resourcesBefore) { "Owned native resource leak" }
        println("CAELUM_JOLT_HELLO_OK workers=$workers commit=${JoltRuntime.JOLT_COMMIT}")
    }
}
