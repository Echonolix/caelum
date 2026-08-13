package net.echonolix.caelum.sdl3

import java.lang.foreign.Arena
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NUInt64
import net.echonolix.caelum.asAllocateScope
import net.echonolix.caelum.calloc
import net.echonolix.caelum.nullptr
import net.echonolix.caelum.value

class SDLInlineTest {
    @Test
    fun `all public header-only helpers are inventoried`() {
        assertEquals(
            listOf(
                "SDL_HasExactlyOneBitSet32",
                "SDL_MostSignificantBitIndex32",
                "SDL_PointInRect",
                "SDL_PointInRectFloat",
                "SDL_RectEmpty",
                "SDL_RectEmptyFloat",
                "SDL_RectToFRect",
                "SDL_RectsEqual",
                "SDL_RectsEqualEpsilon",
                "SDL_RectsEqualFloat",
                "SDL_SwapFloat",
                "SDL_size_add_check_overflow",
                "SDL_size_add_check_overflow_builtin",
                "SDL_size_mul_check_overflow",
                "SDL_size_mul_check_overflow_builtin",
            ),
            SDLInlineFunctions.names,
        )
        assertTrue(SDLInlineFunctions.unsupported.isEmpty())
    }

    @Test
    fun `size helpers preserve output on overflow and share builtin semantics`() {
        Arena.ofConfined().use { arena ->
            val allocator = arena.asAllocateScope()
            val result = NUInt64.calloc(allocator).ptr()

            assertTrue(SDL_size_mul_check_overflow(6uL, 7uL, result))
            assertEquals(42uL, result.value)
            assertFalse(SDL_size_mul_check_overflow(ULong.MAX_VALUE, 2uL, result))
            assertEquals(42uL, result.value)

            assertTrue(SDL_size_add_check_overflow_builtin(20uL, 22uL, result))
            assertEquals(42uL, result.value)
            assertFalse(SDL_size_add_check_overflow(ULong.MAX_VALUE, 1uL, result))
            assertEquals(42uL, result.value)

            assertTrue(SDL_size_mul_check_overflow_builtin(7uL, 6uL, result))
            assertEquals(42uL, result.value)
            assertFailsWith<IllegalArgumentException> {
                SDL_size_add_check_overflow(1uL, 2uL, nullptr())
            }
        }
    }

    @Test
    fun `bit and byte-swap helpers match SDL semantics`() {
        assertEquals(-1, SDL_MostSignificantBitIndex32(0u))
        assertEquals(0, SDL_MostSignificantBitIndex32(1u))
        assertEquals(31, SDL_MostSignificantBitIndex32(0x80000000u))
        assertFalse(SDL_HasExactlyOneBitSet32(0u))
        assertTrue(SDL_HasExactlyOneBitSet32(0x80000000u))
        assertFalse(SDL_HasExactlyOneBitSet32(3u))

        val input = Float.fromBits(0x01020304)
        assertEquals(0x04030201, SDL_SwapFloat(input).toRawBits())
    }

    @Test
    fun `integer rectangle helpers preserve half-open boundaries and null rules`() {
        Arena.ofConfined().use { arena ->
            val allocator = arena.asAllocateScope()
            val rect = SDL_Rect.calloc(allocator)
            val copy = SDL_Rect.calloc(allocator)
            val point = SDL_Point.calloc(allocator)
            val converted = SDL_FRect.calloc(allocator)

            rect.x = 10
            rect.y = 20
            rect.w = 2
            rect.h = 3
            copy.x = 10
            copy.y = 20
            copy.w = 2
            copy.h = 3

            point.x = 10
            point.y = 20
            assertTrue(SDL_PointInRect(point.ptr(), rect.ptr()))
            point.x = 12
            assertFalse(SDL_PointInRect(point.ptr(), rect.ptr()))
            assertFalse(SDL_PointInRect(nullptr(), rect.ptr()))

            assertFalse(SDL_RectEmpty(rect.ptr()))
            assertTrue(SDL_RectEmpty(nullptr()))
            assertTrue(SDL_RectsEqual(rect.ptr(), copy.ptr()))
            assertFalse(SDL_RectsEqual(rect.ptr(), nullptr()))

            SDL_RectToFRect(rect.ptr(), converted.ptr())
            assertEquals(10.0f, converted.x)
            assertEquals(20.0f, converted.y)
            assertEquals(2.0f, converted.w)
            assertEquals(3.0f, converted.h)
            assertFailsWith<IllegalArgumentException> {
                SDL_RectToFRect(nullptr(), converted.ptr())
            }
        }
    }

    @Test
    fun `floating rectangle helpers preserve closed boundaries and epsilon rules`() {
        Arena.ofConfined().use { arena ->
            val allocator = arena.asAllocateScope()
            val rect = SDL_FRect.calloc(allocator)
            val copy = SDL_FRect.calloc(allocator)
            val point = SDL_FPoint.calloc(allocator)

            rect.x = 1.0f
            rect.y = 2.0f
            rect.w = 3.0f
            rect.h = 4.0f
            copy.x = rect.x + SDL_FLT_EPSILON
            copy.y = rect.y
            copy.w = rect.w
            copy.h = rect.h

            point.x = 4.0f
            point.y = 6.0f
            assertTrue(SDL_PointInRectFloat(point.ptr(), rect.ptr()))
            point.x = 4.0001f
            assertFalse(SDL_PointInRectFloat(point.ptr(), rect.ptr()))

            assertFalse(SDL_RectEmptyFloat(rect.ptr()))
            rect.w = -0.01f
            assertTrue(SDL_RectEmptyFloat(rect.ptr()))
            assertTrue(SDL_RectEmptyFloat(nullptr()))

            rect.w = 3.0f
            assertTrue(SDL_RectsEqualFloat(rect.ptr(), copy.ptr()))
            assertTrue(SDL_RectsEqualEpsilon(rect.ptr(), rect.ptr(), 0.0f))
            copy.x += 1.0f
            assertFalse(SDL_RectsEqualFloat(rect.ptr(), copy.ptr()))
            assertFalse(SDL_RectsEqualEpsilon(rect.ptr(), nullptr(), Float.MAX_VALUE))
        }
    }
}

private fun assertSizeHelperSignature(
    helper: (ULong, ULong, NPointer<NUInt64>) -> Boolean,
) {
    @Suppress("UNUSED_VARIABLE")
    val signature = helper
}

@Suppress("unused")
private fun verifyInlineSignatures() {
    assertSizeHelperSignature(::SDL_size_mul_check_overflow)
    assertSizeHelperSignature(::SDL_size_mul_check_overflow_builtin)
    assertSizeHelperSignature(::SDL_size_add_check_overflow)
    assertSizeHelperSignature(::SDL_size_add_check_overflow_builtin)
}
