package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.functions.*
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** A bridge failure. [code] preserves CJStatus; [operation] identifies the failing call. */
public class JoltException(public val operation: String, public val code: Int, message: String) :
    RuntimeException("$operation failed ($code): $message")

internal fun checkJolt(operation: String, status: Int) {
    if (status != 0) {
        val pointer = cj_last_error()
        val message = if (pointer._address == 0L) "no native diagnostic" else
            MemorySegment.ofAddress(pointer._address).reinterpret(2048).getString(0)
        throw JoltException(operation, status, message)
    }
}

internal object JoltCallContext {
    val callback: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    val bodyLock: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    fun requireOrdinaryCall() {
        check(!callback.get()) { "Jolt resources and worlds cannot be accessed from a callback; use its snapshots and world.defer" }
        check(!bodyLock.get()) { "Only the supplied body view may be used inside a body lock scope" }
    }
}

internal object JoltNative {
    const val BUILD_ID: String = "caelum-jolt/1;jolt=e77f175595e64cb44218cc9d9d56fc365ad0e36a;real=64;layer=16;cpu=baseline;fp=deterministic;exceptions=on;gpu=off"
    @Volatile private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            NativeLibraries.load(JoltRuntime::class.java, "caelum_jolt")
            // Bootstrap uses real FFM address carriers and touches no eagerly
            // initialized generated GlobalFuncs before the ABI is accepted.
            fun integer(name: String): Int = APIHelper.LINKER.downcallHandle(
                APIHelper.findSymbol(name), FunctionDescriptor.of(ValueLayout.JAVA_INT)
            ).invokeWithArguments() as Int
            check(integer("cj_abi_version") == 1) { "Incompatible caelum-jolt ABI" }
            check(integer("cj_precision_bits") == 64) { "caelum-jolt requires double-precision positions" }
            check(integer("cj_object_layer_bits") == 16) { "caelum-jolt requires 16-bit object layers" }
            val buildPointer = APIHelper.LINKER.downcallHandle(
                APIHelper.findSymbol("cj_build_id"), FunctionDescriptor.of(ValueLayout.ADDRESS)
            ).invokeWithArguments() as MemorySegment
            check(buildPointer.address() != 0L) { "Missing caelum-jolt build ID" }
            val build = buildPointer.reinterpret(512).getString(0)
            check(build == BUILD_ID) { "Incompatible caelum-jolt build: $build" }
            loaded = true
        }
    }
}

/**
 * A lease on Jolt's global allocator, factory and type registry. Close every
 * resource created by this lease before closing it. Other leases are independent.
 * Loading is process-wide; close does not unload the native library.
 */
public class JoltRuntime private constructor() : AutoCloseable {
    private val monitor = Any()
    private var closed = false
    private var children = 0

    public val isClosed: Boolean get() = synchronized(monitor) { closed }
    public val ownedResourceCount: Int get() = synchronized(monitor) { children }

