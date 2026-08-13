package net.echonolix.caelum.dxgi.com

import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuidTest {
    @Test
    fun canonicalTextRoundTripsThroughWindowsNativeByteOrder() {
        val value = Guid.parse("00112233-4455-6677-8899-aabbccddeeff")
        Arena.ofConfined().use { arena ->
            val native = value.allocate(arena)
            val actual = ByteArray(Guid.BYTE_SIZE.toInt()) { native.get(ValueLayout.JAVA_BYTE, it.toLong()) }
            assertContentEquals(
                byteArrayOf(
                    0x33, 0x22, 0x11, 0x00, 0x55, 0x44, 0x77, 0x66,
                    0x88.toByte(), 0x99.toByte(), 0xaa.toByte(), 0xbb.toByte(),
                    0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(),
                ),
                actual,
            )
            assertEquals(value, Guid.read(native))
            assertEquals("00112233-4455-6677-8899-aabbccddeeff", Guid.read(native).toString())
        }
    }

    @Test
    fun parserRejectsNonCanonicalOrOutOfRangeInput() {
        assertFailsWith<IllegalArgumentException> { Guid.parse("{00112233-4455-6677-8899-aabbccddeeff}") }
        assertFailsWith<IllegalArgumentException> { Guid.parse("00112233445566778899aabbccddeeff") }
        Arena.ofConfined().use { arena ->
            assertFailsWith<IllegalArgumentException> { Guid.read(arena.allocate(15L)) }
            assertFailsWith<IllegalArgumentException> { Guid.parse("00112233-4455-6677-8899-aabbccddeeff").write(arena.allocate(16L), 1L) }
        }
    }
}
