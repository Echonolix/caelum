package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*
import org.junit.jupiter.api.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.test.*

@Tag("native")
class JoltNativeWorldTest {
    private fun floor(runtime: JoltRuntime, world: JoltPhysicsSystem) {
        runtime.createBox(JoltVector(50f, 0.5f, 50f)).use { shape -> MemoryStack {
            val info = JoltBodyCreateInfo.allocate {
                position.set(0.0, -0.5, 0.0); motion = JoltMotionType.CJ_STATIC; layer = JoltObjectLayer.of(0)
            }
            world.bodies.createAndAdd(info, shape, false)
        } }
    }
    private fun ball(world: JoltPhysicsSystem, shape: JoltShape, y: Double = 2.0, x: Double = 0.0, z: Double = 0.0): JoltBodyId = MemoryStack {
        val info = JoltBodyCreateInfo.allocate {
            position.set(x, y, z); motion = JoltMotionType.CJ_DYNAMIC; layer = JoltObjectLayer.of(1)
        }
        world.bodies.createAndAdd(info, shape)
    }

    @Test fun fallingBallRetainsShapeAndSettlesOnFloor() {
        val before = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime ->
            runtime.createWorld().use { world ->
                floor(runtime, world)
                val body = runtime.createSphere(0.5f).use { shape ->
                    shape.retain().use { retained -> ball(world, retained) }
                }
                assertFailsWith<IllegalStateException> { runtime.close() }
                repeat(30) { world.update(1f / 60f).throwIfFailed() }
                assertTrue(world.bodies.transform(body).position.y < 1.5)
                repeat(270) { world.update(1f / 60f).throwIfFailed() }
                val end = world.bodies.transform(body)
                assertTrue(abs(end.position.y - 0.5) < 0.03, "height ${end.position.y}")
                assertTrue(abs(end.linearVelocity.y) < 0.1f)
            }
            assertEquals(0, runtime.ownedResourceCount)
        }
        assertEquals(before, JoltRuntime.liveNativeResources())
    }

    @Test fun bodyLifecycleLocksBatchAndClosedViews() {
        JoltRuntime.acquire().use { runtime ->
            val world = runtime.createWorld()
            world.use {
            val borrowed = world.bodies
            runtime.createCapsule(0.5f, 0.25f).use { shape ->
                val first = ball(world, shape)
                var escaped: JoltBodyReadView? = null
                world.bodies.withBodyWrite(first) { view ->
                    escaped = view; view.setVelocity(JoltVector(1f, 2f, 3f))
                    assertEquals(first, view.snapshot().bodyId)
                    assertFailsWith<IllegalStateException> { world.gravity }
                    assertFailsWith<IllegalStateException> { world.close() }
                }
                assertFailsWith<IllegalStateException> { escaped!!.snapshot() }
                val second = ball(world, shape, 4.0)
                MemoryStack {
                    val ids = NUInt32.malloc(2); ids[0L] = first.bits; ids[1L] = second.bits
                    val transforms = JoltBodyTransform.allocate(2)
                    world.bodies.readTransforms(ids, transforms)
                    assertEquals(world.bodies.transform(first), transforms[0L].copyTransform())
                    assertEquals(world.bodies.transform(second), transforms[1L].copyTransform())
                    assertFailsWith<IllegalArgumentException> { world.bodies.readTransforms(ids, NArray(transforms._address, 1)) }
                }
                assertFailsWith<JoltException> { world.bodies.destroy(first) }
                world.bodies.remove(first); world.bodies.destroy(first)
                assertFailsWith<JoltException> { world.bodies.transform(first) }
                val next = ball(world, shape)
                assertNotEquals(first, next)
                repeat(30) {
                    val id = ball(world, shape, 10.0)
                    world.bodies.remove(id); world.bodies.add(id, false)
                    world.bodies.remove(id); world.bodies.destroy(id)
                }
            }
            world.close(); world.close()
            assertFailsWith<IllegalStateException> { borrowed.transform(JoltBodyId(0u)) }
            }
        }
    }

    @Test fun closestRayUsesLayerTablesAndOverwritesMiss() {
        JoltRuntime.acquire().use { runtime -> runtime.createWorld().use { world ->
            floor(runtime, world)
            val start = JoltPosition(0.0, 3.0, 0.0); val direction = JoltVector(0f, -6f, 0f)
            val hit = assertNotNull(world.query.castRay(start, direction, JoltObjectLayer.of(1)))
            assertTrue(abs(hit.fraction - 0.5f) < 0.001f); assertTrue(hit.normal.y > 0.99f)
            assertNull(world.query.castRay(start, direction, JoltObjectLayer.of(0)))
            MemoryStack {
                val ray = JoltRayCast.malloc().apply { origin.set(0.0, 3.0, 0.0); this.direction.set(0f, -6f, 0f); objectLayer = 1u; reserved = 0u }
                val output = JoltRayCastResult.malloc()
                assertTrue(world.query.castRay(ray.ptr(), output.ptr()))
                ray.direction.set(0f, 6f, 0f)
                assertFalse(world.query.castRay(ray.ptr(), output.ptr()))
                assertEquals(UInt.MAX_VALUE, output.bodyId); assertEquals(1f, output.fraction)
                assertEquals(0.0, output.position.x)
            }
        } }
    }

    @Test fun callbackFailuresReturnSafelyAndDeferredCommandsUseCopies() {
        JoltRuntime.acquire().use { runtime -> runtime.createWorld().use { world ->
            floor(runtime, world)
            val id = runtime.createSphere(0.5f).use { ball(world, it, 0.45) }
            var escaped: JoltContactSettingsView? = null
            var escapedEvent: JoltContactEventView? = null
            world.setListeners(object : JoltContactListener {
                override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) {
                    escaped = settings; escapedEvent = event
                    assertFailsWith<IllegalStateException> { world.update(1f / 60f) }
                    assertFailsWith<IllegalStateException> { world.close() }
                    assertFailsWith<IllegalStateException> { world.clearListeners() }
                    val copy = event.snapshot()
                    world.defer { bodies.setUserData(id, copy.body1.userData + 42uL) }
                    settings.friction = 9f
                    error("listener failure")
                }
            })
            val failure = assertFailsWith<JoltCallbackException> { world.update(1f / 60f) }
            assertEquals("listener failure", failure.cause!!.message)
            assertNotNull(failure.updateErrors); assertTrue(failure.failureCount >= 1)
            assertFailsWith<IllegalStateException> { escaped!!.friction }
            assertFailsWith<IllegalStateException> { escapedEvent!!.snapshot() }
            world.clearListeners()
            assertTrue(world.flushDeferred() >= 1)
            assertEquals(42uL, world.bodies.transform(id).userData)
            val count = AtomicInteger()
            val first = world.setListeners(activation = JoltActivationListener { body, data, active ->
                assertEquals(id, body); assertEquals(42uL, data); assertFalse(active); count.incrementAndGet()
            })
            world.bodies.setActive(id, false)
            assertEquals(1, count.get())
            val second = world.setListeners(activation = JoltActivationListener { _, _, _ -> })
            assertTrue(first.isClosed); first.close(); second.close(); second.close()
            world.bodies.setActive(id, true)
            assertEquals(1, count.get())
        } }
    }

    @Test @Tag("stress") fun workerCallbacksUnregisterAndCloseAfterConcurrentUpdate() {
        val before = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime ->
            val world = runtime.createWorld(workers = 4)
            world.use {
            floor(runtime, world)
            runtime.createSphere(0.5f).use { shape ->
                repeat(512) { ball(world, shape, 0.45, (it % 32 - 16) * 1.25, (it / 32 - 8) * 1.25) }
            }
            val threads = ConcurrentHashMap.newKeySet<Long>()
            val caller = Thread.currentThread().threadId()
            repeat(10) {
                world.setListeners(object : JoltContactListener {
                    override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) { threads.add(Thread.currentThread().threadId()) }
                }).use { world.update(1f / 60f).throwIfFailed() }
            }
            assertTrue(threads.any { it != caller }, "Expected at least one actual native worker upcall")
            val entered = CountDownLatch(1)
            world.setListeners(object : JoltContactListener {
                override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) { entered.countDown() }
            })
            Executors.newSingleThreadExecutor().use { executor ->
                val update = executor.submit { repeat(60) { if (!world.isClosed) runCatching { world.update(1f / 60f) }.getOrElse { failure -> if (!world.isClosed) throw failure } } }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                world.close()
                update.get(20, TimeUnit.SECONDS)
            }
            assertEquals(0, runtime.ownedResourceCount)
            }
        }
        assertEquals(before, JoltRuntime.liveNativeResources())
    }

    @Test fun invalidCreationRollsBackAndKinematicBodiesMove() {
        JoltRuntime.acquire().use { runtime ->
            val resources = runtime.ownedResourceCount
            val failure = assertFailsWith<JoltException> { runtime.createSphere(-1f) }
            assertEquals(1, failure.code)
            assertEquals(resources, runtime.ownedResourceCount)
            assertFailsWith<JoltException> { runtime.createWorld { maxBodies = 0u } }
            assertEquals(resources, runtime.ownedResourceCount)
            val allocation = JoltRuntime.allocationStats()
            runtime.createWorld().use { world -> runtime.createBox(JoltVector(0.5f, 0.5f, 0.5f)).use { shape ->
                val body = MemoryStack {
                    val settings = JoltBodyCreateInfo.allocate { motion = JoltMotionType.CJ_KINEMATIC; layer = JoltObjectLayer.of(1) }
                    world.bodies.createAndAdd(settings, shape)
                }
                world.bodies.moveKinematic(body, JoltPosition(1.0, 0.0, 0.0), JoltRotation(), 1f / 60f)
                world.update(1f / 60f).throwIfFailed()
                assertTrue(abs(world.bodies.transform(body).position.x - 1.0) < 0.001)
                assertFailsWith<JoltException> { world.bodies.addForce(body, JoltVector(1f, 0f, 0f)) }
            } }
            assertTrue(JoltRuntime.allocationStats().calls > allocation.calls)
            assertEquals(resources, runtime.ownedResourceCount)
        }
    }
}
