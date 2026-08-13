package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SDLTypeGeneratorTest {
    private val includeDir = Path.of("..", "include", "SDL3").toAbsolutePath().normalize()

    @Test
    fun `generator emits typed accessors views and unsupported inventory`() {
        val registry = SDLHeaderParser.parse(includeDir)
        val outputDir = Files.createTempDirectory("caelum-sdl3-types-")
        try {
            val outputs = SDLTypeGenerator.generate(registry, outputDir)
            assertEquals(setOf("SDLTypes.kt", "SDLTypeAccessors.kt"), outputs.mapTo(sortedSetOf()) { it.fileName.toString() })

            val types = outputs.single { it.fileName.toString() == "SDLTypes.kt" }.readText()
            val accessors = outputs.single { it.fileName.toString() == "SDLTypeAccessors.kt" }.readText()

            assertContains(types, "public interface SDL_DisplayModeData : NStruct")
            assertContains(types, "public interface SDL_GLContext : NStruct")
            assertContains(types, "public interface SDL_EGLDisplay : NStruct")
            assertContains(types, "public interface SDL_EGLConfig : NStruct")
            assertContains(types, "public interface SDL_EGLSurface : NStruct")
            assertContains(types, "public interface SDL_MetalView : NStruct")
            assertContains(types, "public interface SDL_iconv_t : NStruct")
            assertContains(types, "public val unsupportedReason: String? = null")
            assertContains(accessors, "public var NValue<SDL_Rect>.x: Int")
            assertContains(accessors, "public var NPointer<SDL_Surface>.pixels: NPointer<NChar>")
            assertContains(accessors, "public val NValue<SDL_GUID>.data: NArray<NUInt8>")
            assertContains(accessors, "public val NValue<SDL_HapticConstant>.direction: NValue<SDL_HapticDirection>")
            assertContains(accessors, "public var NValue<SDL_GPUTextureCreateInfo>.width: UInt")
            assertContains(accessors, "public var NValue<SDL_GPUVertexInputState>.vertex_buffer_descriptions")
            assertContains(accessors, "NPointer<NPointer<SDL_GPUTexture>>")
            assertContains(accessors, "SDLUnsupportedField(\"SDL_GamepadBinding\", \"input\"")
            assertContains(accessors, "function pointer exposed as an untyped native pointer")
            assertFalse("NPointer<char>" in accessors)
            assertFalse(Regex("[A-Za-z]:[\\\\/]").containsMatchIn(types + accessors))
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `layout snapshot tool obtains its own Clang AST`() {
        val script = Path.of("tools", "generate-sdl-type-layouts.mjs").readText()

        assertContains(script, "-ast-dump=json")
        assertContains(script, "execFileSync")
        assertFalse("sdl-ast.json" in script)
    }
}
