package net.echonolix.caelum.sdl3

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.UnionLayout
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NValue
import net.echonolix.caelum.sdl3.functions.SDL_EventFilter

class SDLBindingsTest {
    @Test
    fun `generated function inventory covers the SDL3 API`() {
        val names = SDLFunctions.names

        assertEquals(names.size, names.distinct().size, "generated function names must be unique")
        assertEquals(names.sorted(), names, "generated function order must be deterministic")
        assertEquals(names.size, SDLFunctions.descriptors.size)
        assertTrue(names.size >= 1_200, "expected at least 1,200 SDL3 functions, got ${names.size}")
        assertTrue(
            names.containsAll(
                listOf(
                    "SDL_GetVersion",
                    "SDL_GetRevision",
                    "SDL_GetError",
                    "SDL_Init",
                    "SDL_CreateWindow",
                    "SDL_PollEvent",
                    "SDL_DestroyWindow",
                    "SDL_Quit",
                ),
            ),
        )
        assertFalse("SDL_Log" in names, "variadic SDL_Log must not be emitted as a fixed-arity binding")
        assertFalse("SDL_SetError" in names, "variadic SDL_SetError must not be emitted as a fixed-arity binding")
        assertTrue(SDLFunctions.skipped.isNotEmpty(), "unsupported declarations must be reported explicitly")
    }

    @Test
    fun `C bool functions use the boolean carrier`() {
        assertEquals(ValueLayout.JAVA_BOOLEAN, descriptorFor("SDL_Init").returnLayout().orElseThrow())
        assertEquals(ValueLayout.JAVA_BOOLEAN, descriptorFor("SDL_PollEvent").returnLayout().orElseThrow())
    }

    @Test
    fun `verified aggregate functions use their record layouts`() {
        val aggregateFunctions = listOf(
            "SDL_GUIDToString",
            "SDL_GetGamepadGUIDForID",
            "SDL_GetGamepadMappingForGUID",
            "SDL_GetJoystickGUID",
            "SDL_GetJoystickGUIDForID",
            "SDL_GetJoystickGUIDInfo",
            "SDL_SetGPUBlendConstants",
            "SDL_StringToGUID",
        )

        assertTrue(SDLFunctions.names.containsAll(aggregateFunctions))
        assertEquals(SDL_GUID.layout, descriptorFor("SDL_StringToGUID").returnLayout().orElseThrow())
        assertEquals(SDL_GUID.layout, descriptorFor("SDL_GUIDToString").argumentLayouts().first())
        assertEquals(SDL_FColor.layout, descriptorFor("SDL_SetGPUBlendConstants").argumentLayouts()[1])
    }

    @Test
    fun `core constants preserve their unsigned SDL types`() {
        val initVideo: UInt = SDL_INIT_VIDEO
        val hiddenWindow: ULong = SDL_WINDOW_HIDDEN

        assertEquals(0x00000020u, initVideo)
        assertEquals(0x0000000000000008uL, hiddenWindow)
    }

    @Test
    fun `event filter callback preserves bool and pointer carriers`() {
        val callback: SDL_EventFilter = SDL_EventFilter { _: NPointer<*>, _: NPointer<SDL_Event> -> true }
        val invoke: (NPointer<*>, NPointer<SDL_Event>) -> Boolean = callback::invoke
        val descriptor = SDL_EventFilter.functionDescriptor
        val userdata: NPointer<NChar> = NPointer(0L)
        val event: NPointer<SDL_Event> = NPointer(0L)

        assertTrue(invoke(userdata, event))
        assertEquals(ValueLayout.JAVA_BOOLEAN, descriptor.returnLayout().orElseThrow())
        assertEquals(listOf(NPointer.layout, NPointer.layout), descriptor.argumentLayouts())
    }

    @Test
    fun `SDL Event preserves its fixed union ABI`() {
        assertTrue(SDL_Event.layout is UnionLayout)
        assertEquals(128L, SDL_Event.layout.byteSize())
        assertEquals(8L, SDL_Event.layout.byteAlignment())
    }

    @Test
    fun `SDL Rect has four contiguous native ints`() {
        val layout = SDL_Rect.layout

        assertEquals(16L, layout.byteSize())
        assertEquals(4L, layout.byteAlignment())
        assertEquals(0L, layout.byteOffset(groupElement("x")))
        assertEquals(4L, layout.byteOffset(groupElement("y")))
        assertEquals(8L, layout.byteOffset(groupElement("w")))
        assertEquals(12L, layout.byteOffset(groupElement("h")))
    }

    @Test
    fun `representative records preserve their Windows x64 ABI`() {
        assertLayout(
            SDL_Surface.layout,
            size = 48,
            alignment = 8,
            "flags" to 0,
            "format" to 4,
            "w" to 8,
            "h" to 12,
            "pitch" to 16,
            "pixels" to 24,
            "refcount" to 32,
            "reserved" to 40,
        )
        assertLayout(
            SDL_Texture.layout,
            size = 16,
            alignment = 4,
            "format" to 0,
            "w" to 4,
            "h" to 8,
            "refcount" to 12,
        )
        assertLayout(SDL_GUID.layout, size = 16, alignment = 1, "data" to 0)
        assertLayout(
            SDL_FColor.layout,
            size = 16,
            alignment = 4,
            "r" to 0,
            "g" to 4,
            "b" to 8,
            "a" to 12,
        )
        assertLayout(SDL_TLSID.layout, size = 4, alignment = 4, "value" to 0)
    }

    private fun descriptorFor(name: String) =
        SDLFunctions.descriptors[SDLFunctions.names.indexOf(name).also { index ->
            assertTrue(index >= 0, "missing generated descriptor for $name")
        }]

    private fun assertLayout(
        layout: MemoryLayout,
        size: Long,
        alignment: Long,
        vararg offsets: Pair<String, Int>,
    ) {
        assertEquals(size, layout.byteSize())
        assertEquals(alignment, layout.byteAlignment())
        offsets.forEach { (field, offset) ->
            assertEquals(offset.toLong(), layout.byteOffset(groupElement(field)), field)
        }
    }
}

// These declarations are deliberately never called. Compiling them protects the public raw API
// signatures without loading SDL3 during the normal test suite.
@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
private fun verifyRawApiSignatures() {
    val getVersion: () -> Int = ::SDL_GetVersion
    val getRevision: () -> NPointer<NChar> = ::SDL_GetRevision
    val init: (UInt) -> Boolean = ::SDL_Init
    val createWindow: (NPointer<NChar>, Int, Int, ULong) -> NPointer<SDL_Window> = ::SDL_CreateWindow
    val pollEvent: (NPointer<SDL_Event>) -> Boolean = ::SDL_PollEvent
    val destroyWindow: (NPointer<SDL_Window>) -> Unit = ::SDL_DestroyWindow
    val quit: () -> Unit = ::SDL_Quit
    val stringToGuid: (SegmentAllocator, NPointer<NChar>) -> NValue<SDL_GUID> = ::SDL_StringToGUID
    val guidToString: (NValue<SDL_GUID>, NPointer<NChar>, Int) -> Unit = ::SDL_GUIDToString
    val setGpuBlendConstants: (NPointer<SDL_GPURenderPass>, NValue<SDL_FColor>) -> Unit =
        ::SDL_SetGPUBlendConstants
}
