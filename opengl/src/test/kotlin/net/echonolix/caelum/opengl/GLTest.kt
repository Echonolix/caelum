package net.echonolix.caelum.opengl

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GLTest {
    @AfterTest
    fun cleanup() {
        GL.setCapabilities(null)
    }

    @Test
    fun `get without capabilities describes the current thread`() {
        // Given
        GL.setCapabilities(null)

        // When
        val failure = assertFailsWith<IllegalStateException> {
            GL.getCapabilities()
        }

        // Then
        assertContains(failure.message.orEmpty(), Thread.currentThread().name)
        assertContains(failure.message.orEmpty(), "no current OpenGL capabilities")
    }

    @Test
    fun `create rejects invalid function addresses`() {
        listOf(0L, 1L, 2L, 3L, -1L).forEach { address ->
            // Given
            var functionName = ""

            // When
            val failure = assertFailsWith<IllegalArgumentException> {
                GL.createCapabilities { name ->
                    functionName = name
                    address
                }
            }

            // Then
            assertContains(failure.message.orEmpty(), functionName)
            assertContains(failure.message.orEmpty(), address.toString())
            assertContains(failure.message.orEmpty(), "OpenGL context must be current")
        }
    }

    @Test
    fun `failed create preserves prior capabilities`() {
        // Given
        val prior = GLCapabilities(emptyArray())
        GL.setCapabilities(prior)

        // When
        assertFailsWith<IllegalArgumentException> {
            GL.createCapabilities { 0L }
        }

        // Then
        assertSame(prior, GL.getCapabilities())
    }

    @Test
    fun `set and get switch selected capabilities`() {
        // Given
        val first = GLCapabilities(emptyArray())
        val second = GLCapabilities(emptyArray())

        // When
        GL.setCapabilities(first)

        // Then
        assertSame(first, GL.getCapabilities())

        // When
        GL.setCapabilities(second)

        // Then
        assertSame(second, GL.getCapabilities())
    }

    @Test
    fun `another thread has no capabilities`() {
        // Given
        GL.setCapabilities(GLCapabilities(emptyArray()))
        val outcome = AtomicReference<Result<GLCapabilities>>()

        // When
        Thread {
            outcome.set(runCatching(GL::getCapabilities))
        }.apply {
            start()
            join()
        }

        // Then
        assertIs<IllegalStateException>(outcome.get().exceptionOrNull())
    }

    @Test
    fun `successful provider creates and installs the full table`() {
        // Given
        var requestedFunctions = 0
        val provider = GLFunctionProvider { functionName ->
            assertTrue(functionName.startsWith("gl"))
            requestedFunctions++
            4L
        }

        // When
        val capabilities = GL.createCapabilities(provider)

        // Then
        assertSame(capabilities, GL.getCapabilities())
        assertEquals(344, requestedFunctions)
        assertEquals(344, capabilities.functions.size)
    }

    @Test
    fun `setting null removes current thread capabilities`() {
        // Given
        GL.setCapabilities(GLCapabilities(emptyArray()))

        // When
        GL.setCapabilities(null)

        // Then
        assertFailsWith<IllegalStateException> {
            GL.getCapabilities()
        }
    }
}
