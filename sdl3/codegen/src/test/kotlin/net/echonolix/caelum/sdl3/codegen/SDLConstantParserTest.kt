package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SDLConstantParserTest {
    private val registry by lazy {
        SDLConstantParser.parse(Path.of("..", "include", "SDL3").toAbsolutePath().normalize())
    }

    @Test
    fun `pinned Windows x64 active macro manifest is exact`() {
        val definitions = SDLActiveMacroManifest.definitions

        assertEquals(1_148, definitions.size)
        assertEquals(890, definitions.count { it.name.startsWith("SDL_") })
        assertEquals(258, definitions.count { it.name.startsWith("SDLK_") })
        assertEquals(63, definitions.count { it.expression.isEmpty() })
        assertEquals(1_148, definitions.map(SDLActiveMacroDefinition::name).toSet().size)
        assertEquals(1_062, SDLActiveMacroManifest.publicCandidates.size)
    }

    @Test
    fun `every public active macro candidate is classified exactly once`() {
        val candidates = SDLActiveMacroManifest.publicCandidates.map(SDLActiveMacroDefinition::name).toSet()
        val classified = buildList {
            registry.entries.mapNotNullTo(this) { it.name.takeIf(candidates::contains) }
            registry.skipped.filter { it.source == SDLConstantSource.MACRO }
                .mapTo(this, SDLSkippedConstant::name)
        }

        assertEquals(candidates, classified.toSet())
        assertEquals(classified.size, classified.toSet().size)
        assertEquals(1_163, registry.enumCount)
        assertEquals(registry.entries.sortedBy(SDLGeneratedConstant::name), registry.entries)
        assertEquals(registry.entries.size, registry.entries.map(SDLGeneratedConstant::name).distinct().size)
        assertEquals(registry.skipped.size, registry.skipped.distinct().size)
    }

    @Test
    fun `manifest contains only active Windows platform markers`() {
        val activeNames = SDLActiveMacroManifest.definitions.map(SDLActiveMacroDefinition::name).toSet()
        val classifiedNames = registry.entries.map(SDLGeneratedConstant::name).toSet() +
            registry.skipped.map(SDLSkippedConstant::name)

        assertEquals(setOf("SDL_PLATFORM_WIN32", "SDL_PLATFORM_WINDOWS"), activeNames.filterTo(mutableSetOf()) {
            it.startsWith("SDL_PLATFORM_")
        })
        listOf(
            "SDL_PLATFORM_ANDROID",
            "SDL_PLATFORM_APPLE",
            "SDL_PLATFORM_EMSCRIPTEN",
            "SDL_PLATFORM_IOS",
            "SDL_PLATFORM_LINUX",
            "SDL_PLATFORM_MACOS",
            "SDL_PLATFORM_TVOS",
            "SDL_PLATFORM_WINGDK",
            "SDL_PLATFORM_XBOXONE",
            "SDL_PLATFORM_XBOXSERIES",
        ).forEach { assertFalse(it in classifiedNames, "$it must not be classified for Windows x64") }
    }

    @Test
    fun `representative constants preserve C values and Kotlin width`() {
        val values = registry.entries.associate { it.name to it.value }

        assertEquals(SDLConstantValue.SignedInteger(0x100, 32), values["SDL_EVENT_QUIT"])
        assertEquals(SDLConstantValue.SignedInteger(4, 32), values["SDL_SCANCODE_A"])
        assertEquals(SDLConstantValue.SignedInteger(0x8120, 32), values["SDL_AUDIO_F32"])
        assertEquals(SDLConstantValue.UnsignedInteger(0x20uL, 32), values["SDL_INIT_VIDEO"])
        assertEquals(SDLConstantValue.UnsignedInteger(0x8uL, 64), values["SDL_WINDOW_HIDDEN"])
        assertEquals(SDLConstantValue.UnsignedInteger(0x0300uL, 32), values["SDL_KMOD_ALT"])
        assertEquals(SDLConstantValue.UnsignedInteger(0x20000000uL, 32), values["SDLK_EXTENDED_MASK"])
        assertEquals(SDLConstantValue.UnsignedInteger(0x40000000uL, 32), values["SDLK_SCANCODE_MASK"])
        assertEquals(SDLConstantValue.UnsignedInteger(0x4000003auL, 32), values["SDLK_F1"])
        assertEquals(SDLConstantValue.UnsignedInteger(1uL, 32), values["SDL_BUTTON_LMASK"])
        assertEquals(SDLConstantValue.UnsignedInteger(16uL, 32), values["SDL_BUTTON_X2MASK"])
        assertEquals(SDLConstantValue.SignedInteger(3_004_014, 32), values["SDL_VERSION"])
        assertEquals(SDLConstantValue.UnsignedInteger(ULong.MAX_VALUE, 64), values["SDL_SIZE_MAX"])
        assertEquals(SDLConstantValue.SignedInteger(0x32315659, 32), values["SDL_PIXELFORMAT_YV12"])
        assertEquals(SDLConstantValue.FloatingPoint(9.80665, true), values["SDL_STANDARD_GRAVITY"])
        assertEquals(
            SDLConstantValue.StringValue("SDL.window.create.title"),
            values["SDL_PROP_WINDOW_CREATE_TITLE_STRING"],
        )
    }
}
