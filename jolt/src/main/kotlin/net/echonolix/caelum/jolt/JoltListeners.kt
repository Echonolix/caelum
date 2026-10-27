package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.enums.JoltContactKind
import net.echonolix.caelum.jolt.enums.JoltValidateResult
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Callbacks may run concurrently on native workers. Use snapshots and world.defer for later work. */
public interface JoltContactListener {
    public fun validate(event: JoltContactValidationView): JoltValidateResult = JoltValidateResult.CJ_ACCEPT_ALL_CONTACTS
    public fun contact(event: JoltContactEventView, settings: JoltContactSettingsView): Unit = Unit
    /** Only IDs survive removal; the bodies may already have been destroyed. */
    public fun removed(body1: JoltBodyId, subShape1: JoltSubShapeId, body2: JoltBodyId, subShape2: JoltSubShapeId): Unit = Unit
}
public fun interface JoltActivationListener {
    public fun changed(body: JoltBodyId, userData: ULong, active: Boolean)
}

/** Delivered on the caller after native workers are quiescent; prior world mutations remain applied. */
public class JoltCallbackException internal constructor(
    public val operation: String,
    public val failureCount: Int,
    cause: Throwable,
    public val updateErrors: JoltUpdateErrors?,
) : RuntimeException("$failureCount Jolt callback failure(s) during $operation", cause)

public data class JoltContactValidationSnapshot(
    public val body1: JoltTransform, public val body2: JoltTransform,
    public val baseOffset: JoltPosition, public val point1: JoltVector, public val point2: JoltVector,
    public val penetrationAxis: JoltVector, public val penetrationDepth: Float,
    public val subShape1: JoltSubShapeId, public val subShape2: JoltSubShapeId,
)
public data class JoltContactSnapshot(
    public val body1: JoltTransform, public val body2: JoltTransform,
    public val baseOffset: JoltPosition, public val normal: JoltVector, public val penetrationDepth: Float,
    public val subShape1: JoltSubShapeId, public val subShape2: JoltSubShapeId, public val kind: JoltContactKind,
    public val points1: List<JoltVector>, public val points2: List<JoltVector>,
)

/** The contact points are relative to baseOffset. No native pointers escape this view. */
public class JoltContactValidationView internal constructor(
    private val pointer: NPointer<JoltContactValidation>, private val scope: JoltBorrowScope,
) {
    private inline fun <T> read(block: NPointer<JoltContactValidation>.() -> T): T { scope.checkActive(); return pointer.block() }
    public val body1: JoltTransform get() = read { body1.copyTransform() }
    public val body2: JoltTransform get() = read { body2.copyTransform() }
    public val baseOffset: JoltPosition get() = read { baseOffset.copyPosition() }
    public val point1: JoltVector get() = read { point1.copyVector() }
    public val point2: JoltVector get() = read { point2.copyVector() }
    public val penetrationAxis: JoltVector get() = read { penetrationAxis.copyVector() }
    public val penetrationDepth: Float get() = read { penetrationDepth }
    public val subShape1: JoltSubShapeId get() = read { JoltSubShapeId(subShapeId1) }
    public val subShape2: JoltSubShapeId get() = read { JoltSubShapeId(subShapeId2) }
    public fun snapshot(): JoltContactValidationSnapshot = JoltContactValidationSnapshot(
        body1, body2, baseOffset, point1, point2, penetrationAxis, penetrationDepth, subShape1, subShape2,
    )
}

public class JoltContactEventView internal constructor(
    private val pointer: NPointer<JoltContactEvent>, private val scope: JoltBorrowScope,
) {
    private inline fun <T> read(block: NPointer<JoltContactEvent>.() -> T): T { scope.checkActive(); return pointer.block() }
    public val body1: JoltTransform get() = read { body1.copyTransform() }
    public val body2: JoltTransform get() = read { body2.copyTransform() }
    public val baseOffset: JoltPosition get() = read { baseOffset.copyPosition() }
    public val normal: JoltVector get() = read { normal.copyVector() }
    public val penetrationDepth: Float get() = read { penetrationDepth }
    public val subShape1: JoltSubShapeId get() = read { JoltSubShapeId(subShapeId1) }
    public val subShape2: JoltSubShapeId get() = read { JoltSubShapeId(subShapeId2) }
    public val kind: JoltContactKind get() = read { JoltContactKind.fromNativeData(kind) }
    public val pointCount: Int get() = read { pointCount.toInt() }
    public fun pointOnBody1(index: Int): JoltVector = read {
        require(index in 0 until pointCount.toInt()); points1[index.toLong()].copyVector()
    }
    public fun pointOnBody2(index: Int): JoltVector = read {
        require(index in 0 until pointCount.toInt()); points2[index.toLong()].copyVector()
    }
    public fun snapshot(): JoltContactSnapshot = JoltContactSnapshot(
        body1, body2, baseOffset, normal, penetrationDepth, subShape1, subShape2, kind,
        List(pointCount) { pointOnBody1(it) }, List(pointCount) { pointOnBody2(it) },
    )
}

