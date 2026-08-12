package net.echonolix.caelum.opengl.demo

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NInt
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.c_str
import net.echonolix.caelum.glfw.consts.GLFW_CONTEXT_VERSION_MAJOR
import net.echonolix.caelum.glfw.consts.GLFW_CONTEXT_VERSION_MINOR
import net.echonolix.caelum.glfw.consts.GLFW_FALSE
import net.echonolix.caelum.glfw.consts.GLFW_KEY_ESCAPE
import net.echonolix.caelum.glfw.consts.GLFW_OPENGL_CORE_PROFILE
import net.echonolix.caelum.glfw.consts.GLFW_OPENGL_FORWARD_COMPAT
import net.echonolix.caelum.glfw.consts.GLFW_OPENGL_PROFILE
import net.echonolix.caelum.glfw.consts.GLFW_PRESS
import net.echonolix.caelum.glfw.consts.GLFW_SAMPLES
import net.echonolix.caelum.glfw.consts.GLFW_TRUE
import net.echonolix.caelum.glfw.consts.GLFW_VISIBLE
import net.echonolix.caelum.glfw.functions.glfwCreateWindow
import net.echonolix.caelum.glfw.functions.glfwDestroyWindow
import net.echonolix.caelum.glfw.functions.glfwGetFramebufferSize
import net.echonolix.caelum.glfw.functions.glfwGetKey
import net.echonolix.caelum.glfw.functions.glfwGetProcAddress
import net.echonolix.caelum.glfw.functions.glfwGetTime
import net.echonolix.caelum.glfw.functions.glfwInit
import net.echonolix.caelum.glfw.functions.glfwMakeContextCurrent
import net.echonolix.caelum.glfw.functions.glfwPollEvents
import net.echonolix.caelum.glfw.functions.glfwSetWindowShouldClose
import net.echonolix.caelum.glfw.functions.glfwSetWindowTitle
import net.echonolix.caelum.glfw.functions.glfwSwapBuffers
import net.echonolix.caelum.glfw.functions.glfwSwapInterval
import net.echonolix.caelum.glfw.functions.glfwTerminate
import net.echonolix.caelum.glfw.functions.glfwWindowHint
import net.echonolix.caelum.glfw.functions.glfwWindowShouldClose
import net.echonolix.caelum.glfw.structs.GLFWMonitor
import net.echonolix.caelum.glfw.structs.GLFWWindow
import net.echonolix.caelum.nullptr
import net.echonolix.caelum.opengl.GL
import net.echonolix.caelum.opengl.GL_ARRAY_BUFFER
import net.echonolix.caelum.opengl.GL_COLOR_BUFFER_BIT
import net.echonolix.caelum.opengl.GL_COMPILE_STATUS
import net.echonolix.caelum.opengl.GL_DEPTH_BUFFER_BIT
import net.echonolix.caelum.opengl.GL_DEPTH_TEST
import net.echonolix.caelum.opengl.GL_FALSE
import net.echonolix.caelum.opengl.GL_FLOAT
import net.echonolix.caelum.opengl.GL_FRAGMENT_SHADER
import net.echonolix.caelum.opengl.GL_INFO_LOG_LENGTH
import net.echonolix.caelum.opengl.GL_LINK_STATUS
import net.echonolix.caelum.opengl.GL_MULTISAMPLE
import net.echonolix.caelum.opengl.GL_NO_ERROR
import net.echonolix.caelum.opengl.GL_RENDERER
import net.echonolix.caelum.opengl.GL_RGBA
import net.echonolix.caelum.opengl.GL_STATIC_DRAW
import net.echonolix.caelum.opengl.GL_TRIANGLES
import net.echonolix.caelum.opengl.GL_UNSIGNED_BYTE
import net.echonolix.caelum.opengl.GL_VENDOR
import net.echonolix.caelum.opengl.GL_VERSION
import net.echonolix.caelum.opengl.GL_VERTEX_SHADER
import net.echonolix.caelum.opengl.glAttachShader
import net.echonolix.caelum.opengl.glBindBuffer
import net.echonolix.caelum.opengl.glBindVertexArray
import net.echonolix.caelum.opengl.glBufferData
import net.echonolix.caelum.opengl.glClear
import net.echonolix.caelum.opengl.glClearColor
import net.echonolix.caelum.opengl.glCompileShader
import net.echonolix.caelum.opengl.glCreateProgram
import net.echonolix.caelum.opengl.glCreateShader
import net.echonolix.caelum.opengl.glDeleteBuffers
import net.echonolix.caelum.opengl.glDeleteProgram
import net.echonolix.caelum.opengl.glDeleteShader
import net.echonolix.caelum.opengl.glDeleteVertexArrays
import net.echonolix.caelum.opengl.glDrawArrays
import net.echonolix.caelum.opengl.glEnable
import net.echonolix.caelum.opengl.glEnableVertexAttribArray
import net.echonolix.caelum.opengl.glGenBuffers
import net.echonolix.caelum.opengl.glGenVertexArrays
import net.echonolix.caelum.opengl.glGetError
import net.echonolix.caelum.opengl.glGetProgramInfoLog
import net.echonolix.caelum.opengl.glGetProgramiv
import net.echonolix.caelum.opengl.glGetShaderInfoLog
import net.echonolix.caelum.opengl.glGetShaderiv
import net.echonolix.caelum.opengl.glGetString
import net.echonolix.caelum.opengl.glGetUniformLocation
import net.echonolix.caelum.opengl.glLinkProgram
import net.echonolix.caelum.opengl.glReadPixels
import net.echonolix.caelum.opengl.glShaderSource
import net.echonolix.caelum.opengl.glUniform3f
import net.echonolix.caelum.opengl.glUniformMatrix4fv
import net.echonolix.caelum.opengl.glUseProgram
import net.echonolix.caelum.opengl.glVertexAttribPointer
import net.echonolix.caelum.opengl.glViewport
import net.echonolix.caelum.string

