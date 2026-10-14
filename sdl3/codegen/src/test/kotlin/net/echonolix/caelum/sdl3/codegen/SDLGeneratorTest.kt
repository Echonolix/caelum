package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains

class SDLGeneratorTest {
    private val includeDir = Path.of("..", "include", "SDL3").toAbsolutePath().normalize()

    @Test
    fun `aggregate bindings use the Windows x64 FFM carriers`() {
        val registry = SDLHeaderParser.parse(includeDir)
        val outputDir = Files.createTempDirectory("caelum-sdl3-functions-")
        try {
            SDLGenerator.generate(registry, outputDir)

            val sources = Files.walk(outputDir).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                    .map { it.readText() }
                    .toList()
                    .joinToString("\n")
            }
            assertContains(sources, "functionDescriptorOf(SDL_GUID, NPointer)")
            assertContains(sources, "functionDescriptorOf(null, SDL_GUID, NPointer, NInt)")
            assertContains(sources, "functionDescriptorOf(null, NPointer, SDL_FColor)")
            assertContains(
                sources,
                "public fun SDL_StringToGUID(allocator: SegmentAllocator, pchGUID: NPointer<NInt8>): NValue<SDL_GUID>",
            )
            assertContains(
                sources,
                "MemorySegment.ofAddress(guid._address).reinterpret(SDL_GUID.layout.byteSize())",
            )
            assertContains(
                sources,
                "MemorySegment.ofAddress(blend_constants._address).reinterpret(SDL_FColor.layout.byteSize())",
            )
            assertContains(sources, "return NValue(result.address())")
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `size t bindings preserve unsigned Windows x64 carriers`() {
        val registry = SDLHeaderParser.parse(includeDir)
        val outputDir = Files.createTempDirectory("caelum-sdl3-size-t-")
        try {
            SDLGenerator.generate(registry, outputDir)

            val sources = Files.walk(outputDir).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                    .map { it.readText() }
                    .toList()
                    .joinToString("\n")
            }
            assertContains(sources, "functionDescriptorOf(NUInt64)")
            assertContains(sources, "public fun SDL_GetSIMDAlignment(): ULong")
            assertContains(sources, "public fun SDL_malloc(size: ULong): NPointer<*>")
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }
}
