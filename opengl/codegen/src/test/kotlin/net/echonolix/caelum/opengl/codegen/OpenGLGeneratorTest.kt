package net.echonolix.caelum.opengl.codegen

import net.echonolix.ktgen.KtgenProcessor
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenGLGeneratorTest {
    @Test
    fun `generator writes byte identical trees`(@TempDir tempDir: Path) {
        // Given
        val registry = requireNotNull(javaClass.getResourceAsStream("/gl.xml"))
            .use(::parseOpenGLRegistry)
        val first = tempDir.resolve("first")
        val second = tempDir.resolve("second")

        // When
        OpenGLGenerator.generate(registry, first)
        OpenGLGenerator.generate(registry, second)

        // Then
        val firstFiles = relativeFiles(first)
        val secondFiles = relativeFiles(second)
        assertEquals(
            listOf(
                "net/echonolix/caelum/opengl/GL33.kt",
                "net/echonolix/caelum/opengl/GL33Bindings.kt",
            ),
            firstFiles,
        )
        assertEquals(firstFiles, secondFiles)
        firstFiles.forEach { relative ->
            assertContentEquals(
                Files.readAllBytes(first.resolve(relative)),
                Files.readAllBytes(second.resolve(relative)),
                relative,
            )
        }

        val api = Files.readString(first.resolve(firstFiles[0]))
        val bindings = Files.readString(first.resolve(firstFiles[1]))
        val generated = api + bindings
        assertEquals(818, Regex("""\bconst val GL_""").findAll(api).count())
        assertEquals(344, Regex("""(?m)^\s*@JvmStatic\s*$""").findAll(api).count())
        assertTrue("fun glBindVertexArray(" in api)
        assertTrue("fun glGetString(" in api)
        assertTrue("const val GL_VERTEX_SHADER" in api)
        assertFalse("fun glBegin(" in api)
        assertFalse(Regex("""\bconst val GL_QUADS\b""").containsMatchIn(api))
        assertFalse("findSymbol" in generated)
        assertFalse(Regex("""(?m)^\s*(?:internal|private|public)?\s*val\s+\w+\s*:\s*MethodHandle""").containsMatchIn(generated))
    }

    @Test
    fun `processor is discoverable as a ktgen service`(@TempDir tempDir: Path) {
        // Given
        val processorType = OpenGLCodegenProcessor::class

        // When
        val processors = ServiceLoader.load(KtgenProcessor::class.java).toList()
        val generated = processors.single { it::class == processorType }
            .process(emptySet(), tempDir)

        // Then
        assertEquals(
            listOf(
                "net/echonolix/caelum/opengl/GL33.kt",
                "net/echonolix/caelum/opengl/GL33Bindings.kt",
            ),
            generated.map { it.relativeTo(tempDir).toString().replace('\\', '/') }.sorted(),
        )
    }

    private fun relativeFiles(root: Path): List<String> =
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .map { it.relativeTo(root).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
}
