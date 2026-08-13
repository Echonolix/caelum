package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains

class SDLCallbackGeneratorTest {
    private val includeDir = Path.of("..", "include", "SDL3").toAbsolutePath().normalize()

    @Test
    fun `unsigned callback returns use complete carrier expressions`() {
        val types = SDLHeaderParser.parse(includeDir).namedTypes
        val registry = SDLCallbackParser.parse(includeDir, types)
        val outputDir = Files.createTempDirectory("caelum-sdl3-callbacks-")
        try {
            SDLCallbackGenerator.generate(registry, outputDir)

            val functionsDir = outputDir.resolve("net/echonolix/caelum/sdl3/functions")
            val timer = functionsDir.resolve("SDL_TimerCallback.kt").readText()
            assertContains(timer, "return result.toInt()")
            assertContains(timer, "return result.toUInt()")

            val nsTimer = functionsDir.resolve("SDL_NSTimerCallback.kt").readText()
            assertContains(nsTimer, "return result.toLong()")
            assertContains(nsTimer, "return result.toULong()")
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `wide character callback pointers stay unsigned`() {
        val types = SDLHeaderParser.parse(includeDir).namedTypes
        val parsed = SDLCallbackParser.parse(includeDir, types)
        val wideCallback = SDLCallback(
            name = "SDL_WideCallback",
            returnType = SDLType.Void,
            parameters = listOf(SDLParameter("text", SDLType.Pointer("wchar_t", 1))),
            declaration = "typedef void (SDLCALL *SDL_WideCallback)(const wchar_t *text);",
        )
        val registry = parsed.copy(callbacks = parsed.callbacks + wideCallback)
        val outputDir = Files.createTempDirectory("caelum-sdl3-wide-callback-")
        try {
            SDLCallbackGenerator.generate(registry, outputDir)

            val source = outputDir.resolve(
                "net/echonolix/caelum/sdl3/functions/SDL_WideCallback.kt",
            ).readText()
            assertContains(source, "text: NPointer<NUInt16>")
            assertContains(source, "NPointer.fromNativeData<NUInt16>(text)")
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `size t callback pointers stay unsigned`() {
        val types = SDLHeaderParser.parse(includeDir).namedTypes
        val registry = SDLCallbackParser.parse(includeDir, types)
        val outputDir = Files.createTempDirectory("caelum-sdl3-size-t-callback-")
        try {
            SDLCallbackGenerator.generate(registry, outputDir)

            val source = outputDir.resolve(
                "net/echonolix/caelum/sdl3/functions/SDL_ClipboardDataCallback.kt",
            ).readText()
            assertContains(source, "size: NPointer<NUInt64>")
            assertContains(source, "NPointer.fromNativeData<NUInt64>(size)")
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }
}
