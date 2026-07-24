package net.echonolix.caelum.opengl.codegen

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenGLRegistryTest {
    @Test
    fun `pinned registry has expected digest`() {
        // Given
        val registryBytes = resource("/gl.xml")
        val recordedDigest = resource("/gl.xml.sha256").decodeToString().trim()

        // When
        val actualDigest = MessageDigest.getInstance("SHA-256")
            .digest(registryBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        // Then
        assertEquals(PINNED_DIGEST, recordedDigest)
        assertEquals(PINNED_DIGEST, actualDigest)
    }

    @Test
    fun `desktop core through 3_3 has the expected surface`() {
        // Given
        val input = resource("/gl.xml").inputStream()

        // When
        val registry = input.use(::parseOpenGLRegistry)

        // Then
        assertEquals(344, registry.commands.size)
        assertEquals(818, registry.enums.size)
        assertTrue("glBindVertexArray" in registry.commands)
        assertTrue("GL_VERTEX_SHADER" in registry.enums)
        assertFalse("glBegin" in registry.commands)
        assertFalse("GL_QUADS" in registry.enums)
    }

    @Test
    fun `aliases and 64 bit sentinels retain their registry meaning`() {
        // Given
        val input = resource("/gl.xml").inputStream()

        // When
        val registry = input.use(::parseOpenGLRegistry)
        val timeoutIgnored = assertNotNull(registry.enums["GL_TIMEOUT_IGNORED"])

        // Then
        assertEquals(15, registry.enums.values.count { it.alias != null })
        assertEquals(-1L, timeoutIgnored.value)
        assertEquals(64, timeoutIgnored.bitWidth)
    }

    @Test
    fun `external entities are rejected`() {
        // Given
        val xml = """
            <!DOCTYPE registry [
                <!ENTITY xxe SYSTEM "file:///definitely-not-readable">
            ]>
            <registry><comment>&xxe;</comment></registry>
        """.trimIndent()

        // When / Then
        assertFailsWith<IllegalArgumentException> {
            parseOpenGLRegistry(xml.byteInputStream())
        }
    }

    @Test
    fun `malformed registry relationships are rejected`() {
        // Given
        val malformed = mapOf(
            "missing command" to registryXml(
                commands = "",
                enums = "",
                requirements = """<command name="glMissing"/>""",
            ),
            "alias cycle" to registryXml(
                commands = "",
                enums = """
                    <enum name="GL_A" alias="GL_B"/>
                    <enum name="GL_B" alias="GL_A"/>
                """.trimIndent(),
                requirements = """
                    <enum name="GL_A"/>
                    <enum name="GL_B"/>
                """.trimIndent(),
            ),
            "unknown C type" to registryXml(
                commands = """
                    <command>
                        <proto>void <name>glBadType</name></proto>
                        <param><ptype>GLmystery</ptype> <name>value</name></param>
                    </command>
                """.trimIndent(),
                enums = "",
                requirements = """<command name="glBadType"/>""",
            ),
            "missing enum value" to registryXml(
                commands = "",
                enums = """<enum name="GL_MISSING_VALUE"/>""",
                requirements = """<enum name="GL_MISSING_VALUE"/>""",
            ),
            "conflicting enum" to registryXml(
                commands = "",
                enums = """
                    <enum name="GL_CONFLICT" value="1"/>
                    <enum name="GL_CONFLICT" value="2"/>
                """.trimIndent(),
                requirements = """<enum name="GL_CONFLICT"/>""",
            ),
        )

        // When / Then
        malformed.forEach { (case, xml) ->
            assertFailsWith<IllegalArgumentException>("Expected rejection for $case") {
                parseOpenGLRegistry(xml.byteInputStream())
            }
        }
    }

    private fun resource(path: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource $path" }
            .use { it.readAllBytes() }

    private fun registryXml(commands: String, enums: String, requirements: String): String = """
        <registry>
            <commands>$commands</commands>
            <enums>$enums</enums>
            <feature api="gl" name="GL_VERSION_1_0" number="1.0">
                <require>$requirements</require>
            </feature>
        </registry>
    """.trimIndent()

    private companion object {
        const val PINNED_DIGEST = "5e5ae64cad4dc4d4d3d122790086a0bb9df41eba53917d0b0fc93890c7c35499"
    }
}