private const val WINDOW_WIDTH = 960
private const val WINDOW_HEIGHT = 720
private const val FLOATS_PER_VERTEX = 6
private const val VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES
private const val CLEAR_RED = 0.025f
private const val CLEAR_GREEN = 0.04f
private const val CLEAR_BLUE = 0.075f

public fun main() {
    val glfwDll = Path.of(
        requireNotNull(System.getProperty("glfwDll").takeUnless(String::isBlank)) {
            "Pass -PglfwDll=<absolute path to the GLFW native library>"
        },
    ).toAbsolutePath().normalize()
    require(glfwDll.toFile().isFile) { "GLFW native library does not exist: $glfwDll" }

    val durationSeconds = System.getProperty("caelum.demo.seconds", "0").toDouble()
    require(durationSeconds >= 0.0) { "caelum.demo.seconds must be zero or positive" }
    val hidden = System.getProperty("caelum.demo.hidden", "false").toBooleanStrict()

    System.load(glfwDll.toString())

    var initialized = false
    var window: NPointer<GLFWWindow> = nullptr()
    var renderer: TeapotRenderer? = null
    try {
        check(glfwInit() != GLFW_FALSE) { "glfwInit failed" }
        initialized = true

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_SAMPLES, 4)
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        }
        if (hidden) {
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        }

        window = MemoryStack {
            glfwCreateWindow(
                WINDOW_WIDTH,
                WINDOW_HEIGHT,
                "Caelum OpenGL - Lit Teapot".c_str(),
                nullptr<GLFWMonitor>(),
                nullptr(),
            )
        }
        check(window._address != 0L) { "glfwCreateWindow failed for an OpenGL 3.3 core context" }

        glfwMakeContextCurrent(window)
        glfwSwapInterval(if (hidden) 0 else 1)
        GL.createCapabilities { functionName ->
            MemoryStack { glfwGetProcAddress(functionName.c_str())._address }
        }

        println("OPENGL_VERSION=${openGlString(GL_VERSION)}")
        println("OPENGL_RENDERER=${openGlString(GL_RENDERER)}")
        println("OPENGL_VENDOR=${openGlString(GL_VENDOR)}")

        renderer = TeapotRenderer()
        println("TEAPOT_VERTICES=${renderer.vertexCount}")
        if (!hidden) {
            println("Press ESC or close the window to exit.")
        }

        Arena.ofConfined().use { arena ->
            val framebufferWidth = arena.allocate(ValueLayout.JAVA_INT)
            val framebufferHeight = arena.allocate(ValueLayout.JAVA_INT)
            val centerPixel = arena.allocate(4L, 1L)
            val widthPointer = NPointer<NInt>(framebufferWidth.address())
            val heightPointer = NPointer<NInt>(framebufferHeight.address())
            val startTime = glfwGetTime()
            var lastTitleUpdate = startTime
            var framesSinceTitleUpdate = 0
            var totalFrames = 0
            var centerPixelChecked = false

            while (glfwWindowShouldClose(window) == GLFW_FALSE) {
                glfwPollEvents()
                if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
                    glfwSetWindowShouldClose(window, GLFW_TRUE)
                }

                val now = glfwGetTime()
                if (durationSeconds > 0.0 && totalFrames > 0 && now - startTime >= durationSeconds) {
                    glfwSetWindowShouldClose(window, GLFW_TRUE)
                    continue
                }

                glfwGetFramebufferSize(window, widthPointer, heightPointer)
                val width = framebufferWidth.get(ValueLayout.JAVA_INT, 0L)
                val height = framebufferHeight.get(ValueLayout.JAVA_INT, 0L)
                if (width > 0 && height > 0) {
                    renderer.render(width, height, (now - startTime).toFloat())
                    if (!centerPixelChecked) {
                        verifyCenterPixel(width, height, centerPixel)
                        centerPixelChecked = true
                    }
                    glfwSwapBuffers(window)
                    totalFrames++
                    framesSinceTitleUpdate++
                }

                val titleInterval = now - lastTitleUpdate
                if (!hidden && titleInterval >= 1.0) {
                    val framesPerSecond = framesSinceTitleUpdate / titleInterval
                    MemoryStack {
                        glfwSetWindowTitle(
                            window,
                            "Caelum OpenGL - Lit Teapot | %.0f FPS".format(framesPerSecond).c_str(),
                        )
                    }
                    framesSinceTitleUpdate = 0
                    lastTitleUpdate = now
                }
            }

            check(centerPixelChecked) { "The demo closed before rendering a frame" }
            val error = glGetError()
            println("GL_ERROR=$error")
            check(error == GL_NO_ERROR) { "OpenGL error after rendering: $error" }
            println("RENDERED_FRAMES=$totalFrames")
            println("CAELUM_TEAPOT_DEMO_OK")
        }
    } finally {
        renderer?.close()
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

private fun openGlString(name: Int): String {
    val address = glGetString(name)
    check(address != 0L) { "glGetString($name) returned null" }
    return NPointer<NChar>(address).string
}

private fun verifyCenterPixel(width: Int, height: Int, pixel: MemorySegment) {
    glReadPixels(width / 2, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel.address())
    val red = pixel.getAtIndex(ValueLayout.JAVA_BYTE, 0L).toInt() and 0xff
    val green = pixel.getAtIndex(ValueLayout.JAVA_BYTE, 1L).toInt() and 0xff
    val blue = pixel.getAtIndex(ValueLayout.JAVA_BYTE, 2L).toInt() and 0xff
    val backgroundDistance =
        abs(red - (CLEAR_RED * 255).toInt()) +
            abs(green - (CLEAR_GREEN * 255).toInt()) +
            abs(blue - (CLEAR_BLUE * 255).toInt())
    println("CENTER_PIXEL=$red,$green,$blue")
    check(backgroundDistance > 24) {
        "The center pixel still matches the clear color; the teapot draw was not visible"
    }
}

private class TeapotRenderer : AutoCloseable {
    private val arena = Arena.ofConfined()
    private val modelMemory = arena.allocate(16L * Float.SIZE_BYTES, ValueLayout.JAVA_FLOAT.byteAlignment())
    private val mvpMemory = arena.allocate(16L * Float.SIZE_BYTES, ValueLayout.JAVA_FLOAT.byteAlignment())
    private val cameraPosition = Vec3(0.25f, 0.45f, 7.4f)
    private val view = Mat4.lookAt(cameraPosition, Vec3(0f, 0.15f, 0f), Vec3(0f, 1f, 0f))

    private var vertexArray = 0
    private var vertexBuffer = 0
    private var program = 0
    private var closed = false

    val vertexCount: Int
    private val modelLocation: Int
    private val mvpLocation: Int
    private val lightPositionLocation: Int
    private val cameraPositionLocation: Int
    private val baseColorLocation: Int

    init {
        val vertices = createTeapotVertices()
        vertexCount = vertices.size / FLOATS_PER_VERTEX
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        Arena.ofConfined().use { uploadArena ->
            val objectName = uploadArena.allocate(ValueLayout.JAVA_INT)
            glGenVertexArrays(1, objectName.address())
            vertexArray = objectName.get(ValueLayout.JAVA_INT, 0L)
            glGenBuffers(1, objectName.address())
            vertexBuffer = objectName.get(ValueLayout.JAVA_INT, 0L)

            val vertexMemory = uploadArena.allocate(
                vertices.size.toLong() * Float.SIZE_BYTES,
                ValueLayout.JAVA_FLOAT.byteAlignment(),
            )
            vertices.forEachIndexed { index, value ->
                vertexMemory.setAtIndex(ValueLayout.JAVA_FLOAT, index.toLong(), value)
            }

            glBindVertexArray(vertexArray)
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
            glBufferData(
                GL_ARRAY_BUFFER,
                vertices.size.toLong() * Float.SIZE_BYTES,
                vertexMemory.address(),
                GL_STATIC_DRAW,
            )
            glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_STRIDE_BYTES, 0L)
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(1, 3, GL_FLOAT, false, VERTEX_STRIDE_BYTES, 3L * Float.SIZE_BYTES)
            glEnableVertexAttribArray(1)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)

            modelLocation = uniformLocation(program, "uModel", uploadArena)
            mvpLocation = uniformLocation(program, "uMvp", uploadArena)
            lightPositionLocation = uniformLocation(program, "uLightPosition", uploadArena)
            cameraPositionLocation = uniformLocation(program, "uCameraPosition", uploadArena)
            baseColorLocation = uniformLocation(program, "uBaseColor", uploadArena)
        }
        check(vertexArray != 0 && vertexBuffer != 0) { "OpenGL did not create the teapot buffers" }
        check(glGetError() == GL_NO_ERROR) { "OpenGL error while setting up the teapot renderer" }

        glEnable(GL_DEPTH_TEST)
        glEnable(GL_MULTISAMPLE)
    }

    fun render(width: Int, height: Int, timeSeconds: Float) {
        val model = Mat4.rotationY(timeSeconds * 0.48f)
        val projection = Mat4.perspective(
            (42.0 * PI / 180.0).toFloat(),
            width.toFloat() / height.toFloat(),
            0.1f,
            40f,
        )
        val mvp = Mat4.multiply(projection, Mat4.multiply(view, model))
        writeMatrix(modelMemory, model)
        writeMatrix(mvpMemory, mvp)

        glViewport(0, 0, width, height)
        glClearColor(CLEAR_RED, CLEAR_GREEN, CLEAR_BLUE, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUseProgram(program)
        glUniformMatrix4fv(modelLocation, 1, false, modelMemory.address())
        glUniformMatrix4fv(mvpLocation, 1, false, mvpMemory.address())
        glUniform3f(lightPositionLocation, 4.2f, 5.2f, 5.5f)
        glUniform3f(
            cameraPositionLocation,
            cameraPosition.x,
            cameraPosition.y,
            cameraPosition.z,
        )
        glUniform3f(baseColorLocation, 0.055f, 0.55f, 0.46f)
        glBindVertexArray(vertexArray)
        glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        glBindVertexArray(0)
        glUseProgram(0)
    }

    override fun close() {
        if (closed) return
        closed = true
        Arena.ofConfined().use { deleteArena ->
            val objectName = deleteArena.allocate(ValueLayout.JAVA_INT)
            if (vertexBuffer != 0) {
                objectName.set(ValueLayout.JAVA_INT, 0L, vertexBuffer)
                glDeleteBuffers(1, objectName.address())
            }
            if (vertexArray != 0) {
                objectName.set(ValueLayout.JAVA_INT, 0L, vertexArray)
                glDeleteVertexArrays(1, objectName.address())
            }
        }
        if (program != 0) {
            glDeleteProgram(program)
        }
        arena.close()
    }
}

