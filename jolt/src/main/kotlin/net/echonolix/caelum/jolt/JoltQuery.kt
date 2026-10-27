package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.functions.cj_world_cast_ray
import net.echonolix.caelum.jolt.structs.*

/** Borrowed query interface. Direction includes ray length; fraction is in [0, 1]. */
public class JoltQuery internal constructor(private val owner: JoltPhysicsSystem) {
    /** Uses the world's layer tables. Result storage is fully overwritten on hit or miss. */
    public fun castRay(ray: NPointer<JoltRayCast>, output: NPointer<JoltRayCastResult>): Boolean = owner.operation("cast ray") {
        checkJolt("cast ray", cj_world_cast_ray(it, ray, output)); output.hit != 0.toUByte()
    }
    public fun castRay(origin: JoltPosition, direction: JoltVector, layer: JoltObjectLayer = JoltObjectLayer.of(1)): JoltRayHit? = MemoryStack {
        val ray = JoltRayCast.malloc().apply {
            this.origin.set(origin.x, origin.y, origin.z)
            this.direction.set(direction.x, direction.y, direction.z)
            objectLayer = layer.bits; reserved = 0u
        }
        val result = JoltRayCastResult.malloc()
        if (!castRay(ray.ptr(), result.ptr())) null else JoltRayHit(
            JoltBodyId(result.bodyId), JoltSubShapeId(result.subShapeId), result.fraction,
            result.position.copyPosition(), result.normal.copyVector(),
        )
    }
}
