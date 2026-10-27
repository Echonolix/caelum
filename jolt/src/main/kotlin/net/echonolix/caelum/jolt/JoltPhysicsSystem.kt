package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.functions.*
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.withLock

/**
 * Owns bodies and table filters. Managed operations on one world are serialized.
 * Separate worlds can update concurrently; shared allocators/pools are serialized.
 */
public class JoltPhysicsSystem internal constructor(
    runtime: JoltRuntime,
    pointer: NPointer<JoltWorldHandle>,
    public val layers: JoltLayers,
    private val defaultAllocator: JoltTempAllocator,
    private val defaultJobs: JoltJobSystem,
    private val ownsAllocator: Boolean,
    private val ownsJobs: Boolean,
) : JoltResource<JoltWorldHandle>(runtime, pointer) {
    public val bodies: JoltBodyInterface = JoltBodyInterface(this)
    public val query: JoltQuery = JoltQuery(this)
    private val deferred = ConcurrentLinkedQueue<JoltPhysicsSystem.() -> Unit>()
    @Volatile private var closing = false
    private var updating = false
    private var draining = false
    internal var registration: JoltListenerRegistration? = null

    internal fun <T> operation(name: String, block: (NPointer<JoltWorldHandle>) -> T): T = withHandle { handle ->
        check(!closing) { "World is closing" }
        try {
            val result = block(handle)
            registration?.takeFailure(name)?.let { throw it }
            result
        } catch (failure: Throwable) {
            registration?.takeFailure(name)?.let(failure::addSuppressed)
            throw failure
        }
    }

    /** Native flags are returned even when nonzero. Listener failures throw after workers finish. */
    public fun update(
        deltaTime: Float,
        collisionSteps: Int = 1,
        allocator: JoltTempAllocator = defaultAllocator,
        jobs: JoltJobSystem = defaultJobs,
    ): JoltUpdateErrors = operation("update") { world ->
        require(deltaTime.isFinite() && deltaTime > 0f)
        require(collisionSteps in 1..1024)
        check(!updating && !draining) { "Recursive update is forbidden" }
        updating = true
        val errors = try {
            allocator.withHandle { scratch -> jobs.withHandle { workers -> MemoryStack {
                val output = NUInt32.valueOf(0u)
                val status = cj_world_update(world, deltaTime, collisionSteps.toUInt(), scratch, workers, output.ptr())
                val flags = JoltUpdateErrors(output.value)
                // Preserve update flags if the callback also failed.
                val callbackFailure = registration?.takeFailure("update", flags)
                try { checkJolt("update", status) } catch (failure: Throwable) {
                    callbackFailure?.let(failure::addSuppressed); throw failure
                }
                if (callbackFailure != null) throw callbackFailure
                flags
            } } }
        } finally { updating = false }
        if (errors.isSuccess) flushDeferred()
        errors
    }

    /** Safe in a listener. Capture copied values/IDs, never a borrowed callback view. */
    public fun defer(command: JoltPhysicsSystem.() -> Unit) {
        check(!isClosed && !closing) { "World is closed" }
        deferred.add(command)
        if (isClosed || closing) { deferred.remove(command); error("World closed while queuing a command") }
    }

    /** Runs at most [maxCommands]; update drains up to 1024 after successful callbacks. */
    public fun flushDeferred(maxCommands: Int = 1024): Int = operation("deferred commands") {
        require(maxCommands >= 0)
        check(!updating && !draining) { "Cannot recursively drain deferred commands" }
        draining = true
        try {
            var count = 0
            while (count < maxCommands) { val command = deferred.poll() ?: break; command(this); count++ }
            count
        } finally { draining = false }
    }

    public fun optimizeBroadPhase(): Unit = operation("optimize broad phase") { checkJolt("optimize broad phase", cj_world_optimize_broad_phase(it)) }
    public var gravity: JoltVector
        get() = operation("get gravity") { world -> MemoryStack {
            val out = JoltVec3.malloc(); checkJolt("get gravity", cj_world_get_gravity(world, out.ptr())); out.ptr().copyVector()
        } }
        set(value) { operation("set gravity") { world -> MemoryStack {
            val input = JoltVec3.valueOf(value.x, value.y, value.z)
            checkJolt("set gravity", cj_world_set_gravity(world, input.ptr()))
        } } }

    /** Replaces the previous registration only after native quiescence. */
    public fun setListeners(contact: JoltContactListener? = null, activation: JoltActivationListener? = null): JoltListenerRegistration =
        operation("set listeners") { world ->
            require(contact != null || activation != null) { "Use clearListeners to remove listeners" }
            val next = JoltListenerRegistration(this, contact, activation)
            try { checkJolt("set listeners", cj_world_set_callbacks(world, next.pointer)) }
            catch (failure: Throwable) { next.dispose(); throw failure }
            val old = registration
            registration = next
            old?.dispose()
            next
        }

    public fun clearListeners(): Unit = operation("clear listeners") { world ->
        checkJolt("clear listeners", cj_world_set_callbacks(world, NPointer(0L)))
        val old = registration; registration = null; old?.dispose()
    }

    internal fun removeRegistration(value: JoltListenerRegistration) {
        JoltCallContext.requireOrdinaryCall()
        resourceLock.withLock {
            if (value.isClosed) return
            operation("remove listeners") {
                if (registration === value) clearListeners() else value.dispose()
            }
        }
    }

    override fun destroy(pointer: NPointer<JoltWorldHandle>) {
        check(!updating && !draining) { "Close the world outside update and deferred command execution" }
        closing = true
        try { checkJolt("destroy world", cj_world_destroy(pointer)) }
        catch (failure: Throwable) { closing = false; throw failure }
    }

    override fun afterClose() {
        deferred.clear()
        var failure: Throwable? = null
        fun cleanup(block: () -> Unit) {
            try { block() } catch (next: Throwable) { if (failure == null) failure = next else failure.addSuppressed(next) }
        }
        cleanup { registration?.dispose(); registration = null }
        if (ownsJobs) cleanup { defaultJobs.close() }
        if (ownsAllocator) cleanup { defaultAllocator.close() }
        failure?.let { throw it }
    }
}