private fun writeMatrix(memory: MemorySegment, matrix: FloatArray) {
    matrix.forEachIndexed { index, value ->
        memory.setAtIndex(ValueLayout.JAVA_FLOAT, index.toLong(), value)
    }
}

private fun uniformLocation(program: Int, name: String, arena: Arena): Int {
    val location = glGetUniformLocation(program, arena.utf8(name).address())
    check(location >= 0) { "Shader uniform was not found: $name" }
    return location
}

private fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource)
    val fragmentShader = try {
        compileShader(GL_FRAGMENT_SHADER, fragmentSource)
    } catch (failure: Throwable) {
        glDeleteShader(vertexShader)
        throw failure
    }

    val program = glCreateProgram()
    check(program != 0) { "glCreateProgram returned zero" }
    try {
        glAttachShader(program, vertexShader)
        glAttachShader(program, fragmentShader)
        glLinkProgram(program)
        Arena.ofConfined().use { arena ->
            val status = arena.allocate(ValueLayout.JAVA_INT)
            glGetProgramiv(program, GL_LINK_STATUS, status.address())
            check(status.get(ValueLayout.JAVA_INT, 0L) != GL_FALSE) {
                "Program link failed:\n${programInfoLog(program, arena)}"
            }
        }
        return program
    } catch (failure: Throwable) {
        glDeleteProgram(program)
        throw failure
    } finally {
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }
}