    internal fun <H : NType, R : JoltResource<H>> createResource(
        operation: String,
        create: (NPointer<NPointer<H>>) -> Int,
        destroy: (NPointer<H>) -> Int,
        wrap: (NPointer<H>) -> R,
    ): R {
        JoltCallContext.requireOrdinaryCall()
        return synchronized(monitor) {
            check(!closed) { "JoltRuntime is closed" }
            Arena.ofConfined().use { arena ->
                val output = arena.allocate(ValueLayout.ADDRESS)
                output.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
                checkJolt(operation, create(NPointer(output.address())))
                val address = output.get(ValueLayout.ADDRESS, 0).address()
                check(address != 0L) { "$operation succeeded with a null resource" }
                val pointer = NPointer<H>(address)
                try {
                    val resource = wrap(pointer)
                    children++
                    resource
                } catch (failure: Throwable) {
                    runCatching { checkJolt("rollback $operation", destroy(pointer)) }.exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
            }
        }
    }

    internal fun resourceClosed() { synchronized(monitor) { check(children > 0); children-- } }

    /** Explicit scratch storage; exhausted fixed storage falls back to native allocation. */
    public fun createTempAllocator(bytes: UInt = 10u * 1024u * 1024u): JoltTempAllocator = createResource(
        "create temp allocator", { cj_temp_allocator_create(bytes, it) }, ::cj_temp_allocator_destroy,
    ) { JoltTempAllocator(this, it) }

    /** Zero workers is deterministic single-thread scheduling; 1..256 uses Jolt workers. */
    public fun createJobSystem(workers: Int = 0): JoltJobSystem {
        require(workers in 0..256) { "workers must be in 0..256" }
        return createResource("create job system", { cj_job_system_create(workers.toUInt(), it) }, ::cj_job_system_destroy) {
            JoltJobSystem(this, it, workers)
        }
    }

    override fun close() {
        JoltCallContext.requireOrdinaryCall()
        synchronized(monitor) {
            if (closed) return
            check(children == 0) { "Close $children owned Jolt resources before closing this runtime" }
            checkJolt("release runtime", cj_runtime_release())
            closed = true
        }
    }

    public companion object {
        public const val ABI_VERSION: Int = 1
        public const val JOLT_COMMIT: String = "e77f175595e64cb44218cc9d9d56fc365ad0e36a"

        /** Loads and checks the ABI without initializing the physics engine. Idempotent. */
        @JvmStatic public fun load() { JoltCallContext.requireOrdinaryCall(); JoltNative.load() }

        @JvmStatic public fun acquire(): JoltRuntime {
            load()
            // Allocate the wrapper before acquiring the native lease.
            val lease = JoltRuntime()
            checkJolt("acquire runtime", cj_runtime_acquire())
            return lease
        }

        /** Diagnostic count of all owned bridge resources across runtime leases. */
        @JvmStatic public fun liveNativeResources(): ULong { load(); return cj_live_resources() }
        /** Excludes JVM, bridge/system allocations and suballocations within scratch buffers. */
        @JvmStatic public fun allocationStats(): JoltAllocationStats {
            load(); return JoltAllocationStats(cj_allocation_calls(), cj_allocation_bytes_requested())
        }
    }
}

/** Managed opaque handle. Borrowed views and BodyId deliberately do not inherit this. */
public abstract class JoltResource<H : NType> internal constructor(
    internal val runtime: JoltRuntime,
    pointer: NPointer<H>,
) : AutoCloseable {
    internal val resourceLock: ReentrantLock = ReentrantLock()
    private var pointer: NPointer<H> = pointer
    @Volatile private var closed = false
    public val isClosed: Boolean get() = closed

    internal fun <T> withHandle(block: (NPointer<H>) -> T): T {
        JoltCallContext.requireOrdinaryCall()
        return resourceLock.withLock {
            check(pointer._address != 0L) { "${javaClass.simpleName} is closed" }
            block(pointer)
        }
    }

    protected abstract fun destroy(pointer: NPointer<H>)
    protected open fun afterClose(): Unit = Unit

    final override fun close() {
        JoltCallContext.requireOrdinaryCall()
        resourceLock.withLock {
            if (pointer._address == 0L) return
            destroy(pointer)
            pointer = NPointer(0L)
            closed = true
            runtime.resourceClosed()
            afterClose()
        }
    }
}

/** May be shared across worlds; update holds this resource's lock until workers finish. */
public class JoltTempAllocator internal constructor(runtime: JoltRuntime, pointer: NPointer<JoltTempAllocatorHandle>) :
    JoltResource<JoltTempAllocatorHandle>(runtime, pointer) {
    override fun destroy(pointer: NPointer<JoltTempAllocatorHandle>) { checkJolt("destroy temp allocator", cj_temp_allocator_destroy(pointer)) }
}

/** Owned worker pool or single-thread scheduler. Close waits for an in-flight managed use. */
public class JoltJobSystem internal constructor(runtime: JoltRuntime, pointer: NPointer<JoltJobSystemHandle>, public val workers: Int) :
    JoltResource<JoltJobSystemHandle>(runtime, pointer) {
    override fun destroy(pointer: NPointer<JoltJobSystemHandle>) { checkJolt("destroy job system", cj_job_system_destroy(pointer)) }
}
