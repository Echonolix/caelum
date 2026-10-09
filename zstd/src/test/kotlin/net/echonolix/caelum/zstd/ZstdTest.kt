package net.echonolix.caelum.zstd

import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.zstd.functions.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZstdTest {
    private fun ptr(segment: MemorySegment) = NPointer<NChar>(segment.address())

    @BeforeTest
    fun setUp() {
        Zstd.load()
    }

    @Test
    fun roundTrip() {
        val input = ByteArray(1 shl 20) { i -> ((i * 31) xor (i ushr 7)).toByte() }
        Arena.ofConfined().use { arena ->
            val src = arena.allocate(input.size.toLong())
            src.copyFrom(MemorySegment.ofArray(input))
            val bound = ZSTD_compressBound(input.size.toLong())
            val compressed = arena.allocate(bound)

            val compressedSize = ZSTD_compress(
                ptr(compressed), bound,
                ptr(src), input.size.toLong(),
                3,
            )
            assertEquals(0u, ZSTD_isError(compressedSize), "compress failed")
            assertTrue(compressedSize in 1 until input.size)

            val contentSize = ZSTD_getFrameContentSize(ptr(compressed), compressedSize)
            assertEquals(input.size.toULong(), contentSize)

            val dst = arena.allocate(input.size.toLong())
            val decompressedSize = ZSTD_decompress(
                ptr(dst), input.size.toLong(),
                ptr(compressed), compressedSize,
            )
            assertEquals(0u, ZSTD_isError(decompressedSize), "decompress failed")
            assertEquals(input.size.toLong(), decompressedSize)
            assertTrue(input.contentEquals(dst.toArray(ValueLayout.JAVA_BYTE)))
        }
    }

    @Test
    fun reportsErrors() {
        Arena.ofConfined().use { arena ->
            val garbage = arena.allocate(16)
            val dst = arena.allocate(16)
            val result = ZSTD_decompress(ptr(dst), 16, ptr(garbage), 16)
            assertEquals(1u, ZSTD_isError(result))
            assertEquals(Zstd.CONTENTSIZE_ERROR.toULong(), ZSTD_getFrameContentSize(ptr(garbage), 16))
        }
    }
}
