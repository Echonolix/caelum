package net.echonolix.caelum.jolt

import net.echonolix.caelum.NPointer
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*

/** Full index/sequence bits. Valid only in its source world, not a persistent entity ID. */
@JvmInline
public value class JoltBodyId(public val bits: UInt) {
    public val isValid: Boolean get() = bits != UInt.MAX_VALUE && bits and 0x80000000u == 0u
    public companion object { public val INVALID: JoltBodyId = JoltBodyId(UInt.MAX_VALUE) }
}
@JvmInline public value class JoltSubShapeId(public val bits: UInt)
@JvmInline public value class JoltObjectLayer(public val bits: UShort) {
    public companion object {
        public fun of(index: Int): JoltObjectLayer {
            require(index in 0..65535)
            return JoltObjectLayer(index.toUShort())
        }
    }
}

/** Immutable convenience copies. Hot paths can use the generated struct/out-buffer APIs. */
public data class JoltVector(public val x: Float, public val y: Float, public val z: Float)
public data class JoltPosition(public val x: Double, public val y: Double, public val z: Double)
public data class JoltRotation(public val x: Float = 0f, public val y: Float = 0f, public val z: Float = 0f, public val w: Float = 1f)
public data class JoltTransform(
    public val bodyId: JoltBodyId,
    public val position: JoltPosition,
    public val rotation: JoltRotation,
    public val linearVelocity: JoltVector,
    public val angularVelocity: JoltVector,
    public val userData: ULong,
    public val motionType: JoltMotionType,
    public val objectLayer: JoltObjectLayer,
    public val active: Boolean,
    public val sensor: Boolean,
)
public data class JoltRayHit(
    public val bodyId: JoltBodyId,
    public val subShapeId: JoltSubShapeId,
    public val fraction: Float,
    public val position: JoltPosition,
    public val normal: JoltVector,
)

/** Process-wide cumulative Jolt allocator requests; not live bytes or process RSS. */
public data class JoltAllocationStats(public val calls: ULong, public val bytesRequested: ULong)

/** Preserves EPhysicsUpdateError bits, including unknown bits from a future ABI. */
@JvmInline
public value class JoltUpdateErrors(public val bits: UInt) {
    public val isSuccess: Boolean get() = bits == 0u
    public val manifoldCacheFull: Boolean get() = bits and 1u != 0u
    public val bodyPairCacheFull: Boolean get() = bits and 2u != 0u
    public val contactConstraintsFull: Boolean get() = bits and 4u != 0u
    public fun throwIfFailed() { check(isSuccess) { "Jolt update exhausted capacity (error flags: $bits)" } }
}

internal fun NPointer<JoltVec3>.copyVector(): JoltVector = JoltVector(x, y, z)
internal fun NPointer<JoltRVec3>.copyPosition(): JoltPosition = JoltPosition(x, y, z)
internal fun NPointer<JoltQuat>.copyRotation(): JoltRotation = JoltRotation(x, y, z, w)
internal fun NPointer<JoltBodyTransform>.copyTransform(): JoltTransform = JoltTransform(
    JoltBodyId(bodyId), position.copyPosition(), rotation.copyRotation(), linearVelocity.copyVector(), angularVelocity.copyVector(),
    userData, JoltMotionType.fromNativeData(motionType), JoltObjectLayer(objectLayer), isActive != 0.toUByte(), isSensor != 0.toUByte(),
)
