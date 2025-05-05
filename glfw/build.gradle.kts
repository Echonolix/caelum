import buildsrc.convention.ElementType

plugins {
    id("buildsrc.convention.codegen-c")
}

codegenC {
    packageName.set("net.echonolix.caelum.glfw")
    val excludedConsts = setOf(
        "APIENTRY",
        "WINGDIAPI",
        "CALLBACK",
        "GLFWAPI",
        "GLAPIENTRY"
    )
    val typeDefRename = mapOf(
        "GLFWglproc" to "GLFWFuncPtrGLProc",
        "GLFWvkproc" to "GLFWFuncPtrVKProc",
        "GLFWallocatefun" to "GLFWFuncPtrAllocateCallback",
        "GLFWreallocatefun" to "GLFWFuncPtrReallocateCallback",
        "GLFWdeallocatefun" to "GLFWFuncPtrDeallocateCallback",
        "GLFWerrorfun" to "GLFWFuncPtrErrorCallback",
        "GLFWwindowposfun" to "GLFWFuncPtrWindowPosCallback",
        "GLFWwindowsizefun" to "GLFWFuncPtrWindowSizeCallback",
        "GLFWwindowclosefun" to "GLFWFuncPtrWindowCloseCallback",
        "GLFWwindowrefreshfun" to "GLFWFuncPtrWindowRefreshCallback",
        "GLFWwindowfocusfun" to "GLFWFuncPtrWindowFocusCallback",
        "GLFWwindowiconifyfun" to "GLFWFuncPtrWindowIconifyCallback",
        "GLFWwindowmaximizefun" to "GLFWFuncPtrWindowMaximizeCallback",
        "GLFWframebuffersizefun" to "GLFWFuncPtrFramebufferSizeCallback",
        "GLFWwindowcontentscalefun" to "GLFWFuncPtrWindowContentScaleCallback",
        "GLFWmousebuttonfun" to "GLFWFuncPtrMouseButtonCallback",
        "GLFWcursorposfun" to "GLFWFuncPtrCursorPosCallback",
        "GLFWcursorenterfun" to "GLFWFuncPtrCursorEnterCallback",
        "GLFWscrollfun" to "GLFWFuncPtrScrollCallback",
        "GLFWkeyfun" to "GLFWFuncPtrKeyCallback",
        "GLFWcharfun" to "GLFWFuncPtrCharCallback",
        "GLFWcharmodsfun" to "GLFWFuncPtrCharModsCallback",
        "GLFWdropfun" to "GLFWFuncPtrDropCallback",
        "GLFWmonitorfun" to "GLFWFuncPtrMonitorCallback",
        "GLFWjoystickfun" to "GLFWFuncPtrJoystickCallback"
    )
    val structRename = mapOf(
        "GLFWmonitor" to "GLFWMonitor",
        "GLFWwindow" to "GLFWWindow",
        "GLFWcursor" to "GLFWCursor",
        "GLFWvidmode" to "GLFWVidMode",
        "GLFWgammaramp" to "GLFWGammaRamp",
        "GLFWimage" to "GLFWImage",
        "GLFWgamepadstate" to "GLFWGamepadState",
        "GLFWallocator" to "GLFWAllocator"
    )
    elementMapper = block@ { type, name ->
        if (type == ElementType.CONST && name in excludedConsts) {
            return@block null
        }

        when (type) {
            ElementType.TYPEDEF -> typeDefRename[name]
            ElementType.FUNCTION -> "GLFWFunc${(name.removePrefix("glfw"))}"
            ElementType.STRUCT -> structRename[name]
            else -> name
        }
    }
}

dependencies {
    ktgenInput(project.layout.projectDirectory.dir("include").asFileTree)
}