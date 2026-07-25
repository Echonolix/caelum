package net.echonolix.caelum.opengl

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
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
    fun `optional sentinel addresses create absent handles in the full table`() {
        // Given
        val sentinels = longArrayOf(-1L, 0L, 1L, 2L, 3L)
        var optionalIndex = 0

        // When
        val capabilities = GL.createCapabilities { functionName ->
            if (functionName == "glGetString") {
                4L
            } else {
                sentinels[optionalIndex++ % sentinels.size]
            }
        }

        // Then
        assertSame(capabilities, GL.getCapabilities())
        assertEquals(2624, capabilities.functions.size)
        assertEquals(1, capabilities.functions.filterNotNull().size)
    }

    @Test
    fun `missing glGetString fails without replacing prior capabilities`() {
        // Given
        val prior = GLCapabilities(emptyArray())
        GL.setCapabilities(prior)

        // When
        val failure = assertFailsWith<IllegalArgumentException> {
            GL.createCapabilities { 0L }
        }

        // Then
        assertSame(prior, GL.getCapabilities())
        assertContains(failure.message.orEmpty(), "glGetString")
        assertContains(failure.message.orEmpty(), "OpenGL context must be current")
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
    fun `valid provider requests every unique command once`() {
        // Given
        val requestedFunctions = mutableListOf<String>()
        val provider = GLFunctionProvider { functionName ->
            assertTrue(functionName.startsWith("gl"))
            requestedFunctions += functionName
            4L
        }

        // When
        val capabilities = GL.createCapabilities(provider)

        // Then
        assertSame(capabilities, GL.getCapabilities())
        assertEquals(2624, requestedFunctions.size)
        assertEquals(2624, requestedFunctions.toSet().size)
        assertEquals(requestedFunctions.sorted(), requestedFunctions)
        assertEquals(2624, capabilities.functions.size)
    }

    @Test
    fun `GLhandleARB descriptor uses platform raw carriers at masked positions`() {
        // Given
        val descriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_LONG,
        )
        val returnAndSecondParameterMask = 1L or (1L shl 2)

        // When
        val macDescriptor = glHandleArbFunctionDescriptor(descriptor, returnAndSecondParameterMask, true)
        val nonMacDescriptor = glHandleArbFunctionDescriptor(descriptor, returnAndSecondParameterMask, false)

        // Then
        assertEquals(ValueLayout.ADDRESS, macDescriptor.returnLayout().orElseThrow())
        assertEquals(listOf(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS), macDescriptor.argumentLayouts())
        assertEquals(ValueLayout.JAVA_INT, nonMacDescriptor.returnLayout().orElseThrow())
        assertEquals(listOf(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT), nonMacDescriptor.argumentLayouts())
    }

    @Test
    fun `GLhandleARB mac adapter exposes long arguments and return`() {
        // Given
        val raw = MethodHandles.dropArguments(
            MethodHandles.constant(MemorySegment::class.java, MemorySegment.ofAddress(9L)),
            0,
            MemorySegment::class.java,
        )

        // When
        val adapted = adaptGlHandleArbFunction(raw, 3L, true)

        // Then
        assertEquals("(long)long", adapted.type().toString())
        assertEquals(9L, adapted.invokeExact(11L) as Long)
    }

    @Test
    fun `GLhandleARB non-mac adapter exposes long arguments and return`() {
        // Given
        val raw = MethodHandles.dropArguments(
            MethodHandles.constant(Integer.TYPE, 7),
            0,
            Integer.TYPE,
        )

        // When
        val adapted = adaptGlHandleArbFunction(raw, 3L, false)

        // Then
        assertEquals("(long)long", adapted.type().toString())
        assertEquals(7L, adapted.invokeExact(11L) as Long)
    }

    @Test
    fun `macOS detection can exercise both platform branches`() {
        assertTrue(isMacOs("Mac OS X"))
        assertTrue(!isMacOs("Windows 11"))
    }

    @Test
    fun `calling an unavailable generated function names it`() {
        // Given
        GL.setCapabilities(GLCapabilities(arrayOfNulls(2624)))

        // When
        val failure = assertFailsWith<UnsupportedOperationException> {
            glCreateShader(GL_VERTEX_SHADER)
        }

        // Then
        assertContains(failure.message.orEmpty(), "glCreateShader")
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