private fun compileShader(type: Int, source: String): Int {
    val shader = glCreateShader(type)
    check(shader != 0) { "glCreateShader returned zero for type $type" }
    try {
        Arena.ofConfined().use { arena ->
            val sourceMemory = arena.utf8(source)
            val sourcePointer = arena.allocate(ValueLayout.ADDRESS)
            sourcePointer.set(ValueLayout.ADDRESS, 0L, sourceMemory)
            glShaderSource(shader, 1, sourcePointer.address(), 0L)
            glCompileShader(shader)

            val status = arena.allocate(ValueLayout.JAVA_INT)
            glGetShaderiv(shader, GL_COMPILE_STATUS, status.address())
            check(status.get(ValueLayout.JAVA_INT, 0L) != GL_FALSE) {
                "Shader compilation failed:\n${shaderInfoLog(shader, arena)}"
            }
        }
        return shader
    } catch (failure: Throwable) {
        glDeleteShader(shader)
        throw failure
    }
}

private fun shaderInfoLog(shader: Int, arena: Arena): String {
    val length = arena.allocate(ValueLayout.JAVA_INT)
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, length.address())
    val capacity = length.get(ValueLayout.JAVA_INT, 0L).coerceAtLeast(1)
    val log = arena.allocate(capacity.toLong(), 1L)
    glGetShaderInfoLog(shader, capacity, 0L, log.address())
    return log.getString(0L)
}

