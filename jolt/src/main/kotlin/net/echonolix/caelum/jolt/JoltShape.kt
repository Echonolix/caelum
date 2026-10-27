package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.functions.*
import net.echonolix.caelum.jolt.structs.*

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
