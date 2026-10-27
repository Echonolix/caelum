package net.echonolix.caelum.jolt.examples

import com.sun.management.ThreadMXBean
import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

/** Simple baseline harness, not a replacement for forked JMH or an engine-aligned JNI comparison. */
public object Benchmark {
    @Volatile private var sink = 0.0
    @JvmStatic public fun main(args: Array<String>) {
        val count = args.getOrNull(0)?.toInt() ?: 1000
        val workers = args.getOrNull(1)?.toInt() ?: 0
        require(count in 1..100000)
        println("# jdk=${System.getProperty("java.version")} os=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")} processors=${Runtime.getRuntime().availableProcessors()}")
        println("# commit=${JoltRuntime.JOLT_COMMIT} precision=64 layer=16 cpu=baseline fp=deterministic bodies=$count workers=$workers")
        println("# Record CPU model, RAM, OS version, power mode, and this checkout revision beside this output.")
        println("# JVM bytes count the caller thread only. Native counters count Jolt allocator requests, not live bytes/RSS.")
        println("case,iterations,units_per_iteration,p50_ns,p95_ns,units_per_second,jvm_caller_bytes,jolt_alloc_calls,jolt_bytes_requested,live_resources")
        JoltRuntime.acquire().use { runtime -> runtime.createWorld(workers = workers, configure = {
            maxBodies = (count + 1).toUInt()
            maxBodyPairs = maxOf(65536, count * 8).toUInt()
            maxContactConstraints = maxOf(10240, count * 4).toUInt()
        }).use { world -> Arena.ofConfined().use { arena ->
            val scope = WrappedAllocateScope.Impl(arena)
            val ids = scope.malloc(NUInt32, count.toLong())
            val transforms = JoltBodyTransform.allocate(scope, count.toLong())
            runtime.createSphere(0.5f).use { sphere ->
                // Sensors keep a fixed overlap without solver motion, so each
                // density below produces a stable, measurable callback load.
                val settings = JoltBodyCreateInfo.allocate(scope) {
                    layer = JoltObjectLayer.of(1); allowSleeping = 0u; isSensor = 1u; gravityFactor = 0f
                }
                repeat(count) { index ->
                    settings.position.set((index % 100) * 1.25, 0.45, (index / 100) * 1.25)
                    ids[index.toLong()] = world.bodies.createAndAdd(settings, sphere).bits
                }
            }
            runtime.createBox(JoltVector(100000f, 0.5f, 100000f)).use { shape ->
                val settings = JoltBodyCreateInfo.allocate(scope) { motion = JoltMotionType.CJ_STATIC; layer = JoltObjectLayer.of(0); position.set(0.0, -0.5, 0.0) }
                world.bodies.createAndAdd(settings, shape, false)
            }
            world.optimizeBroadPhase()
            val first = JoltBodyId(ids[0L])
            measure("single_transform", 1) { world.bodies.readTransform(first, transforms[0L]); sink = transforms[0L].position.y }
            measure("batch_transform", count) { world.bodies.readTransforms(ids, transforms); sink = transforms[(count - 1).toLong()].position.y }
            measure("update_no_listener", count) { world.update(1f / 60f).throwIfFailed() }
            for (density in listOf(0, 1, 100)) {
                repeat(count) { index ->
                    val height = if (index.toLong() * 100 < count.toLong() * density) 0.45 else 3.0
                    world.bodies.setTransform(JoltBodyId(ids[index.toLong()]), JoltPosition((index % 100) * 1.25, height, (index / 100) * 1.25))
                }
                val callbacks = AtomicLong()
                world.setListeners(object : JoltContactListener {
                    override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) {
                        callbacks.incrementAndGet(); sink = event.body1.position.y
                    }
                }).use {
                    measure("update_contacts_${density}pct", count) { world.update(1f / 60f).throwIfFailed() }
                }
                println("# contacts_${density}pct callbacks=${callbacks.get()}")
            }
        } } }
        println("# final_live_resources=${JoltRuntime.liveNativeResources()}")
    }

    private fun measure(name: String, units: Int, operation: () -> Unit) {
        repeat(120) { operation() }
        val iterations = 500
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        val thread = Thread.currentThread().threadId()
        if (bean?.isThreadAllocatedMemorySupported == true && !bean.isThreadAllocatedMemoryEnabled) bean.isThreadAllocatedMemoryEnabled = true
        fun allocated(): Long = if (bean?.isThreadAllocatedMemorySupported == true) bean.getThreadAllocatedBytes(thread) else -1
        val times = LongArray(iterations)
        val jvmBefore = allocated(); val nativeBefore = JoltRuntime.allocationStats()
        repeat(iterations) { index -> val start = System.nanoTime(); operation(); times[index] = System.nanoTime() - start }
        val nativeAfter = JoltRuntime.allocationStats(); val jvmAfter = allocated()
        val elapsed = times.sum(); times.sort()
        println("$name,$iterations,$units,${times[iterations / 2]},${times[iterations * 95 / 100]},${iterations.toDouble() * units * 1e9 / elapsed},${if (jvmBefore < 0) -1 else jvmAfter - jvmBefore},${nativeAfter.calls - nativeBefore.calls},${nativeAfter.bytesRequested - nativeBefore.bytesRequested},${JoltRuntime.liveNativeResources()}")
    }
}
