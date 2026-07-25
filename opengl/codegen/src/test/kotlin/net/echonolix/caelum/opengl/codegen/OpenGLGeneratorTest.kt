package net.echonolix.caelum.opengl.codegen

import net.echonolix.ktgen.KtgenProcessor
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenGLGeneratorTest {
    @Test
    fun `generator writes byte identical trees`(@TempDir tempDir: Path) {
        // Given
        val xml = resource("/gl.xml")
        val registry = xml.inputStream().use(::parseOpenGLRegistry)
        val first = tempDir.resolve("first")
        val second = tempDir.resolve("second")
        val expectedFiles = expectedGeneratedFiles(desktopOwnerNames(xml))

        // When
        OpenGLGenerator.generate(registry, first)
        OpenGLGenerator.generate(registry, second)
        val staleBinding = first.resolve("net/echonolix/caelum/opengl/GL33Bindings.kt")
        Files.writeString(staleBinding, "stale")
        OpenGLGenerator.generate(registry, first)

        // Then
        val firstFiles = relativeFiles(first)
        val secondFiles = relativeFiles(second)
        firstFiles.forEach { relative ->
            assertContentEquals(
                Files.readAllBytes(first.resolve(relative)),
                Files.readAllBytes(second.resolve(relative)),
                relative,
            )
        }

        val generated = firstFiles.joinToString("\n") { Files.readString(first.resolve(it)) }
        val commands = COMMAND_DECLARATION.findAll(generated).map { it.groupValues[1] }.toList()
        val enums = ENUM_DECLARATION.findAll(generated).map { it.groupValues[1] }.toList()
        assertAll(
            { assertEquals(643, firstFiles.size, "generated source file count") },
            { assertTrue(firstFiles.containsAll(expectedFiles), "one generated file per desktop owner") },
            { assertEquals(firstFiles, secondFiles, "generated relative paths") },
            { assertTrue("net/echonolix/caelum/opengl/GL10.kt" in firstFiles, "GL10.kt is generated") },
            { assertTrue("net/echonolix/caelum/opengl/GL46.kt" in firstFiles, "GL46.kt is generated") },
            {
                assertTrue(
                    "net/echonolix/caelum/opengl/GLNVTextureShader.kt" in firstFiles,
                    "extension acronyms map to GLNVTextureShader.kt",
                )
            },
            { assertTrue("net/echonolix/caelum/opengl/GLBindings.kt" in firstFiles, "shared GLBindings.kt is generated") },
            { assertEquals(2624, commands.size, "top-level command declarations") },
            { assertEquals(commands.size, commands.toSet().size, "unique command declarations") },
            { assertEquals(4670, enums.size, "top-level enum declarations") },
            { assertEquals(enums.size, enums.toSet().size, "unique enum declarations") },
            { assertTrue("glGetPointerv" in commands, "glGetPointerv is a top-level declaration") },
            { assertTrue("GL_QUADS" in enums, "GL_QUADS is a top-level declaration") },
            { assertFalse("glBegin" in commands, "glBegin remains excluded") },
            { assertFalse("glDebugMessageControlKHR" in commands, "GLES-only KHR command remains excluded") },
            { assertFalse("GL_DEBUG_OUTPUT_KHR" in enums, "GLES-only KHR enum remains excluded") },
            { assertFalse("object GL33" in generated, "object-era GL33 is removed") },
            { assertFalse("@JvmStatic" in generated, "top-level declarations need no @JvmStatic") },
            { assertFalse(Files.exists(staleBinding), "same-directory regeneration deletes stale bindings") },
            { assertFalse("findSymbol" in generated) },
            {
                assertFalse(
                    Regex("""(?m)^\s*(?:internal|private|public)?\s*val\s+\w+\s*:\s*MethodHandle""")
                        .containsMatchIn(generated),
                )
            },
        )
    }

    @Test
    fun `regeneration preserves unrelated Kotlin files`(@TempDir tempDir: Path) {
        // Given
        val registry = resource("/gl.xml").inputStream().use(::parseOpenGLRegistry)
        val packageDir = tempDir.resolve("net/echonolix/caelum/opengl")
        OpenGLGenerator.generate(registry, tempDir)
        val staleBinding = packageDir.resolve("GL33Bindings.kt")
        val sentinel = packageDir.resolve("UnrelatedSentinel.kt")
        Files.writeString(staleBinding, "legacy")
        Files.writeString(sentinel, "sentinel")

        // When
        OpenGLGenerator.generate(registry, tempDir)

        // Then
        assertFalse(Files.exists(staleBinding), "known object-era binding is deleted")
        assertEquals("sentinel", Files.readString(sentinel), "unrelated source is preserved")
    }

    @Test
    fun `generator rejects case insensitive relative path collisions`(@TempDir tempDir: Path) {
        // Given
        fun owner(name: String) = GlOwner(
            name = name,
            fileName = openGlOwnerFileName(name),
            commandNames = emptyList(),
            enumNames = emptyList(),
            declarationCommandNames = emptyList(),
            declarationEnumNames = emptyList(),
        )
        val registry = GlRegistry(
            commands = sortedMapOf(),
            enums = sortedMapOf(),
            owners = listOf(owner("GL_EXT_case"), owner("GL_ext_case")),
        )

        // When / Then
        assertFailsWith<IllegalArgumentException> {
            OpenGLGenerator.generate(registry, tempDir)
        }
    }

    @Test
    fun `filename oracle preserves acronyms and rejects collisions`() {
        // Given
        val owners = listOf(
            "GL_VERSION_1_0",
            "GL_VERSION_4_6",
            "GL_3DFX_tbuffer",
            "GL_ARB_ES2_compatibility",
            "GL_KHR_debug",
            "GL_NV_texture_shader",
        )

        // When
        val files = expectedGeneratedFiles(owners)

        // Then
        assertEquals(
            listOf(
                "net/echonolix/caelum/opengl/GL10.kt",
                "net/echonolix/caelum/opengl/GL46.kt",
                "net/echonolix/caelum/opengl/GL3DFXTbuffer.kt",
                "net/echonolix/caelum/opengl/GLARBES2Compatibility.kt",
                "net/echonolix/caelum/opengl/GLKHRDebug.kt",
                "net/echonolix/caelum/opengl/GLNVTextureShader.kt",
                "net/echonolix/caelum/opengl/GLBindings.kt",
            ),
            files,
        )
        assertFailsWith<IllegalArgumentException> {
            expectedGeneratedFiles(listOf("GL_EXT_test_case", "GL_EXT_test__case"))
        }
    }

    @Test
    fun `processor is discoverable as a ktgen service`(@TempDir tempDir: Path) {
        // Given
        val processorType = OpenGLCodegenProcessor::class

        // When
        val processors = ServiceLoader.load(KtgenProcessor::class.java).toList()
        val generated = processors.single { it::class == processorType }
            .process(emptySet(), tempDir)
        val expected = expectedGeneratedFiles(desktopOwnerNames(resource("/gl.xml")))

        // Then
        val actual = generated.map { it.relativeTo(tempDir).toString().replace('\\', '/') }.sorted()
        assertAll(
            { assertEquals(643, actual.size, "processor-generated source file count") },
            { assertTrue(actual.containsAll(expected), "processor emits every desktop owner") },
        )
    }

    private fun resource(path: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource $path" }
            .use { it.readAllBytes() }

    private fun relativeFiles(root: Path): List<String> =
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .map { it.relativeTo(root).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }

    private companion object {
        val COMMAND_DECLARATION = Regex("""(?m)^public fun (gl[A-Za-z0-9_]+)\(""")
        val ENUM_DECLARATION = Regex("""(?m)^public const val (GL_[A-Za-z0-9_]+)\b""")
    }
}

private fun expectedGeneratedFiles(owners: List<String>): List<String> {
    val files = owners.map { "net/echonolix/caelum/opengl/${ownerFileName(it)}" }
    require(files.size == files.toSet().size) { "OpenGL owner filename collision" }
    return files + "net/echonolix/caelum/opengl/GLBindings.kt"
}

private fun ownerFileName(owner: String): String {
    val version = Regex("""GL_VERSION_(\d+)_(\d+)""").matchEntire(owner)
    if (version != null) return "GL${version.groupValues[1]}${version.groupValues[2]}.kt"
    return owner.removePrefix("GL_")
        .split('_')
        .filter(String::isNotEmpty)
        .joinToString("", prefix = "GL", postfix = ".kt") { part ->
            if (part.all { it.isUpperCase() || it.isDigit() }) part else part.replaceFirstChar(Char::uppercase)
        }
}
