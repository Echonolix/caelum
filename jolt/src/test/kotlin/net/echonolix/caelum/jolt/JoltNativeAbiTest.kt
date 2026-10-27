package net.echonolix.caelum.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.enums.*
import net.echonolix.caelum.jolt.functions.*
import net.echonolix.caelum.jolt.structs.*
import org.junit.jupiter.api.Tag
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.*

@Tag("native")
class JoltNativeAbiTest {
    @BeforeTest fun load() = JoltRuntime.load()

    private fun string(pointer: NPointer<NChar>): String = MemorySegment.ofAddress(pointer._address).reinterpret(256).getString(0)

    @Test fun everyGeneratedFieldMatchesTheNativeCompiler() {
        assertEquals(transportLayouts.size.toUInt(), cj_abi_type_count())
        for (index in 0u until cj_abi_type_count()) {
            val name = string(cj_abi_type_name(index))
            val layout = transportLayouts.getValue(name)
            assertEquals(layout.byteSize().toULong(), cj_abi_size(index), name)
            assertEquals(layout.byteAlignment().toULong(), cj_abi_alignment(index), name)
            val fields = layout.memberLayouts().mapNotNull { it.name().orElse(null) }
            assertEquals(fields.size.toUInt(), cj_abi_field_count(index), name)
            for (field in fields.indices) {
                assertEquals(fields[field], string(cj_abi_field_name(index, field.toUInt())))
                assertEquals(layout.byteOffset(groupElement(fields[field])).toULong(), cj_abi_field_offset(index, field.toUInt()), "$name.${fields[field]}")
            }
        }
        assertEquals(ULong.MAX_VALUE, cj_abi_size(UInt.MAX_VALUE))
    }

    @Test fun enumsAndFlagsMatchPinnedCppSemantics() {
        val expected = buildMap<String, Long> {
            JoltStatus.entries.forEach { put(it.name, it.value.toLong()) }
            JoltMotionType.entries.forEach { put(it.name, it.value.toLong()) }
            JoltMotionQuality.entries.forEach { put(it.name, it.value.toLong()) }
            JoltActivation.entries.forEach { put(it.name, it.value.toLong()) }
            JoltValidateResult.entries.forEach { put(it.name, it.value.toLong()) }
            JoltContactKind.entries.forEach { put(it.name, it.value.toLong()) }
            put("JPH_ACTIVATE", 0); put("JPH_DONT_ACTIVATE", 1) // bridge explicitly maps its 0/1 choice
            put("BODY_ID_INVALID", UInt.MAX_VALUE.toLong())
            put("MANIFOLD_CACHE_FULL", 1); put("BODY_PAIR_CACHE_FULL", 2); put("CONTACT_CONSTRAINTS_FULL", 4)
        }
        val actual = (0u until cj_abi_constant_count()).associate { string(cj_abi_constant_name(it)) to cj_abi_constant_value(it) }
        assertEquals(expected, actual)
        assertEquals(JoltBodyId.INVALID.bits, cj_body_id_invalid())
    }

    @Test fun dirtySettingsArraysAreInitializedAndRoundTripThroughJoltMath() {
        Arena.ofConfined().use { arena ->
            val dirty = object : AllocateScope {
                override fun _malloc(byteSize: Long, byteAlignment: Long): Long = arena.allocate(byteSize, byteAlignment).fill(0xa5.toByte()).address()
                override fun _calloc(byteSize: Long, byteAlignment: Long): Long = _malloc(byteSize, byteAlignment)
            }
            val array = JoltBodyCreateInfo.allocate(dirty, 3)
            for (index in 0L..2L) {
                val info = array[index]
                assertEquals(1f, info.rotation.w); assertEquals(0.05f, info.linearDamping)
                assertEquals(0.2f, info.friction); assertEquals(500f, info.maxLinearVelocity)
                assertEquals(1f, info.gravityFactor); assertEquals(1u.toUByte(), info.allowSleeping)
                assertEquals(JoltMotionType.CJ_DYNAMIC.value, info.motionType)
                assertEquals(0.0, info.position.x); assertEquals(0uL, info.userData)
                // Padding is also overwritten, including the tail of this transport struct.
                var offset = 0L
                val bytes = MemorySegment.ofAddress(info._address).reinterpret(JoltBodyCreateInfo.layout.byteSize())
                for (member in JoltBodyCreateInfo.layout.memberLayouts()) {
                    if (member.name().isEmpty) for (byte in offset until offset + member.byteSize()) assertEquals(0.toByte(), bytes.get(ValueLayout.JAVA_BYTE, byte))
                    offset += member.byteSize()
                }
            }
            val source = array[0L]; val output = array[1L]
            source.position.set(16777217.125, -2.5, 3.75)
            source.linearVelocity.set(1.25f, -2.5f, 3.75f)
            source.userData = 0xfedcba9876543210uL
            checkJolt("roundtrip", cj_probe_roundtrip(source, output))
            assertEquals(source.position.x, output.position.x)
            assertEquals(source.linearVelocity.y, output.linearVelocity.y)
            assertEquals(source.userData, output.userData)
            val config = JoltPhysicsSystemConfig.allocate(dirty)
            assertEquals(-9.81f, config.gravity.y)
            assertEquals(10240u, config.maxBodies)
        }
    }

    @Test fun registrationStubsPreservePointerScalarAndExceptionContracts() {
        JoltRuntime.acquire().use { runtime -> runtime.createWorld().use { world ->
            var escaped: JoltContactValidationView? = null
            val listener = object : JoltContactListener {
                override fun validate(event: JoltContactValidationView): JoltValidateResult {
                    escaped = event
                    assertEquals(0x12345678u, event.body1.bodyId.bits)
                    assertEquals(0x123456789abcdef0uL, event.body2.userData)
                    assertEquals(1.25, event.baseOffset.x)
                    return JoltValidateResult.CJ_REJECT_CONTACT
                }
            }
            world.setListeners(listener).use { registration -> MemoryStack {
                val out = NInt32.valueOf(-1)
                checkJolt("callback probe", cj_probe_callback(registration.pointer.validate, NPointer<NChar>(0), out.ptr()))
                assertNull(registration.takeFailure("probe"))
                assertEquals(2, out.value)
                assertFailsWith<IllegalStateException> { escaped!!.snapshot() }
            } }
            world.setListeners(object : JoltContactListener {
                override fun validate(event: JoltContactValidationView): JoltValidateResult = error("contained failure")
            }).use { registration -> MemoryStack {
                val out = NInt32.valueOf(-1)
                checkJolt("callback probe", cj_probe_callback(registration.pointer.validate, NPointer<NChar>(0), out.ptr()))
                assertEquals(3, out.value)
                val failure = assertNotNull(registration.takeFailure("probe"))
                assertEquals("contained failure", failure.cause!!.message)
                assertEquals(1, failure.failureCount)
            } }
        } }
    }
}
