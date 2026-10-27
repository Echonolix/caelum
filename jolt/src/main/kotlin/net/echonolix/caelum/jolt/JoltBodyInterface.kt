package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.functions.*
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** Borrowed world interface. Body IDs are valid only in this world. */
public class JoltBodyInterface internal constructor(private val owner: JoltPhysicsSystem) {
    /** Settings and shape are borrowed for this call; the body retains the shape. */
    public fun create(info: NPointer<JoltBodyCreateInfo>, shape: JoltShape, add: Boolean = false, activate: Boolean = true): JoltBodyId =
        owner.operation("create body") { world -> shape.withHandle { nativeShape -> MemoryStack {
            val out = NUInt32.valueOf(UInt.MAX_VALUE)
            checkJolt("create body", cj_body_create(world, info, nativeShape, if (add) 1u else 0u, if (activate) 1 else 0, out.ptr()))
            JoltBodyId(out.value)
        } } }

    public fun createAndAdd(info: NValue<JoltBodyCreateInfo>, shape: JoltShape, activate: Boolean = true): JoltBodyId =
        create(info.ptr(), shape, true, activate)

    public fun add(id: JoltBodyId, activate: Boolean = true): Unit = owner.operation("add body") {
        checkJolt("add body", cj_body_add(it, id.bits, if (activate) 1 else 0))
    }
    public fun remove(id: JoltBodyId): Unit = owner.operation("remove body") { checkJolt("remove body", cj_body_remove(it, id.bits)) }
    /** Remove an added body first. Destroy rejects a body still in the broad phase. */
    public fun destroy(id: JoltBodyId): Unit = owner.operation("destroy body") { checkJolt("destroy body", cj_body_destroy(it, id.bits)) }

    public fun readTransform(id: JoltBodyId, output: NPointer<JoltBodyTransform>): Unit = owner.operation("read transform") {
        checkJolt("read transform", cj_body_get_transform(it, id.bits, output))
    }
    public fun transform(id: JoltBodyId): JoltTransform = MemoryStack {
        val output = JoltBodyTransform.malloc()
        readTransform(id, output.ptr()); output.ptr().copyTransform()
    }

    /** One native call, no per-body JVM objects. All IDs are validated before any output is written. */
    public fun readTransforms(ids: NArray<NUInt32>, output: NArray<JoltBodyTransform>): Unit = owner.operation("read transforms") {
        require(ids.count in 0..UInt.MAX_VALUE.toLong() && output.count in 0..UInt.MAX_VALUE.toLong())
        require(output.count >= ids.count) { "Output capacity is smaller than the ID count" }
        checkJolt("read transforms", cj_body_get_transforms(it, ids.ptr(), ids.count.toUInt(), output.ptr(), output.count.toUInt()))
    }

    public fun setTransform(id: JoltBodyId, position: JoltPosition, rotation: JoltRotation = JoltRotation(), activate: Boolean = true): Unit =
        owner.operation("set transform") { world -> MemoryStack {
            val p = JoltRVec3.valueOf(position.x, position.y, position.z)
            val q = JoltQuat.valueOf(rotation.x, rotation.y, rotation.z, rotation.w)
            checkJolt("set transform", cj_body_set_transform(world, id.bits, p.ptr(), q.ptr(), if (activate) 1 else 0))
        } }