private fun programInfoLog(program: Int, arena: Arena): String {
    val length = arena.allocate(ValueLayout.JAVA_INT)
    glGetProgramiv(program, GL_INFO_LOG_LENGTH, length.address())
    val capacity = length.get(ValueLayout.JAVA_INT, 0L).coerceAtLeast(1)
    val log = arena.allocate(capacity.toLong(), 1L)
    glGetProgramInfoLog(program, capacity, 0L, log.address())
    return log.getString(0L)
}

private fun Arena.utf8(value: String): MemorySegment {
    val bytes = (value + '\u0000').encodeToByteArray()
    return allocate(bytes.size.toLong(), 1L).also { it.copyFrom(MemorySegment.ofArray(bytes)) }
}

internal fun createTeapotVertices(): FloatArray = MeshBuilder().apply {
    addLathe(
        listOf(
            ProfilePoint(0.00f, -1.43f),
            ProfilePoint(0.58f, -1.42f),
            ProfilePoint(1.08f, -1.26f),
            ProfilePoint(1.48f, -0.86f),
            ProfilePoint(1.68f, -0.18f),
            ProfilePoint(1.60f, 0.48f),
            ProfilePoint(1.36f, 0.90f),
            ProfilePoint(1.02f, 1.10f),
            ProfilePoint(0.88f, 1.16f),
        ),
        48,
    )
    addLathe(
        listOf(
            ProfilePoint(0.00f, 1.12f),
            ProfilePoint(0.88f, 1.12f),
            ProfilePoint(1.13f, 1.16f),
            ProfilePoint(1.18f, 1.22f),
            ProfilePoint(1.05f, 1.29f),
            ProfilePoint(0.80f, 1.40f),
            ProfilePoint(0.42f, 1.52f),
            ProfilePoint(0.00f, 1.57f),
        ),
        48,
    )
    addLathe(
        listOf(
            ProfilePoint(0.00f, 1.53f),
            ProfilePoint(0.22f, 1.56f),
            ProfilePoint(0.38f, 1.68f),
            ProfilePoint(0.40f, 1.83f),
            ProfilePoint(0.25f, 1.99f),
            ProfilePoint(0.00f, 2.04f),
        ),
        40,
    )
    addTube(
        curve = CubicCurve(
            Vec3(1.22f, 0.22f, 0f),
            Vec3(2.02f, 0.30f, 0f),
            Vec3(1.98f, 1.27f, 0f),
            Vec3(2.98f, 1.48f, 0f),
        ),
        longitudinalSegments = 30,
        radialSegments = 24,
        radius = { t ->
            val taper = 0.46f * (1f - t) + 0.24f * t
            if (t > 0.88f) taper + 0.055f * ((t - 0.88f) / 0.12f) else taper
        },
    )
    addTube(
        curve = CubicCurve(
            Vec3(-1.24f, 0.70f, 0f),
            Vec3(-3.18f, 1.62f, 0f),
            Vec3(-3.18f, -1.52f, 0f),
            Vec3(-1.18f, -0.78f, 0f),
        ),
        longitudinalSegments = 40,
        radialSegments = 20,
        radius = { t -> 0.23f + 0.035f * sin(PI.toFloat() * t) },
    )
}.toFloatArray()