/** Changes are committed together only when the listener returns successfully. */
public class JoltContactSettingsView internal constructor(
    private val pointer: NPointer<JoltContactSettings>, private val scope: JoltBorrowScope,
) {
    private inline fun <T> read(block: NPointer<JoltContactSettings>.() -> T): T { scope.checkActive(); return pointer.block() }
    private fun nonnegative(value: Float): Float { require(value.isFinite() && value >= 0f); return value }
    public var friction: Float
        get() = read { friction }
        set(value) { read { friction = nonnegative(value) } }
    public var restitution: Float
        get() = read { restitution }
        set(value) { read { restitution = nonnegative(value) } }
    public var inverseMassScale1: Float
        get() = read { inverseMassScale1 }
        set(value) { read { inverseMassScale1 = nonnegative(value) } }
    public var inverseMassScale2: Float
        get() = read { inverseMassScale2 }
        set(value) { read { inverseMassScale2 = nonnegative(value) } }
    public var inverseInertiaScale1: Float
        get() = read { inverseInertiaScale1 }
        set(value) { read { inverseInertiaScale1 = nonnegative(value) } }
    public var inverseInertiaScale2: Float
        get() = read { inverseInertiaScale2 }
        set(value) { read { inverseInertiaScale2 = nonnegative(value) } }
    public var isSensor: Boolean
        get() = read { isSensor != 0.toUByte() }
        set(value) { read { isSensor = if (value) 1u else 0u } }
    public var relativeLinearSurfaceVelocity: JoltVector
        get() = read { relativeLinearSurfaceVelocity.copyVector() }
        set(value) { read { requireFinite(value); relativeLinearSurfaceVelocity.set(value.x, value.y, value.z) } }
    public var relativeAngularSurfaceVelocity: JoltVector
        get() = read { relativeAngularSurfaceVelocity.copyVector() }
        set(value) { read { requireFinite(value); relativeAngularSurfaceVelocity.set(value.x, value.y, value.z) } }
    private fun requireFinite(value: JoltVector) { require(value.x.isFinite() && value.y.isFinite() && value.z.isFinite()) }
}

/** Owns shared FFM stubs; close unregisters and waits for in-flight world work before freeing them. */
public class JoltListenerRegistration internal constructor(
    private val owner: JoltPhysicsSystem,
    private val contactListener: JoltContactListener?,
    private val activationListener: JoltActivationListener?,
) : AutoCloseable {
    private val arena = Arena.ofShared()
    private val firstFailure = AtomicReference<Throwable?>()
    private val failures = AtomicInteger()
    @Volatile public var isClosed: Boolean = false
        private set
    internal val pointer: NPointer<JoltCallbacks>

    init {
        try {
            val storage = arena.allocate(JoltCallbacks.layout).fill(0)
            pointer = NPointer(storage.address())
            // MethodHandles use exact ADDRESS carriers, independent of Caelum's
            // raw pointer representation and global NFunction stub cache.
            if (contactListener != null) {
                pointer.validate = NPointer(stub("onValidation", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)).address())
                pointer.contact = NPointer(stub("onContact", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)).address())
                pointer.removed = NPointer(stub("onRemoved", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)).address())
            }
            if (activationListener != null) {
                pointer.activation = NPointer(stub("onActivation", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BYTE)).address())
            }
        } catch (failure: Throwable) { arena.close(); throw failure }
    }

    private fun stub(name: String, descriptor: FunctionDescriptor): MemorySegment {
        val type: MethodType = descriptor.toMethodType()
        val handle = MethodHandles.lookup().findVirtual(JoltListenerRegistration::class.java, name, type).bindTo(this)
        return APIHelper.LINKER.upcallStub(handle, descriptor, arena)
    }

    private inline fun <T> contained(fallback: T, block: (JoltBorrowScope) -> T): T {
        // All user code, view creation and guard setup stay within the exception boundary.
        return try {
            val scope = JoltBorrowScope()
            val previous = JoltCallContext.callback.get()
            try { JoltCallContext.callback.set(true); block(scope) }
            finally { scope.invalidate(); JoltCallContext.callback.set(previous) }
        } catch (failure: Throwable) {
            firstFailure.compareAndSet(null, failure); failures.incrementAndGet(); fallback
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onValidation(context: MemorySegment, event: MemorySegment): Int = contained(3) { scope ->
        contactListener!!.validate(JoltContactValidationView(NPointer(event.address()), scope)).value
    }
    @Suppress("UNUSED_PARAMETER")
    private fun onContact(context: MemorySegment, event: MemorySegment, settings: MemorySegment): Int = contained(1) { scope ->
        contactListener!!.contact(JoltContactEventView(NPointer(event.address()), scope), JoltContactSettingsView(NPointer(settings.address()), scope))
        0
    }
    @Suppress("UNUSED_PARAMETER")
    private fun onRemoved(context: MemorySegment, body1: Int, sub1: Int, body2: Int, sub2: Int): Unit = contained(Unit) {
        contactListener!!.removed(JoltBodyId(body1.toUInt()), JoltSubShapeId(sub1.toUInt()), JoltBodyId(body2.toUInt()), JoltSubShapeId(sub2.toUInt()))
    }
    @Suppress("UNUSED_PARAMETER")
    private fun onActivation(context: MemorySegment, body: Int, data: Long, active: Byte): Unit = contained(Unit) {
        activationListener!!.changed(JoltBodyId(body.toUInt()), data.toULong(), active != 0.toByte())
    }

    /** Called only under the world lock after all native callbacks have returned. */
    internal fun takeFailure(operation: String, flags: JoltUpdateErrors? = null): JoltCallbackException? {
        val first = firstFailure.getAndSet(null) ?: return null
        return JoltCallbackException(operation, failures.getAndSet(0), first, flags)
    }
    internal fun dispose() { if (!isClosed) { arena.close(); isClosed = true } }
    override fun close() { JoltCallContext.requireOrdinaryCall(); owner.removeRegistration(this) }
}
