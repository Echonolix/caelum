package net.echonolix.caelum.opengl.codegen

import net.echonolix.ktgen.KtgenProcessor
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

public class OpenGLCodegenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val xml = resource("/gl.xml").use { it.readAllBytes() }.canonicalLineEndings()
        val expectedHash = resource("/gl.xml.sha256").bufferedReader().use { it.readText().trim() }
        val actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml))
        require(actualHash == expectedHash) {
            "OpenGL registry SHA-256 mismatch: expected $expectedHash, found $actualHash"
        }
        val registry = ByteArrayInputStream(xml).use(::parseOpenGLRegistry)
        return OpenGLGenerator.generate(registry, outputDir)
    }

    private fun resource(name: String) =
        requireNotNull(javaClass.getResourceAsStream(name)) { "Missing OpenGL codegen resource $name" }
}

internal fun ByteArray.canonicalLineEndings(): ByteArray =
    decodeToString().replace("\r\n", "\n").encodeToByteArray()