private data class ProfilePoint(val radius: Float, val y: Float)

private data class Vertex(val position: Vec3, val normal: Vec3)

private data class CubicCurve(
    val p0: Vec3,
    val p1: Vec3,
    val p2: Vec3,
    val p3: Vec3,
) {
    fun point(t: Float): Vec3 {
        val oneMinusT = 1f - t
        return p0 * (oneMinusT * oneMinusT * oneMinusT) +
            p1 * (3f * oneMinusT * oneMinusT * t) +
            p2 * (3f * oneMinusT * t * t) +
            p3 * (t * t * t)
    }

    fun tangent(t: Float): Vec3 {
        val oneMinusT = 1f - t
        return (p1 - p0) * (3f * oneMinusT * oneMinusT) +
            (p2 - p1) * (6f * oneMinusT * t) +
            (p3 - p2) * (3f * t * t)
    }
}

private class MeshBuilder {
    private val values = ArrayList<Float>()

    fun addLathe(profile: List<ProfilePoint>, radialSegments: Int) {
        require(profile.size >= 2)
        require(radialSegments >= 3)
        val rings = profile.mapIndexed { profileIndex, point ->
            val previous = profile[(profileIndex - 1).coerceAtLeast(0)]
            val next = profile[(profileIndex + 1).coerceAtMost(profile.lastIndex)]
            val deltaRadius = next.radius - previous.radius
            val deltaY = next.y - previous.y
            List(radialSegments + 1) { radialIndex ->
                val angle = 2f * PI.toFloat() * radialIndex / radialSegments
                val cosine = cos(angle)
                val sine = sin(angle)
                Vertex(
                    Vec3(point.radius * cosine, point.y, point.radius * sine),
                    Vec3(deltaY * cosine, -deltaRadius, deltaY * sine).normalized(),
                )
            }
        }

        for (profileIndex in 0 until profile.lastIndex) {
            for (radialIndex in 0 until radialSegments) {
                val lowerLeft = rings[profileIndex][radialIndex]
                val upperLeft = rings[profileIndex + 1][radialIndex]
                val upperRight = rings[profileIndex + 1][radialIndex + 1]
                val lowerRight = rings[profileIndex][radialIndex + 1]
                addTriangle(lowerLeft, upperLeft, upperRight)
                addTriangle(lowerLeft, upperRight, lowerRight)
            }
        }
    }

