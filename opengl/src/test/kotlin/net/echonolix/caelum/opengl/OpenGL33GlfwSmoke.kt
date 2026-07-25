package net.echonolix.caelum.opengl

import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.c_str
import net.echonolix.caelum.glfw.consts.GLFW_CONTEXT_VERSION_MAJOR
import net.echonolix.caelum.glfw.consts.GLFW_CONTEXT_VERSION_MINOR
import net.echonolix.caelum.glfw.consts.GLFW_FALSE
import net.echonolix.caelum.glfw.consts.GLFW_OPENGL_CORE_PROFILE
import net.echonolix.caelum.glfw.consts.GLFW_OPENGL_PROFILE
import net.echonolix.caelum.glfw.consts.GLFW_VISIBLE
import net.echonolix.caelum.glfw.functions.glfwCreateWindow
import net.echonolix.caelum.glfw.functions.glfwDestroyWindow
import net.echonolix.caelum.glfw.functions.glfwGetProcAddress
import net.echonolix.caelum.glfw.functions.glfwInit
import net.echonolix.caelum.glfw.functions.glfwMakeContextCurrent
import net.echonolix.caelum.glfw.functions.glfwTerminate
import net.echonolix.caelum.glfw.functions.glfwWindowHint
import net.echonolix.caelum.glfw.structs.GLFWMonitor
import net.echonolix.caelum.glfw.structs.GLFWWindow
import net.echonolix.caelum.nullptr
import net.echonolix.caelum.string

public fun main() {
    val glfwDll = Path.of(requireNotNull(System.getProperty("glfwDll")))
        .toAbsolutePath()
        .normalize()
    require(glfwDll.toFile().isFile) { "GLFW DLL does not exist: $glfwDll" }
    System.load(glfwDll.toString())

    var initialized = false
    var window: NPointer<GLFWWindow> = nullptr()
    try {
        check(glfwInit() != GLFW_FALSE) { "glfwInit failed" }
        initialized = true

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        window = MemoryStack {
            glfwCreateWindow(
                64,
                64,
                "caelum-opengl smoke".c_str(),
                nullptr<GLFWMonitor>(),
                nullptr(),
            )
        }
        check(window._address != 0L) { "glfwCreateWindow failed" }
        glfwMakeContextCurrent(window)

        GL.createCapabilities { name ->
            MemoryStack { glfwGetProcAddress(name.c_str())._address }
        }

        val versionAddress = glGetString(GL_VERSION)
        check(versionAddress != 0L) { "glGetString(GL_VERSION) returned null" }
        val version = NPointer<NChar>(versionAddress).string
        check(version.isNotBlank()) { "OpenGL version is blank" }

        Arena.ofConfined().use { arena ->
            val vao = arena.allocate(ValueLayout.JAVA_INT)
            glGenVertexArrays(1, vao.address())
            glBindVertexArray(vao.get(ValueLayout.JAVA_INT, 0L))
            glClearColor(0.125f, 0.25f, 0.5f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glDeleteVertexArrays(1, vao.address())
        }

        val error = glGetError()
        println("OPENGL_VERSION=$version")
        println("GL_ERROR=$error")
        check(error == GL_NO_ERROR) { "OpenGL error: $error" }
        println("OPENGL33_SMOKE_OK")
    } finally {
        GL.setCapabilities(null)
        if (window._address != 0L) {
            glfwMakeContextCurrent(nullptr())
            glfwDestroyWindow(window)
        }
        if (initialized) {
            glfwTerminate()
        }
    }
}
