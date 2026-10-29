package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.enums.JoltStatus
import net.echonolix.caelum.jolt.functions.cj_shape_convex_hull
import net.echonolix.caelum.jolt.structs.*
import org.junit.jupiter.api.Tag
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.math.abs
import kotlin.test.*

@Tag("native")
class JoltNativeConvexHullTest {
    @Test fun tetrahedronCopiesPackedInputAndRayUsesItsSlopingFace() {
        val before = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime -> runtime.createWorld().use { world ->
            val positions = floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 2f, 0.2f, 0.2f, 0.2f, 2f, 0f, 0f)
            val body = runtime.createConvexHull(positions, convexRadius = 0f).use { shape ->
                positions.fill(1000f)
                MemoryStack {
                    val info = JoltBodyCreateInfo.allocate { position.set(0.0, 10.0, 0.0); motion = JoltMotionType.CJ_STATIC }
                    world.bodies.createAndAdd(info, shape, false)
                }
            }
            assertEquals(JoltPosition(0.0, 10.0, 0.0), world.bodies.transform(body).position)
            val hit = assertNotNull(world.query.castRay(JoltPosition(0.25, 14.0, 0.25), JoltVector(0f, -8f, 0f)))
            assertEquals(body, hit.bodyId)
            // Analytic tetrahedron plane x + (y - 10) + z = 2, not its bounding box's y = 12.
            assertTrue(abs(hit.position.y - 11.5) < 0.002, "hit ${hit.position}")
            assertTrue(abs(hit.normal.y - 0.57735026f) < 0.002f, "normal ${hit.normal}")
            assertNull(world.query.castRay(JoltPosition(1.5, 14.0, 1.5), JoltVector(0f, -8f, 0f)))
        } }
        assertEquals(before, JoltRuntime.liveNativeResources())
    }

    @Test fun offsetHullCopiesNativePointsRetainsReferencesAndSettlesAtItsShapeOrigin() {
        val before = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime ->
            runtime.createWorld().use { world ->
                runtime.createBox(JoltVector(20f, 0.5f, 20f)).use { floor -> MemoryStack {
                    val info = JoltBodyCreateInfo.allocate { position.set(0.0, -0.5, 0.0); motion = JoltMotionType.CJ_STATIC }
                    world.bodies.createAndAdd(info, floor, false)
                } }
                val body = MemoryStack {
                    val points = JoltVec3.malloc(9)
                    for (index in 0L..7L) points[index].set(
                        if (index and 1L == 0L) 3f else 5f,
                        if (index and 2L == 0L) 2f else 4f,
                        if (index and 4L == 0L) -1f else 1f,
                    )
                    points[8L].set(Float.NaN, Float.NaN, Float.NaN)
                    runtime.createConvexHull(points, count = 8, convexRadius = 0.025f).use { shape ->
                        for (index in 0L..7L) points[index].set(1000f, 1000f, 1000f)
                        shape.retain().use { retained ->
                            val info = JoltBodyCreateInfo.allocate { position.set(-4.0, 4.0, 0.0); layer = JoltObjectLayer.of(1) }
                            world.bodies.createAndAdd(info, retained)
                        }
                    }
                }
                assertEquals(JoltPosition(-4.0, 4.0, 0.0), world.bodies.transform(body).position)
                val hit = assertNotNull(world.query.castRay(JoltPosition(0.0, 12.0, 0.0), JoltVector(0f, -20f, 0f)))
                assertEquals(body, hit.bodyId)
                assertTrue(abs(hit.position.y - 8.0) < 0.003)
                var contacts = 0
                world.setListeners(object : JoltContactListener {
                    override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) { contacts++ }
                })
                repeat(360) { world.update(1f / 60f).throwIfFailed() }
                val end = world.bodies.transform(body)
                // Local bottom is y=2, so resting shape origin is y=-2; COM would be y=1.
                assertTrue(abs(end.position.y + 2.0) < 0.04, "shape origin ${end.position}")
                assertTrue(abs(end.position.x + 4.0) < 0.04, "shape origin ${end.position}")
                assertTrue(abs(end.linearVelocity.y) < 0.1f)
                assertTrue(contacts > 0)
            }
            assertEquals(0, runtime.ownedResourceCount)
        }
        assertEquals(before, JoltRuntime.liveNativeResources())
    }

    @Test fun invalidHullInputsClearRawOutputsAndDoNotLeakOwners() {
        val before = JoltRuntime.liveNativeResources()
        JoltRuntime.acquire().use { runtime -> MemoryStack {
            val points = JoltVec3.malloc(4)
            points[0L].set(0f, 0f, 0f); points[1L].set(2f, 0f, 0f)
            points[2L].set(0f, 2f, 0f); points[3L].set(0f, 0f, 2f)
            assertFailsWith<IllegalArgumentException> { runtime.createConvexHull(points, count = 3) }
            assertFailsWith<IllegalArgumentException> { runtime.createConvexHull(points, count = 5) }
            assertFailsWith<IllegalArgumentException> { runtime.createConvexHull(NArray<JoltVec3>(points._address, UInt.MAX_VALUE.toLong())) }
            assertFailsWith<IllegalArgumentException> { runtime.createConvexHull(FloatArray(13)) }
            assertFailsWith<IllegalArgumentException> { runtime.createConvexHull(FloatArray(9)) }
            Arena.ofConfined().use { arena ->
                val output = arena.allocate(ValueLayout.ADDRESS)
                val pointer = NPointer<NPointer<JoltShapeHandle>>(output.address())
                fun rejected(input: NPointer<JoltVec3>, count: UInt, radius: Float, status: JoltStatus) {
                    output.set(ValueLayout.ADDRESS, 0, MemorySegment.ofAddress(1))
                    assertEquals(status.value, cj_shape_convex_hull(input, count, radius, pointer))
                    assertEquals(0L, output.get(ValueLayout.ADDRESS, 0).address())
                    assertEquals(0, runtime.ownedResourceCount)
                    assertEquals(before, JoltRuntime.liveNativeResources())
                }
                rejected(NPointer(0), 4u, 0f, JoltStatus.CJ_INVALID_ARGUMENT)
                rejected(points.ptr(), 3u, 0f, JoltStatus.CJ_INVALID_ARGUMENT)
                rejected(points.ptr(), UInt.MAX_VALUE, 0f, JoltStatus.CJ_INVALID_ARGUMENT)
                rejected(points.ptr(), 4u, -1f, JoltStatus.CJ_INVALID_ARGUMENT)
                rejected(points.ptr(), 4u, Float.NaN, JoltStatus.CJ_INVALID_ARGUMENT)
                rejected(points.ptr(), 4u, Float.POSITIVE_INFINITY, JoltStatus.CJ_INVALID_ARGUMENT)
                assertEquals(JoltStatus.CJ_INVALID_ARGUMENT.value, cj_shape_convex_hull(points.ptr(), 4u, 0f, NPointer(0)))
                points[3L].set(0f, 0f, Float.POSITIVE_INFINITY)
                rejected(points.ptr(), 4u, 0f, JoltStatus.CJ_INVALID_ARGUMENT)
                points[3L].set(0f, 0f, Float.NaN)
                rejected(points.ptr(), 4u, 0f, JoltStatus.CJ_INVALID_ARGUMENT)
                points[3L].set(2f, 2f, 0f)
                rejected(points.ptr(), 4u, 0f, JoltStatus.CJ_SHAPE_ERROR)
                points[0L].set(0f, 0f, 1f); points[1L].set(1f, 0f, 0f)
                points[2L].set(0f, 1f, 0f); points[3L].set(1f, 1f, -1f)
                rejected(points.ptr(), 4u, 0f, JoltStatus.CJ_SHAPE_ERROR)
                for (index in 0L..3L) points[index].set(index.toFloat(), 0f, 0f)
                rejected(points.ptr(), 4u, 0f, JoltStatus.CJ_SHAPE_ERROR)
                for (index in 0L..3L) points[index].set(1f, 1f, 1f)
                rejected(points.ptr(), 4u, 0f, JoltStatus.CJ_SHAPE_ERROR)
                assertFailsWith<JoltException> { runtime.createConvexHull(FloatArray(12)) }
            }
        } }
        assertEquals(before, JoltRuntime.liveNativeResources())
    }
}