    public fun setVelocity(id: JoltBodyId, linear: JoltVector, angular: JoltVector = JoltVector(0f, 0f, 0f)): Unit =
        owner.operation("set velocity") { world -> MemoryStack {
            val l = JoltVec3.valueOf(linear.x, linear.y, linear.z)
            val a = JoltVec3.valueOf(angular.x, angular.y, angular.z)
            checkJolt("set velocity", cj_body_set_velocity(world, id.bits, l.ptr(), a.ptr()))
        } }
    public fun addForce(id: JoltBodyId, force: JoltVector): Unit = owner.operation("add force") { world -> MemoryStack {
        val v = JoltVec3.valueOf(force.x, force.y, force.z)
        checkJolt("add force", cj_body_add_force(world, id.bits, v.ptr()))
    } }
    public fun addImpulse(id: JoltBodyId, impulse: JoltVector): Unit = owner.operation("add impulse") { world -> MemoryStack {
        val v = JoltVec3.valueOf(impulse.x, impulse.y, impulse.z)
        checkJolt("add impulse", cj_body_add_impulse(world, id.bits, v.ptr()))
    } }
    public fun moveKinematic(id: JoltBodyId, position: JoltPosition, rotation: JoltRotation, deltaTime: Float): Unit =
        owner.operation("move kinematic") { world -> MemoryStack {
            val p = JoltRVec3.valueOf(position.x, position.y, position.z)
            val q = JoltQuat.valueOf(rotation.x, rotation.y, rotation.z, rotation.w)
            checkJolt("move kinematic", cj_body_move_kinematic(world, id.bits, p.ptr(), q.ptr(), deltaTime))
        } }
    public fun setActive(id: JoltBodyId, active: Boolean): Unit = owner.operation("set active") {
        checkJolt("set active", cj_body_set_active(it, id.bits, if (active) 1u else 0u))
    }
    public fun setUserData(id: JoltBodyId, userData: ULong): Unit = owner.operation("set user data") {
        checkJolt("set user data", cj_body_set_user_data(it, id.bits, userData))
    }

    public fun <T> withBodyRead(id: JoltBodyId, block: (JoltBodyReadView) -> T): T = locked(id, false) { handle, scope ->
        block(JoltBodyReadView(handle, scope))
    }
    public fun <T> withBodyWrite(id: JoltBodyId, block: (JoltBodyWriteView) -> T): T = locked(id, true) { handle, scope ->
        block(JoltBodyWriteView(handle, scope))
    }

    private fun <T> locked(id: JoltBodyId, write: Boolean, block: (NPointer<JoltBodyLockHandle>, JoltBorrowScope) -> T): T =
        owner.operation("body lock") { world -> Arena.ofConfined().use { arena ->
            val scope = JoltBorrowScope()
            val output = arena.allocate(ValueLayout.ADDRESS)
            output.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
            checkJolt("lock body", cj_body_lock(world, id.bits, if (write) 1u else 0u, NPointer(output.address())))
            val handle = NPointer<JoltBodyLockHandle>(output.get(ValueLayout.ADDRESS, 0).address())
            var failure: Throwable? = null
            try {
                JoltCallContext.bodyLock.set(true)
                block(handle, scope)
            } catch (next: Throwable) { failure = next; throw next }
            finally {
                scope.invalidate()
                JoltCallContext.bodyLock.set(false)
                try { checkJolt("unlock body", cj_body_unlock(handle)) }
                catch (next: Throwable) { if (failure == null) throw next else failure.addSuppressed(next) }
            }
        } }
}

internal class JoltBorrowScope {
    private val thread = Thread.currentThread()
    @Volatile private var active = true
    fun checkActive() {
        check(active) { "Borrowed Jolt view has expired; copy its data inside the scope" }
        check(Thread.currentThread() === thread) { "Borrowed Jolt view belongs to another thread" }
    }
    fun invalidate() { active = false }
}

/** Valid only on the thread and during the block of withBodyRead/withBodyWrite. */
public open class JoltBodyReadView internal constructor(
    internal val handle: NPointer<JoltBodyLockHandle>, internal val scope: JoltBorrowScope,
) {
    public fun readTransform(output: NPointer<JoltBodyTransform>) {
        scope.checkActive(); checkJolt("read locked body", cj_locked_body_get_transform(handle, output))
    }
    public fun snapshot(): JoltTransform = MemoryStack {
        val output = JoltBodyTransform.malloc(); readTransform(output.ptr()); output.ptr().copyTransform()
    }
}

/** Velocity writes activate an added body after its native lock is released. */
public class JoltBodyWriteView internal constructor(handle: NPointer<JoltBodyLockHandle>, scope: JoltBorrowScope) :
    JoltBodyReadView(handle, scope) {
    public fun setVelocity(linear: JoltVector, angular: JoltVector = JoltVector(0f, 0f, 0f)) {
        scope.checkActive()
        MemoryStack {
            val l = JoltVec3.valueOf(linear.x, linear.y, linear.z)
            val a = JoltVec3.valueOf(angular.x, angular.y, angular.z)
            checkJolt("write locked body velocity", cj_locked_body_set_velocity(handle, l.ptr(), a.ptr()))
        }
    }
}