    fun addTube(
        curve: CubicCurve,
        longitudinalSegments: Int,
        radialSegments: Int,
        radius: (Float) -> Float,
    ) {
        require(longitudinalSegments >= 1)
        require(radialSegments >= 3)
        val rings = List(longitudinalSegments + 1) { longitudinalIndex ->
            val t = longitudinalIndex.toFloat() / longitudinalSegments
            val center = curve.point(t)
            val tangent = curve.tangent(t).normalized()
            val side = Vec3(-tangent.y, tangent.x, 0f).normalized()
            val binormal = Vec3(0f, 0f, 1f)
            List(radialSegments + 1) { radialIndex ->
                val angle = 2f * PI.toFloat() * radialIndex / radialSegments
                val normal = (side * cos(angle) + binormal * sin(angle)).normalized()
                Vertex(center + normal * radius(t), normal)
            }
        }

        for (longitudinalIndex in 0 until longitudinalSegments) {
            for (radialIndex in 0 until radialSegments) {
                val lowerLeft = rings[longitudinalIndex][radialIndex]
                val upperLeft = rings[longitudinalIndex + 1][radialIndex]
                val upperRight = rings[longitudinalIndex + 1][radialIndex + 1]
                val lowerRight = rings[longitudinalIndex][radialIndex + 1]
                addTriangle(lowerLeft, upperRight, upperLeft)
                addTriangle(lowerLeft, lowerRight, upperRight)
            }
        }
    }

    fun toFloatArray(): FloatArray = FloatArray(values.size) { values[it] }

    private fun addTriangle(first: Vertex, second: Vertex, third: Vertex) {
        if ((second.position - first.position).cross(third.position - first.position).lengthSquared() < 1e-10f) {
            return
        }
        addVertex(first)
        addVertex(second)
        addVertex(third)
    }

