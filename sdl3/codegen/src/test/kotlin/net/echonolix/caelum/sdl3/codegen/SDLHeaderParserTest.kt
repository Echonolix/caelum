package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SDLHeaderParserTest {
    private val registry by lazy {
        SDLHeaderParser.parse(Path.of("..", "include", "SDL3").toAbsolutePath().normalize())
    }

    @Test
    fun `all declarations are classified deterministically`() {
        val classifiedNames = (registry.functions.map(SDLFunction::name) + registry.skipped.map(SDLSkipped::name))
        assertEquals(classifiedNames.size, classifiedNames.distinct().size)
        assertEquals(registry.functions.sortedBy(SDLFunction::name), registry.functions)
        assertEquals(registry.skipped.sortedBy(SDLSkipped::name), registry.skipped)
        assertTrue(registry.functions.size >= 1_200)
    }

    @Test
    fun `multiline and annotated declarations retain their ABI carriers`() {
        val createWindow = registry.functions.single { it.name == "SDL_CreateWindow" }
        assertEquals(SDLType.Scalar(SDLScalar.ULONG), createWindow.parameters.last().type)
        assertEquals(SDLType.Pointer("SDL_Window", 1), createWindow.returnType)

        val realloc = registry.functions.single { it.name == "SDL_realloc" }
        assertEquals(SDLType.Pointer(null, 1), realloc.returnType)

        val runApp = registry.functions.single { it.name == "SDL_RunApp" }
        assertEquals(SDLType.Pointer("NInt8", 2), runApp.parameters[1].type)

        val addEventWatch = registry.functions.single { it.name == "SDL_AddEventWatch" }
        assertEquals(SDLType.Pointer("SDL_EventFilter", 1), addEventWatch.parameters[0].type)

        val getEventFilter = registry.functions.single { it.name == "SDL_GetEventFilter" }
        assertEquals(SDLType.Pointer("SDL_EventFilter", 2), getEventFilter.parameters[0].type)

        val setMemoryFunctions = registry.functions.single { it.name == "SDL_SetMemoryFunctions" }
        assertEquals(
            listOf(
                SDLType.Pointer("SDL_malloc_func", 1),
                SDLType.Pointer("SDL_calloc_func", 1),
                SDLType.Pointer("SDL_realloc_func", 1),
                SDLType.Pointer("SDL_free_func", 1),
            ),
            setMemoryFunctions.parameters.map(SDLParameter::type),
        )

        val getMemoryFunctions = registry.functions.single { it.name == "SDL_GetMemoryFunctions" }
        assertTrue(getMemoryFunctions.parameters.all { (it.type as SDLType.Pointer).depth == 2 })

        val createThreadRuntime = registry.functions.single { it.name == "SDL_CreateThreadRuntime" }
        assertEquals(SDLType.Pointer("SDL_FunctionPointer", 1), createThreadRuntime.parameters[3].type)

        val localePreferences = registry.functions.single { it.name == "SDL_GetDateTimeLocalePreferences" }
        assertEquals(SDLType.Pointer("NInt", 1), localePreferences.parameters[0].type)
        assertEquals(SDLType.Pointer("NInt", 1), localePreferences.parameters[1].type)

        val wcslen = registry.functions.single { it.name == "SDL_wcslen" }
        assertEquals(SDLType.Pointer("NUInt16", 1), wcslen.parameters.single().type)

        val getSIMDAlignment = registry.functions.single { it.name == "SDL_GetSIMDAlignment" }
        assertEquals(SDLType.Scalar(SDLScalar.ULONG), getSIMDAlignment.returnType)

        val malloc = registry.functions.single { it.name == "SDL_malloc" }
        assertEquals(SDLType.Scalar(SDLScalar.ULONG), malloc.parameters.single().type)

        val reportAssertion = registry.functions.single { it.name == "SDL_ReportAssertion" }
        assertEquals(SDLType.Scalar(SDLScalar.INT), reportAssertion.returnType)

        val createContext = registry.functions.single { it.name == "SDL_GL_CreateContext" }
        assertEquals(SDLType.Pointer("SDL_GLContext", 1), createContext.returnType)

        val makeCurrent = registry.functions.single { it.name == "SDL_GL_MakeCurrent" }
        assertEquals(SDLType.Pointer("SDL_GLContext", 1), makeCurrent.parameters[1].type)

        assertEquals(
            SDLType.Pointer("SDL_EGLDisplay", 1),
            registry.functions.single { it.name == "SDL_EGL_GetCurrentDisplay" }.returnType,
        )
        assertEquals(
            SDLType.Pointer("SDL_EGLConfig", 1),
            registry.functions.single { it.name == "SDL_EGL_GetCurrentConfig" }.returnType,
        )
        assertEquals(
            SDLType.Pointer("SDL_EGLSurface", 1),
            registry.functions.single { it.name == "SDL_EGL_GetWindowSurface" }.returnType,
        )
        assertEquals(
            SDLType.Pointer("SDL_MetalView", 1),
            registry.functions.single { it.name == "SDL_Metal_CreateView" }.returnType,
        )

        val iconvOpen = registry.functions.single { it.name == "SDL_iconv_open" }
        assertEquals(SDLType.Pointer("SDL_iconv_t", 1), iconvOpen.returnType)
        val iconvClose = registry.functions.single { it.name == "SDL_iconv_close" }
        assertEquals(SDLType.Pointer("SDL_iconv_t", 1), iconvClose.parameters.single().type)
    }

    @Test
    fun `verified aggregate declarations retain their group carriers`() {
        val aggregateFunctions = listOf(
            "SDL_GUIDToString",
            "SDL_GetGamepadGUIDForID",
            "SDL_GetGamepadMappingForGUID",
            "SDL_GetJoystickGUID",
            "SDL_GetJoystickGUIDForID",
            "SDL_GetJoystickGUIDInfo",
            "SDL_SetGPUBlendConstants",
            "SDL_StringToGUID",
        )
        val functions = registry.functions.associateBy(SDLFunction::name)
        val skippedNames = registry.skipped.mapTo(mutableSetOf(), SDLSkipped::name)

        assertTrue(aggregateFunctions.all(functions::containsKey))
        assertTrue(aggregateFunctions.none(skippedNames::contains))
        assertEquals(SDLType.Aggregate("SDL_GUID"), functions.getValue("SDL_StringToGUID").returnType)
        assertEquals(SDLType.Aggregate("SDL_GUID"), functions.getValue("SDL_GUIDToString").parameters.first().type)
        assertEquals(
            SDLType.Aggregate("SDL_FColor"),
            functions.getValue("SDL_SetGPUBlendConstants").parameters.last().type,
        )
    }

    @Test
    fun `core constants preserve their public Kotlin types and values`() {
        val constants = registry.constants.associateBy(SDLConstant::name)

        assertEquals(SDLConstant(SDL_MAJOR_VERSION, SDLConstantKind.INT, 3uL), constants[SDL_MAJOR_VERSION])
        assertEquals(SDLConstant(SDL_MINOR_VERSION, SDLConstantKind.INT, 4uL), constants[SDL_MINOR_VERSION])
        assertEquals(SDLConstant(SDL_MICRO_VERSION, SDLConstantKind.INT, 14uL), constants[SDL_MICRO_VERSION])
        assertEquals(SDLConstant(SDL_INIT_VIDEO, SDLConstantKind.UINT, 0x20uL), constants[SDL_INIT_VIDEO])
        assertEquals(SDLConstant(SDL_WINDOW_HIDDEN, SDLConstantKind.ULONG, 0x8uL), constants[SDL_WINDOW_HIDDEN])
        assertEquals(SDLConstant(SDL_EVENT_QUIT, SDLConstantKind.INT, 0x100uL), constants[SDL_EVENT_QUIT])
    }

    @Test
    fun `unsupported ABI declarations are explicit`() {
        val skipped = registry.skipped.associate { it.name to it.reason }
        assertTrue(skipped.getValue("SDL_Log").contains("variadic"))
        assertTrue(skipped.getValue("SDL_SetErrorV").contains("va_list"))
        assertFalse("SDL_StringToGUID" in skipped)
        assertTrue(skipped.getValue("SDL_Vulkan_CreateSurface").contains("platform-specific"))
        assertTrue(skipped.getValue("SDL_CreateThread").contains("macro"))
        assertTrue(skipped.getValue("SDL_CreateThreadWithProperties").contains("macro"))
        assertFalse(registry.functions.any { it.name in skipped })
    }
}

private const val SDL_MAJOR_VERSION = "SDL_MAJOR_VERSION"
private const val SDL_MINOR_VERSION = "SDL_MINOR_VERSION"
private const val SDL_MICRO_VERSION = "SDL_MICRO_VERSION"
private const val SDL_INIT_VIDEO = "SDL_INIT_VIDEO"
private const val SDL_WINDOW_HIDDEN = "SDL_WINDOW_HIDDEN"
private const val SDL_EVENT_QUIT = "SDL_EVENT_QUIT"
