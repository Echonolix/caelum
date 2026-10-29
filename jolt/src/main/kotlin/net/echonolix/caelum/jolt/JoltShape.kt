package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.functions.*
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/** One owned shape reference. Bodies keep their own references independently. */
public class JoltShape internal constructor(runtime: JoltRuntime, pointer: NPointer<JoltShapeHandle>) :
    JoltResource<JoltShapeHandle>(runtime, pointer) {
    /** A separately closeable reference to the same immutable native shape. */
    public fun retain(): JoltShape = withHandle { handle ->
        runtime.createResource("retain shape", { cj_shape_retain(handle, it) }, ::cj_shape_release) { JoltShape(runtime, it) }
    }
    override fun destroy(pointer: NPointer<JoltShapeHandle>) { checkJolt("release shape", cj_shape_release(pointer)) }
}

public fun JoltRuntime.createBox(halfExtent: JoltVector, convexRadius: Float = 0.05f): JoltShape = MemoryStack {
    val extent = JoltVec3.valueOf(halfExtent.x, halfExtent.y, halfExtent.z)
    createResource("create box", { cj_shape_box(extent.ptr(), convexRadius, it) }, ::cj_shape_release) { JoltShape(this@createBox, it) }
}
public fun JoltRuntime.createSphere(radius: Float): JoltShape = createResource(
    "create sphere", { cj_shape_sphere(radius, it) }, ::cj_shape_release,
) { JoltShape(this, it) }
public fun JoltRuntime.createCapsule(halfHeight: Float, radius: Float): JoltShape = createResource(
    "create capsule", { cj_shape_capsule(halfHeight, radius, it) }, ::cj_shape_release,
) { JoltShape(this, it) }

/**
 * Creates one owned solid convex hull, copying the first [count] points during this call.
 * Storage must remain live and aligned until return. Interior and duplicate points are allowed;
 * degenerate/coplanar hulls are rejected. Jolt may lower [convexRadius] to fit the hull.
 * Points use the body's shape origin; body transform snapshots preserve that origin even when
 * Jolt computes an offset center of mass. Concave model detail is filled by the convex hull.
 */
public fun JoltRuntime.createConvexHull(
    points: NArray<JoltVec3>,
    count: Long = points.count,
    convexRadius: Float = 0.05f,
): JoltShape {
    require(count in 4..Int.MAX_VALUE.toLong() && count <= points.count) { "Convex hull point count must be in 4..INT32_MAX and fit the input array" }
    return createResource(
        "create convex hull", { cj_shape_convex_hull(points.ptr(), count.toUInt(), convexRadius, it) }, ::cj_shape_release,
    ) { JoltShape(this, it) }
}

/** Copies packed xyz triples into a solid convex hull. The input array is not retained. */
public fun JoltRuntime.createConvexHull(positions: FloatArray, convexRadius: Float = 0.05f): JoltShape {
    require(positions.size >= 12 && positions.size % 3 == 0) { "Convex hull positions must contain at least four xyz triples" }
    return Arena.ofConfined().use { arena ->
        // CJVec3 is the ABI's tightly packed three-float transport, not Jolt's SIMD Vec3.
        val storage = arena.allocate(positions.size.toLong() * Float.SIZE_BYTES, JoltVec3.layout.byteAlignment())
        storage.copyFrom(MemorySegment.ofArray(positions))
        createConvexHull(NArray<JoltVec3>(storage.address(), positions.size.toLong() / 3), convexRadius = convexRadius)
    }
}
