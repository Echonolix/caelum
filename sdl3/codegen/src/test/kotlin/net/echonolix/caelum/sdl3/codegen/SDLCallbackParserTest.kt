package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SDLCallbackParserTest {
    private val includeDir = Path.of("..", "include", "SDL3").toAbsolutePath().normalize()
    private val types by lazy { SDLHeaderParser.parse(includeDir).namedTypes }
    private val registry by lazy { SDLCallbackParser.parse(includeDir, types) }

    @Test
    fun `all public SDL callback typedefs are classified`() {
        val names = registry.callbacks.map(SDLCallback::name) + registry.unsupported.map(SDLUnsupportedCallback::name)

        assertEquals(40, names.size)
        assertEquals(names.size, names.distinct().size)
        assertEquals(40, registry.callbacks.size)
        assertTrue(registry.unsupported.isEmpty())
        assertEquals(
            listOf(
                SDLCallbackPointeeErasure(
                    "SDL_WindowsMessageHook",
                    "msg",
                    "MSG *",
                    "NPointer<*>",
                    "pointee layout is owned by platform headers and is not defined by SDL",
                ),
                SDLCallbackPointeeErasure(
                    "SDL_X11EventHook",
                    "xevent",
                    "XEvent *",
                    "NPointer<*>",
                    "pointee layout is owned by platform headers and is not defined by SDL",
                ),
            ),
            registry.erasures,
        )
    }

    @Test
    fun `callback signatures preserve scalar pointer and array carriers`() {
        val eventFilter = callback("SDL_EventFilter")
        assertEquals(SDLType.Scalar(SDLScalar.BOOL), eventFilter.returnType)
        assertEquals(SDLType.Pointer(null, 1), eventFilter.parameters[0].type)
        assertEquals(SDLType.Pointer("SDL_Event", 1), eventFilter.parameters[1].type)

        val appInit = callback("SDL_AppInit_func")
        assertEquals(SDLType.Pointer(null, 2), appInit.parameters[0].type)
        assertEquals(SDLType.Pointer("char", 2), appInit.parameters[2].type)

        val clipboard = callback("SDL_ClipboardDataCallback")
        assertEquals(SDLType.Pointer(null, 1), clipboard.returnType)
        assertEquals(SDLType.Pointer("NUInt64", 1), clipboard.parameters.last().type)

        val mouseMotion = callback("SDL_MouseMotionTransformCallback")
        assertEquals(SDLType.Pointer("NFloat", 1), mouseMotion.parameters[4].type)
        assertEquals(SDLType.Pointer("NFloat", 1), mouseMotion.parameters[5].type)

        val eglIntArray = callback("SDL_EGLIntArrayCallback")
        assertEquals(SDLType.Pointer("SDL_EGLDisplay", 1), eglIntArray.parameters[1].type)
        assertEquals(SDLType.Pointer("SDL_EGLConfig", 1), eglIntArray.parameters[2].type)

        val windowsMessage = callback("SDL_WindowsMessageHook")
        assertEquals(SDLType.Pointer(null, 1), windowsMessage.parameters[1].type)

        val x11Event = callback("SDL_X11EventHook")
        assertEquals(SDLType.Pointer(null, 1), x11Event.parameters[1].type)

        val functionPointer = callback("SDL_FunctionPointer")
        assertEquals(SDLType.Void, functionPointer.returnType)
        assertTrue(functionPointer.parameters.isEmpty())
    }

    private fun callback(name: String): SDLCallback = registry.callbacks.single { it.name == name }
}
