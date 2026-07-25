package net.echonolix.caelum.opengl.codegen

import org.junit.jupiter.api.assertAll
import org.w3c.dom.NodeList
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
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
    fun `pinned registry resolves the desktop core and extension union`() {
        // Given
        val input = resource("/gl.xml").inputStream()

        // When
        val registry = input.use(::parseOpenGLRegistry)

        // Then
        assertAll(
            { assertEquals(2624, registry.commands.size, "desktop command union") },
            { assertEquals(4670, registry.enums.size, "desktop enum union") },
            { assertTrue("glGetPointerv" in registry.commands, "GL_KHR_debug desktop command") },
            { assertTrue("GL_QUADS" in registry.enums, "extension-retained compatibility enum") },
            { assertFalse("glBegin" in registry.commands, "removed-only core command") },
            { assertFalse("glDebugMessageControlKHR" in registry.commands, "GLES-only KHR_debug command") },
            { assertFalse("GL_DEBUG_OUTPUT_KHR" in registry.enums, "GLES-only KHR_debug enum") },
        )
    }

    @Test
    fun `pinned registry declares every desktop feature and extension owner`() {
        // Given
        val xml = resource("/gl.xml")

        // When
        val owners = desktopOwnerNames(xml)
        val features = owners.filter { it.startsWith("GL_VERSION_") }
        val extensions = owners - features.toSet()

        // Then
        assertAll(
            { assertEquals(19, features.size, "desktop core feature count") },
            { assertEquals(623, extensions.size, "desktop extension count") },
            { assertEquals(owners.size, owners.toSet().size, "unique owner names") },
            { assertTrue("GL_VERSION_1_0" in features) },
            { assertTrue("GL_VERSION_4_6" in features) },
            { assertTrue("GL_KHR_debug" in extensions) },
            { assertTrue("GL_NV_texture_shader" in extensions) },
        )
    }

    @Test
    fun `registry ownership is complete deterministic and ABI aware`() {
        // Given / When
        val registry = resource("/gl.xml").inputStream().use(::parseOpenGLRegistry)
        val core = registry.owners.filter { it.name.startsWith("GL_VERSION_") }
        val extensions = registry.owners - core.toSet()
        val nvTextureShader = registry.owners.single { it.name == "GL_NV_texture_shader" }
        val getPointerOwner = registry.owners.single { "glGetPointerv" in it.declarationCommandNames }
        val createProgramObject = assertNotNull(registry.commands["glCreateProgramObjectARB"])

        // Then
        assertAll(
            { assertEquals(19, core.size) },
            { assertEquals(623, extensions.size) },
            { assertEquals(657, core.sumOf { it.declarationCommandNames.size }, "final core commands") },
            { assertEquals(1367, core.sumOf { it.declarationEnumNames.size }, "final core enums") },
            {
                assertEquals(
                    registry.commands.size,
                    registry.owners.sumOf { it.declarationCommandNames.size },
                    "each command has one declaration owner",
                )
            },
            {
                assertEquals(
                    registry.enums.size,
                    registry.owners.sumOf { it.declarationEnumNames.size },
                    "each enum has one declaration owner",
                )
            },
            { assertEquals("GL43.kt", getPointerOwner.fileName) },
            { assertEquals("GLNVTextureShader.kt", nvTextureShader.fileName) },
            { assertEquals(0, nvTextureShader.commandNames.size) },
            { assertEquals(73, nvTextureShader.enumNames.size) },
            { assertEquals(GlCarrier.LONG, createProgramObject.returnCarrier) },
            { assertEquals(GlAbi.GL_HANDLE_ARB, createProgramObject.returnAbi) },
        )
    }

    @Test
    fun `owner filenames preserve registry spelling and reject collisions`() {
        assertAll(
            { assertEquals("GL10.kt", openGlOwnerFileName("GL_VERSION_1_0")) },
            { assertEquals("GL3DFXTbuffer.kt", openGlOwnerFileName("GL_3DFX_tbuffer")) },
            { assertEquals("GLARBES2Compatibility.kt", openGlOwnerFileName("GL_ARB_ES2_compatibility")) },
            { assertEquals("GLEXTFramebufferSRGB.kt", openGlOwnerFileName("GL_EXT_framebuffer_sRGB")) },
            { assertEquals("GLNVTextureShader.kt", openGlOwnerFileName("GL_NV_texture_shader")) },
        )

        val collision = """
            <registry>
                <commands/>
                <enums/>
                <extensions>
                    <extension name="GL_EXT_test_case" supported="gl"/>
                    <extension name="GL_EXT_test__case" supported="gl"/>
                </extensions>
            </registry>
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            parseOpenGLRegistry(collision.byteInputStream())
        }

        val caseInsensitiveCollision = """
            <registry>
                <commands/>
                <enums/>
                <extensions>
                    <extension name="GL_EXT_case" supported="gl"/>
                    <extension name="GL_ext_case" supported="gl"/>
                </extensions>
            </registry>
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            parseOpenGLRegistry(caseInsensitiveCollision.byteInputStream())
        }
    }

    @Test
    fun `aliases and 64 bit sentinels retain their registry meaning`() {
        // Given
        val input = resource("/gl.xml").inputStream()

        // When
        val registry = input.use(::parseOpenGLRegistry)
        val timeoutIgnored = assertNotNull(registry.enums["GL_TIMEOUT_IGNORED"])
        val pointSizeRange = assertNotNull(registry.enums["GL_SMOOTH_POINT_SIZE_RANGE"])

        // Then
        assertEquals("GL_POINT_SIZE_RANGE", pointSizeRange.alias)
        assertEquals(0x0B12, pointSizeRange.value)
        assertEquals(-1L, timeoutIgnored.value)
        assertEquals(64, timeoutIgnored.bitWidth)
    }

    @Test
    fun `later core requirements reactivate previously removed members`() {
        // Given
        val xml = """
            <registry>
                <commands>
                    <command><proto>void <name>glRevived</name></proto></command>
                    <command><proto>void <name>glRetired</name></proto></command>
                </commands>
                <enums>
                    <enum name="GL_REVIVED" value="1"/>
                    <enum name="GL_RETIRED" value="2"/>
                </enums>
                <feature api="gl" name="GL_VERSION_3_0" number="3.0">
                    <require><command name="glRevived"/><enum name="GL_REVIVED"/></require>
                </feature>
                <feature api="gl" name="GL_VERSION_2_0" number="2.0">
                    <remove>
                        <command name="glRevived"/><command name="glRetired"/>
                        <enum name="GL_REVIVED"/><enum name="GL_RETIRED"/>
                    </remove>
                </feature>
                <feature api="gl" name="GL_VERSION_1_0" number="1.0">
                    <require>
                        <command name="glRevived"/><command name="glRetired"/>
                        <enum name="GL_REVIVED"/><enum name="GL_RETIRED"/>
                    </require>
                </feature>
            </registry>
        """.trimIndent()

        // When
        val registry = parseOpenGLRegistry(xml.byteInputStream())

        // Then
        assertAll(
            { assertTrue("glRevived" in registry.commands) },
            { assertTrue("GL_REVIVED" in registry.enums) },
            { assertFalse("glRetired" in registry.commands) },
            { assertFalse("GL_RETIRED" in registry.enums) },
        )
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

internal fun desktopOwnerNames(xml: ByteArray): List<String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(xml.inputStream())
    val xpath = XPathFactory.newInstance().newXPath()

    fun names(expression: String): List<String> {
        val nodes = xpath.evaluate(expression, document, XPathConstants.NODESET) as NodeList
        return List(nodes.length) { index -> nodes.item(index).attributes.getNamedItem("name").nodeValue }
    }

    return names("/registry/feature[@api='gl']") + names(
        "/registry/extensions/extension[" +
            "contains(concat('|', @supported, '|'), '|gl|') or " +
            "contains(concat('|', @supported, '|'), '|glcore|')]",
    )
}
