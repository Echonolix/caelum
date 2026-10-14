package net.echonolix.caelum.sdl3

import java.lang.foreign.Arena
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.asAllocateScope
import net.echonolix.caelum.calloc
import net.echonolix.caelum.get
import net.echonolix.caelum.set

class SDLTypeAccessorsTest {
    @Test
    fun `scalar enum bool pointer and TLS accessors round trip through both receivers`() {
        Arena.ofConfined().use { arena ->
            val allocator = arena.asAllocateScope()
            val rect = SDL_Rect.calloc(allocator)
            val audioEvent = SDL_AudioDeviceEvent.calloc(allocator)
            val surface = SDL_Surface.calloc(allocator)
            val displayMode = SDL_DisplayMode.calloc(allocator)
            val tls = SDL_TLSID.calloc(allocator)

            rect.x = -123
            assertEquals(-123, rect.ptr().x)
            rect.ptr().w = 640
            assertEquals(640, rect.w)

            surface.flags = 0xf1234567u
            assertEquals(0xf1234567u, surface.ptr().flags)
            surface.ptr().refcount = -7
            assertEquals(-7, surface.refcount)

            audioEvent.recording = true
            assertTrue(audioEvent.ptr().recording)
            audioEvent.ptr().recording = false
            assertFalse(audioEvent.recording)

            displayMode.format = 0x16161804
            assertEquals(0x16161804, displayMode.ptr().format)

            val pixels = NPointer<NChar>(arena.allocate(32).address())
            surface.pixels = pixels
            assertEquals(pixels, surface.ptr().pixels)
            surface.ptr().pixels = NPointer(0L)
            assertEquals(0L, surface.pixels._address)

            tls.value = 42
            assertEquals(42, tls.ptr().value)
            tls.ptr().value = -1
            assertEquals(-1, tls.value)
        }
    }

    @Test
    fun `nested record and inline array accessors are zero copy views`() {
        Arena.ofConfined().use { arena ->
            val allocator = arena.asAllocateScope()
            val haptic = SDL_HapticConstant.calloc(allocator)
            val guid = SDL_GUID.calloc(allocator)

            assertEquals(haptic._address + 4L, haptic.direction._address)
            assertEquals(haptic._address + 4L, haptic.ptr().direction._address)
            haptic.direction.type = 3u.toUByte()
            assertEquals(3u.toUByte(), haptic.ptr().direction.type)

            assertEquals(guid._address, guid.data._address)
            assertEquals(guid._address, guid.ptr().data._address)
            assertEquals(16L, guid.data.count)
            guid.data[0] = 0xabu.toUByte()
            guid.ptr().data[15] = 0xcdu.toUByte()
            assertEquals(0xabu.toUByte(), guid.ptr().data[0])
            assertEquals(0xcdu.toUByte(), guid.data[15])
        }
    }

    @Test
    fun `record inventory exposes the only partial layout and all unsupported fields`() {
        assertEquals(123, SDLRecordLayouts.records.size, "122 public records plus SDL_TLSID")
        assertEquals(27, SDLUnsupportedFields.fields.size)
        assertEquals(
            setOf("input", "output"),
            SDLUnsupportedFields.fields
                .filter { it.record == "SDL_GamepadBinding" }
                .mapTo(sortedSetOf(), SDLUnsupportedField::field),
        )
        assertTrue(
            SDLUnsupportedFields.fields
                .filterNot { it.record == "SDL_GamepadBinding" }
                .all { it.reason == "function pointer exposed as an untyped native pointer" },
        )
    }
}
