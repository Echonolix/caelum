package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.StructLayout
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.readText
import kotlin.test.*

internal val transportLayouts: Map<String, StructLayout> = linkedMapOf(
    "CJVec3" to JoltVec3.layout, "CJRVec3" to JoltRVec3.layout, "CJQuat" to JoltQuat.layout,
    "CJWorldConfig" to JoltPhysicsSystemConfig.layout, "CJBodyCreateInfo" to JoltBodyCreateInfo.layout,
    "CJBodyTransform" to JoltBodyTransform.layout, "CJRayCast" to JoltRayCast.layout,
    "CJRayCastResult" to JoltRayCastResult.layout, "CJContactValidation" to JoltContactValidation.layout,
    "CJContactEvent" to JoltContactEvent.layout, "CJContactSettings" to JoltContactSettings.layout,
    "CJCallbacks" to JoltCallbacks.layout,
)

/** Runs without loading Jolt; native ABI agreement is tested separately. */
class JoltContractsTest {
    @Test fun layerTablesAreCopiedAndSymmetric() {
        val mapping = intArrayOf(0, 1)
        val layers = JoltLayers.of(mapping, 2) { a, b -> a != 0 || b != 0 }
        mapping[0] = 200
        assertEquals(0, layers.mapping[0].toInt())
        assertFalse(layers.collides(JoltObjectLayer.of(0), JoltObjectLayer.of(0)))
        assertTrue(layers.collides(JoltObjectLayer.of(0), JoltObjectLayer.of(1)))
        assertFailsWith<IllegalArgumentException> { JoltLayers.of(intArrayOf(0, 1), 2) { a, b -> a < b } }
        assertFailsWith<IllegalArgumentException> { JoltLayers.of(intArrayOf(2), 2) { _, _ -> true } }
        assertFailsWith<IllegalArgumentException> { JoltObjectLayer.of(65536) }
    }

    @Test fun borrowScopesRejectEscapeAndForeignThreads() {
        val scope = JoltBorrowScope()
        scope.checkActive()
        Executors.newSingleThreadExecutor().use { executor ->
            assertTrue(executor.submit<Boolean> {
                runCatching { scope.checkActive() }.exceptionOrNull() is IllegalStateException
            }.get())
        }
        scope.invalidate()
        assertFailsWith<IllegalStateException> { scope.checkActive() }
    }

    @Test fun managedCallsRejectCallbackAndBodyLockReentry() {
        try {
            JoltCallContext.callback.set(true)
            assertFailsWith<IllegalStateException> { JoltRuntime.load() }
            JoltCallContext.callback.set(false)
            JoltCallContext.bodyLock.set(true)
            assertFailsWith<IllegalStateException> { JoltRuntime.acquire() }
        } finally { JoltCallContext.callback.set(false); JoltCallContext.bodyLock.set(false) }
    }

    @Test fun idsPreserveSequenceAndUpdateFlags() {
        assertFalse(JoltBodyId.INVALID.isValid)
        assertFalse(JoltBodyId(0x80000001u).isValid)
        assertTrue(JoltBodyId(0x7f000001u).isValid)
        val flags = JoltUpdateErrors(0x80000007u)
        assertTrue(flags.manifoldCacheFull && flags.bodyPairCacheFull && flags.contactConstraintsFull)
        assertEquals(0x80000007u, flags.bits)
        assertFailsWith<IllegalStateException> { flags.throwIfFailed() }
    }

    @Test fun generatedAccessorsAliasNestedFieldsAndRespectArrayStride() {
        Arena.ofConfined().use { arena ->
            val storage = arena.allocate(JoltBodyTransform.layout.byteSize() * 2, JoltBodyTransform.layout.byteAlignment()).fill(0)
            val values = NArray<JoltBodyTransform>(storage.address(), 2)
            values[0L].position.set(16777217.125, -2.5, 3.75)
            values[1L].position.set(4.0, 5.0, 6.0)
            val nested = values[0L].position
            nested.y = 9.5
            assertEquals(9.5, values[0L].position.y)
            assertEquals(5.0, values[1L].position.y)
            assertEquals(16777217.125, values[0L].position.x)
            assertEquals(JoltBodyTransform.layout.byteSize(), values[1L]._address - values[0L]._address)
        }
        assertEquals(12L, JoltVec3.layout.byteSize()); assertEquals(4L, JoltVec3.layout.byteAlignment())
        assertEquals(24L, JoltRVec3.layout.byteSize()); assertEquals(8L, JoltRVec3.layout.byteAlignment())
        assertEquals(16L, JoltQuat.layout.byteSize()); assertEquals(4L, JoltQuat.layout.byteAlignment())
        assertFailsWith<ArithmeticException> { JoltBodyTransform.allocate(object : AllocateScope {
            override fun _malloc(byteSize: Long, byteAlignment: Long): Long = error("Must validate before allocating")
            override fun _calloc(byteSize: Long, byteAlignment: Long): Long = error("Must validate before allocating")
        }, Long.MAX_VALUE) }
    }