/** Config and tables are copied synchronously; supplied allocator/jobs remain caller owned. */
public fun JoltRuntime.createWorld(
    config: NPointer<JoltPhysicsSystemConfig>,
    layers: JoltLayers = JoltLayers.movingAndStatic(),
    allocator: JoltTempAllocator? = null,
    jobs: JoltJobSystem? = null,
): JoltPhysicsSystem {
    JoltCallContext.requireOrdinaryCall()
    require(config._address != 0L)
    require(config.objectLayerCount.toLong() == layers.objectLayerCount.toLong() &&
        config.broadPhaseLayerCount.toLong() == layers.broadPhaseLayerCount.toLong()) { "Config layer counts must match the supplied tables" }
    val scratch = allocator ?: createTempAllocator()
    var pool: JoltJobSystem? = null
    try {
        val workerPool = jobs ?: createJobSystem()
        pool = workerPool
        check(!scratch.isClosed && !workerPool.isClosed) { "Allocator and job system must be open" }
        return Arena.ofConfined().use { arena ->
            val mapping = arena.allocate(layers.mapping.size.toLong())
            MemorySegment.copy(MemorySegment.ofArray(layers.mapping), 0, mapping, 0, mapping.byteSize())
            val matrix = arena.allocate(layers.collisions.size.toLong())
            MemorySegment.copy(MemorySegment.ofArray(layers.collisions), 0, matrix, 0, matrix.byteSize())
            createResource("create world", { output ->
                cj_world_create(config, NPointer(mapping.address()), layers.objectLayerCount.toUInt(),
                    NPointer(matrix.address()), layers.collisions.size.toUInt(), output)
            }, ::cj_world_destroy) { handle ->
                JoltPhysicsSystem(this, handle, layers, scratch, workerPool, allocator == null, jobs == null)
            }
        }
    } catch (failure: Throwable) {
        if (jobs == null) runCatching { pool?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
        if (allocator == null) runCatching { scratch.close() }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}

public fun JoltRuntime.createWorld(
    layers: JoltLayers = JoltLayers.movingAndStatic(),
    workers: Int = 0,
    configure: NValue<JoltPhysicsSystemConfig>.() -> Unit = {},
): JoltPhysicsSystem = MemoryStack {
    val config = JoltPhysicsSystemConfig.allocate {
        objectLayerCount = layers.objectLayerCount.toUInt()
        broadPhaseLayerCount = layers.broadPhaseLayerCount.toUInt()
        configure()
    }
    // Transfer this overload's resources to the world on success.
    val scratch = createTempAllocator()
    var pool: JoltJobSystem? = null
    try {
        val workerPool = createJobSystem(workers); pool = workerPool
        Arena.ofConfined().use { arena ->
            val mapping = arena.allocate(layers.mapping.size.toLong())
            val matrix = arena.allocate(layers.collisions.size.toLong())
            mapping.copyFrom(MemorySegment.ofArray(layers.mapping)); matrix.copyFrom(MemorySegment.ofArray(layers.collisions))
            createResource("create world", { output ->
                cj_world_create(config.ptr(), NPointer(mapping.address()), layers.objectLayerCount.toUInt(),
                    NPointer(matrix.address()), layers.collisions.size.toUInt(), output)
            }, ::cj_world_destroy) { handle ->
                JoltPhysicsSystem(this@createWorld, handle, layers, scratch, workerPool, true, true)
            }
        }
    } catch (failure: Throwable) {
        runCatching { pool?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
        runCatching { scratch.close() }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}
