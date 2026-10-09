package net.echonolix.caelum.bc7e

import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NUInt32
import net.echonolix.caelum.NUInt64
import net.echonolix.caelum.bc7e.functions.*
import net.echonolix.caelum.bc7e.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.ValueLayout
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Bc7eTest {
    @BeforeTest
    fun setUp() {
        Bc7e.load()
    }

    /** Same numbers as the static asserts in src/main/c/bc7e_abi_check.c. */
    @Test
    fun generatedLayoutMatchesIspc() {
        assertEquals(20, bc7e_opaque_settings.layout.byteSize())
        assertEquals(12, bc7e_opaque_settings.layout.byteOffset(groupElement("m_use_mode")))
        assertEquals(28, bc7e_alpha_settings.layout.byteSize())
        assertEquals(20, bc7e_alpha_settings.layout.byteOffset(groupElement("m_use_mode4")))
        val params = bc7e_compress_block_params.layout
        assertEquals(124, params.byteSize())
        assertEquals(48, params.byteOffset(groupElement("m_uber_level")))
        assertEquals(72, params.byteOffset(groupElement("m_perceptual")))
        assertEquals(76, params.byteOffset(groupElement("m_opaque_settings")))
        assertEquals(96, params.byteOffset(groupElement("m_alpha_settings")))
    }

    @Test
    fun presetsInitializeParams() {
        Arena.ofConfined().use { arena ->
            val p = NPointer<bc7e_compress_block_params>(arena.allocate(bc7e_compress_block_params.layout).address())
            bc7e_compress_block_params_init_basic(p, 1u)
            assertEquals(1u.toUByte(), p.m_perceptual)
            assertEquals(0u.toUByte(), p.m_mode6_only)
            bc7e_compress_block_params_init_ultrafast(p, 0u)
            assertEquals(0u.toUByte(), p.m_perceptual)
            assertEquals(1u.toUByte(), p.m_mode6_only)
        }
    }

    /**
     * Opaque blocks whose colors lie on a line in RGB. Ultrafast params force BC7 mode
     * 6 for opaque blocks, which this test decodes to check the reconstruction error.
     */
    @Test
    fun encodesMode6Blocks() {
        val blockCount = 256
        val pixels = IntArray(blockCount * 16) { i ->
            val block = i / 16
            val t = i % 16
            val r = (block % 16) * 4 + t * 9
            val g = 230 - (block / 16) * 3 - t * 7
            val b = 40 + block / 4 + t * 4
            r or (g shl 8) or (b shl 16) or (255 shl 24)
        }
        Arena.ofConfined().use { arena ->
            val params = NPointer<bc7e_compress_block_params>(arena.allocate(bc7e_compress_block_params.layout).address())
            bc7e_compress_block_params_init_ultrafast(params, 0u)
            val src = arena.allocate(ValueLayout.JAVA_INT, pixels.size.toLong())
            src.copyFrom(java.lang.foreign.MemorySegment.ofArray(pixels))
            val dst = arena.allocate(blockCount.toLong() * Bc7e.BLOCK_BYTES, 16)
            bc7e_compress_blocks(
                blockCount.toUInt(),
                NPointer<NUInt64>(dst.address()),
                NPointer<NUInt32>(src.address()),
                params,
            )
            val blocks = dst.toArray(ValueLayout.JAVA_LONG)
            var maxError = 0
            for (block in 0 until blockCount) {
                val decoded = decodeMode6(blocks[block * 2], blocks[block * 2 + 1])
                for (t in 0 until 16) {
                    val expected = pixels[block * 16 + t]
                    for (c in 0 until 4) {
                        val e = (expected ushr (c * 8)) and 0xFF
                        val d = (decoded[t] ushr (c * 8)) and 0xFF
                        maxError = maxOf(maxError, abs(e - d))
                    }
                }
            }
            assertTrue(maxError <= 6, "max channel error ${maxError}")
        }
    }

    private fun decodeMode6(lo: Long, hi: Long): IntArray {
        var bit = 0
        fun read(count: Int): Int {
            var value = 0
            for (i in 0 until count) {
                val b = if (bit < 64) (lo ushr bit) and 1L else (hi ushr (bit - 64)) and 1L
                value = value or (b.toInt() shl i)
                bit++
            }
            return value
        }
        assertEquals(1 shl 6, read(7), "expected a mode 6 block")
        val raw = IntArray(8) { read(7) } // R0 R1 G0 G1 B0 B1 A0 A1
        val p0 = read(1)
        val p1 = read(1)
        val weights = intArrayOf(0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64)
        return IntArray(16) { t ->
            val index = read(if (t == 0) 3 else 4)
            val w = weights[index]
            var color = 0
            for (c in 0 until 4) {
                val e0 = (raw[c * 2] shl 1) or p0
                val e1 = (raw[c * 2 + 1] shl 1) or p1
                color = color or ((((64 - w) * e0 + w * e1 + 32) shr 6) shl (c * 8))
            }
            color
        }
    }
}