    @Test fun callbackViewsCopySnapshotsAndGuardSettingsWrites() {
        Arena.ofConfined().use { arena ->
            val event = NPointer<JoltContactEvent>(arena.allocate(JoltContactEvent.layout).fill(0).address())
            event.body1.bodyId = 7u; event.body2.bodyId = 8u
            event.pointCount = 1u; event.points1[0L].set(1f, 2f, 3f)
            event.baseOffset.set(16777217.125, 4.0, 5.0)
            val scope = JoltBorrowScope()
            val view = JoltContactEventView(event, scope)
            val copy = view.snapshot()
            event.points1[0L].x = 99f
            assertEquals(JoltBodyId(7u), copy.body1.bodyId)
            assertEquals(1f, copy.points1[0].x)
            assertEquals(16777217.125, copy.baseOffset.x)
            assertEquals(99f, view.pointOnBody1(0).x)
            assertFailsWith<IllegalArgumentException> { view.pointOnBody1(1) }
            val settings = NPointer<JoltContactSettings>(arena.allocate(JoltContactSettings.layout).fill(0).address())
            val writable = JoltContactSettingsView(settings, scope)
            writable.friction = 0.4f; writable.isSensor = true
            writable.relativeLinearSurfaceVelocity = JoltVector(1f, 2f, 3f)
            assertEquals(0.4f, settings.friction)
            assertEquals(1u.toUByte(), settings.isSensor)
            assertEquals(2f, writable.relativeLinearSurfaceVelocity.y)
            assertFailsWith<IllegalArgumentException> { writable.inverseMassScale1 = Float.NaN }
            scope.invalidate()
            assertFailsWith<IllegalStateException> { view.snapshot() }
            assertFailsWith<IllegalStateException> { writable.friction = 0.1f }
        }
    }

    @Test fun headerSurfaceIsFullyImplementedGeneratedAndInventoried() {
        val header = Path.of("include/caelum_jolt.h").readText()
        val implementation = Path.of("src/main/cpp/caelum_jolt.cpp").readText() + Path.of("src/main/cpp/caelum_jolt_abi.cpp").readText()
        val globals = Path.of("build/generated/ktgen/net/echonolix/caelum/jolt/functions/GlobalFuncs.kt").readText()
        val surface = Path.of("SURFACE.md").readText()
        val exports = Regex("CJ_API[^;]+?\\b(cj_\\w+)\\s*\\(").findAll(header).map { it.groupValues[1] }.toSet()
        assertTrue(exports.size > 50)
        val implemented = Regex("\\b(cj_\\w+)\\s*\\([^;{}]*\\)\\s*\\{").findAll(implementation).map { it.groupValues[1] }.toSet()
        val generated = Regex("public fun (cj_\\w+)\\(").findAll(globals).map { it.groupValues[1] }.toSet()
        assertEquals(exports, implemented)
        assertEquals(exports, generated)
        for (symbol in exports) assertTrue(surface.contains("`$symbol`"), "Missing surface entry: $symbol")
        val structs = Regex("struct (CJ\\w+)\\s*\\{([^}]+)}", RegexOption.DOT_MATCHES_ALL).findAll(header).toList()
        assertEquals(transportLayouts.keys, structs.map { it.groupValues[1] }.toSet())
        for (struct in structs) {
            val name = struct.groupValues[1]
            val fields = struct.groupValues[2].split(';').map(String::trim).filter(String::isNotEmpty)
                .map { Regex("(\\w+)(?:\\[\\d+])?$").find(it)!!.groupValues[1] }
            val layout = transportLayouts.getValue(name)
            assertEquals(fields, layout.memberLayouts().mapNotNull { it.name().orElse(null) }, name)
            for (field in fields) {
                assertTrue(implementation.contains("FIELD($name, $field)"), "Missing native oracle: $name.$field")
                assertTrue(layout.byteOffset(groupElement(field)) < layout.byteSize())
            }
        }
    }
}