    private fun addVertex(vertex: Vertex) {
        values += vertex.position.x
        values += vertex.position.y
        values += vertex.position.z
        values += vertex.normal.x
        values += vertex.normal.y
        values += vertex.normal.z
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun lengthSquared(): Float = dot(this)

    fun normalized(): Vec3 {
        val length = sqrt(lengthSquared())
        check(length > 1e-7f) { "Cannot normalize a zero-length vector" }
        return this * (1f / length)
    }
}

private object Mat4 {
    fun rotationY(angle: Float): FloatArray {
        val cosine = cos(angle)
        val sine = sin(angle)
        return floatArrayOf(
            cosine, 0f, -sine, 0f,
            0f, 1f, 0f, 0f,
            sine, 0f, cosine, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun perspective(fieldOfViewRadians: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val focalLength = 1f / tan(fieldOfViewRadians / 2f)
        return floatArrayOf(
            focalLength / aspect, 0f, 0f, 0f,
            0f, focalLength, 0f, 0f,
            0f, 0f, (far + near) / (near - far), -1f,
            0f, 0f, (2f * far * near) / (near - far), 0f,
        )
    }

    fun lookAt(eye: Vec3, center: Vec3, up: Vec3): FloatArray {
        val forward = (center - eye).normalized()
        val side = forward.cross(up).normalized()
        val correctedUp = side.cross(forward)
        return floatArrayOf(
            side.x, correctedUp.x, -forward.x, 0f,
            side.y, correctedUp.y, -forward.y, 0f,
            side.z, correctedUp.z, -forward.z, 0f,
            -side.dot(eye), -correctedUp.dot(eye), forward.dot(eye), 1f,
        )
    }

    fun multiply(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == 16 && right.size == 16)
        return FloatArray(16).also { result ->
            for (column in 0 until 4) {
                for (row in 0 until 4) {
                    var value = 0f
                    for (index in 0 until 4) {
                        value += left[index * 4 + row] * right[column * 4 + index]
                    }
                    result[column * 4 + row] = value
                }
            }
        }
    }
}

private val VERTEX_SHADER = """
    #version 330 core

    layout (location = 0) in vec3 aPosition;
    layout (location = 1) in vec3 aNormal;

    uniform mat4 uModel;
    uniform mat4 uMvp;

    out vec3 vWorldPosition;
    out vec3 vNormal;

    void main() {
        vec4 worldPosition = uModel * vec4(aPosition, 1.0);
        vWorldPosition = worldPosition.xyz;
        vNormal = normalize(mat3(uModel) * aNormal);
        gl_Position = uMvp * vec4(aPosition, 1.0);
    }
""".trimIndent()

private val FRAGMENT_SHADER = """
    #version 330 core

    in vec3 vWorldPosition;
    in vec3 vNormal;

    uniform vec3 uLightPosition;
    uniform vec3 uCameraPosition;
    uniform vec3 uBaseColor;

    out vec4 fragmentColor;

    void main() {
        vec3 normal = normalize(vNormal);
        vec3 lightDirection = normalize(uLightPosition - vWorldPosition);
        vec3 viewDirection = normalize(uCameraPosition - vWorldPosition);
        vec3 halfDirection = normalize(lightDirection + viewDirection);

        float diffuse = max(dot(normal, lightDirection), 0.0);
        float specular = pow(max(dot(normal, halfDirection), 0.0), 96.0);
        float rim = pow(1.0 - max(dot(normal, viewDirection), 0.0), 3.0);

        vec3 ambientColor = uBaseColor * 0.16;
        vec3 diffuseColor = uBaseColor * diffuse * 1.05;
        vec3 specularColor = vec3(1.0, 0.93, 0.78) * specular * 0.85;
        vec3 rimColor = vec3(0.12, 0.48, 0.58) * rim * 0.32;
        vec3 linearColor = ambientColor + diffuseColor + specularColor + rimColor;
        vec3 gammaCorrected = pow(linearColor, vec3(1.0 / 2.2));
        fragmentColor = vec4(gammaCorrected, 1.0);
    }
""".trimIndent()
