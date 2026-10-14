package net.echonolix.caelum.sdl3

import java.lang.foreign.Arena
import java.nio.file.Files
import java.nio.file.Path
import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.asAllocateScope
import net.echonolix.caelum.c_str
import net.echonolix.caelum.calloc
import net.echonolix.caelum.nullptr
import net.echonolix.caelum.string

public fun main() {
    check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "The SDL3 smoke test currently supports Windows only"
    }

    val configuredPath = requireNotNull(System.getProperty("sdl3Dll")) {
        "Missing sdl3Dll system property"
    }
    val sdl3Dll = Path.of(configuredPath)
    require(sdl3Dll.isAbsolute) { "sdl3Dll must be an absolute path: $sdl3Dll" }
    val normalizedDll = sdl3Dll.normalize()
    require(Files.isRegularFile(normalizedDll)) { "SDL3 DLL does not exist: $normalizedDll" }

    SDL.load(normalizedDll)
    REQUIRED_EXPORTS.forEach(SDL::findSymbol)

    var initCompleted = false
    var window: NPointer<SDL_Window> = nullptr()
    try {
        val version = SDL_GetVersion()
        val revisionPointer = SDL_GetRevision()
        check(revisionPointer._address != 0L) { "SDL_GetRevision returned null" }
        val revision = revisionPointer.string
        check(version / 1_000_000 == 3) { "Expected SDL major version 3, got ${formatVersion(version)}" }
        check(version >= MINIMUM_SMOKE_VERSION) {
            "Expected SDL 3.2.0 or newer, got ${formatVersion(version)}"
        }

        val guidRoundTrip = Arena.ofConfined().use { arena ->
            val guid = SDL_StringToGUID(arena, GUID_INPUT.c_str(arena.asAllocateScope()))
            val output = NPointer<NChar>(arena.allocate(GUID_BUFFER_SIZE.toLong(), 1L).address())
            SDL_GUIDToString(guid, output, GUID_BUFFER_SIZE)
            output.string
        }
        check(guidRoundTrip == GUID_CANONICAL) {
            "SDL GUID roundtrip mismatch: expected $GUID_CANONICAL, got $guidRoundTrip"
        }

        val initialized = SDL_Init(SDL_INIT_VIDEO)
        initCompleted = true
        check(initialized) { "SDL_Init failed: ${currentSdlError()}" }

        window = MemoryStack {
            SDL_CreateWindow(
                "caelum-sdl3 smoke".c_str(),
                64,
                64,
                SDL_WINDOW_HIDDEN,
            )
        }
        check(window._address != 0L) { "SDL_CreateWindow failed: ${currentSdlError()}" }

        // A false result only means the queue is currently empty; it is not an SDL error.
        val eventAvailable = MemoryStack {
            val event = SDL_Event.calloc()
            SDL_PollEvent(event.ptr())
        }

        println("SDL_VERSION=${formatVersion(version)}")
        println("SDL_REVISION=$revision")
        println("SDL_GUID=$guidRoundTrip")
        println("SDL_EVENT_AVAILABLE=$eventAvailable")
        println("SDL3_SMOKE_OK")
    } finally {
        try {
            if (window._address != 0L) {
                SDL_DestroyWindow(window)
            }
        } finally {
            if (initCompleted) {
                SDL_Quit()
            }
        }
    }
}

private fun NPointer<NChar>.readNullableString(): String =
    if (_address == 0L) "" else string

private fun currentSdlError(): String = SDL_GetError().readNullableString().ifBlank { "unknown error" }

private fun formatVersion(version: Int): String =
    "${version / 1_000_000}.${(version / 1_000) % 1_000}.${version % 1_000}"

private const val GUID_INPUT = "030000005E0400008E02000014010000"
private const val GUID_CANONICAL = "030000005e0400008e02000014010000"
private const val GUID_BUFFER_SIZE = 33
private const val MINIMUM_SMOKE_VERSION = 3_002_000
private val REQUIRED_EXPORTS = listOf(
    "SDL_CreateWindow",
    "SDL_DestroyWindow",
    "SDL_GetError",
    "SDL_GetRevision",
    "SDL_GetVersion",
    "SDL_GUIDToString",
    "SDL_Init",
    "SDL_PollEvent",
    "SDL_Quit",
    "SDL_StringToGUID",
)
