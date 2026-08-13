package net.echonolix.caelum.directx.demo

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.math.PI

private const val WINDOW_WIDTH = 960
private const val WINDOW_HEIGHT = 720
private const val DEFAULT_HIDDEN_SECONDS = 0.35
private const val MAX_HIDDEN_FRAMES = 300

/**
 * Runs the Direct3D 11 backend of the Caelum DirectX teapot demo.
 *
 * The demo intentionally lives in the test source set: it is executable sample
 * code, while the published module remains a binding library. The default
 * Gradle demo task now selects the D3D12 launcher; this legacy backend remains
 * directly runnable with `caelum.directx.version=11`. Version-specific
 * launchers must never silently fall back to D3D11.
 */
public fun main(): Unit {
    check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "The Direct3D teapot demo can only run on Windows"
    }
    val requestedVersion = System.getProperty("caelum.directx.version", "11")
    require(requestedVersion == "11") {
        "This launcher is the Direct3D 11 backend; requested DirectX $requestedVersion"
    }

    val requestedSeconds = System.getProperty("caelum.demo.seconds", "0").toDouble()
    require(requestedSeconds.isFinite() && requestedSeconds >= 0.0) {
        "caelum.demo.seconds must be finite and zero or positive"
    }
    val hidden = System.getProperty("caelum.demo.hidden", "false").toBooleanStrict()
    val durationSeconds = when {
        requestedSeconds > 0.0 -> requestedSeconds
        hidden -> DEFAULT_HIDDEN_SECONDS
        else -> 0.0
    }

    Win32DemoWindow(
        title = "Caelum DirectX 11 - Lit Teapot",
        width = WINDOW_WIDTH,
        height = WINDOW_HEIGHT,
        hidden = hidden,
    ).use { window ->
        D3D11TeapotRenderer(window.hwnd, WINDOW_WIDTH, WINDOW_HEIGHT).use { renderer ->
            println("DIRECTX_BACKEND=D3D11")
            println("D3D_FEATURE_LEVEL=0x${renderer.featureLevel.toString(16)}")
            println("TEAPOT_VERTICES=${renderer.vertexCount}")
            println("TEAPOT_INDICES=${renderer.indexCount}")
            if (!hidden) println("Close the window to exit.")

            val startedAt = System.nanoTime()
            var renderedFrames = 0
            var frameVerified = false
            while (!window.pollCloseRequested()) {
                val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
                if (durationSeconds > 0.0 && renderedFrames > 0 && elapsedSeconds >= durationSeconds) break
                if (hidden && requestedSeconds == 0.0 && renderedFrames >= MAX_HIDDEN_FRAMES) break

                renderer.render(elapsedSeconds.toFloat())
                if (!frameVerified) {
                    renderer.verifyRenderedFrame()
                    frameVerified = true
                }
                renderer.present(if (hidden) 0 else 1)
                renderedFrames++
            }

            check(frameVerified) { "The demo closed before a frame could be verified" }
            check(renderedFrames > 0) { "The demo closed before rendering a frame" }
            renderer.checkDeviceHealth()
            println("RENDERED_FRAMES=$renderedFrames")
            println("CAELUM_DIRECTX11_TEAPOT_DEMO_OK")
            println("CAELUM_TEAPOT_DEMO_OK")
        }
    }
}

internal class D3D11TeapotRenderer(
    hwnd: MemorySegment,
    private val width: Int,
    private val height: Int,
) : AutoCloseable {
    private val ownedObjects = ArrayList<MemorySegment>()
    private val mesh = TeapotMesh.create()
    private val cameraPosition = Vec3(0.25f, 0.45f, 7.4f)
    private val view = Mat4.lookAtRightHanded(cameraPosition, Vec3(0f, 0.15f, 0f), Vec3(0f, 1f, 0f))
    private val arena: Arena

    private lateinit var swapChain: MemorySegment
    private lateinit var device: MemorySegment
    private lateinit var context: MemorySegment
    private lateinit var backBuffer: MemorySegment
    private lateinit var renderTargetView: MemorySegment
    private lateinit var depthTexture: MemorySegment
    private lateinit var depthStencilView: MemorySegment
    private lateinit var rasterizerState: MemorySegment
    private lateinit var stagingTexture: MemorySegment
    private lateinit var vertexShader: MemorySegment
    private lateinit var pixelShader: MemorySegment
    private lateinit var inputLayout: MemorySegment
    private lateinit var vertexBuffer: MemorySegment
    private lateinit var indexBuffer: MemorySegment
    private lateinit var constantBuffer: MemorySegment
    private lateinit var constantData: MemorySegment
    private lateinit var renderTargets: MemorySegment
    private lateinit var vertexBuffers: MemorySegment
    private lateinit var constantBuffers: MemorySegment
    private lateinit var vertexStrides: MemorySegment
    private lateinit var vertexOffsets: MemorySegment
    private lateinit var clearColor: MemorySegment
    private lateinit var viewport: MemorySegment

    internal var featureLevel: Int = 0
        private set
    internal val vertexCount: Int get() = mesh.vertexCount
    internal val indexCount: Int get() = mesh.indexCount

    private var frameVerified = false
    private var closed = false

    init {
        arena = Arena.ofConfined()
        try {
            require(width > 0 && height > 0)
            D3D11Native.ensureAvailable()

            val swapChainDesc = arena.allocate(D3D11Layouts.DXGI_SWAP_CHAIN_DESC)
            D3D11Layouts.initializeSwapChainDesc(swapChainDesc, hwnd, width, height)
            val swapChainOut = arena.allocate(ValueLayout.ADDRESS)
            val deviceOut = arena.allocate(ValueLayout.ADDRESS)
            val featureLevelOut = arena.allocate(ValueLayout.JAVA_INT)
            val contextOut = arena.allocate(ValueLayout.ADDRESS)

            var creationResult = D3D11Native.createDeviceAndSwapChain(
                driverType = D3D_DRIVER_TYPE_HARDWARE,
                swapChainDesc = swapChainDesc,
                swapChainOut = swapChainOut,
                deviceOut = deviceOut,
                featureLevelOut = featureLevelOut,
                contextOut = contextOut,
            )
            var driver = "hardware"
            if (creationResult < 0) {
                releaseComOutputs(contextOut, deviceOut, swapChainOut)
                creationResult = D3D11Native.createDeviceAndSwapChain(
                    driverType = D3D_DRIVER_TYPE_WARP,
                    swapChainDesc = swapChainDesc,
                    swapChainOut = swapChainOut,
                    deviceOut = deviceOut,
                    featureLevelOut = featureLevelOut,
                    contextOut = contextOut,
                )
                driver = "WARP"
            }
            if (creationResult < 0) {
                releaseComOutputs(contextOut, deviceOut, swapChainOut)
                checkHResult(creationResult, "D3D11CreateDeviceAndSwapChain ($driver)")
            }
            val createdObjects = try {
                listOf(
                    swapChainOut.requireComObject("IDXGISwapChain"),
                    deviceOut.requireComObject("ID3D11Device"),
                    contextOut.requireComObject("ID3D11DeviceContext"),
                )
            } catch (failure: Throwable) {
                releaseComOutputs(contextOut, deviceOut, swapChainOut)
                throw failure
            }
            swapChain = own(createdObjects[0])
            device = own(createdObjects[1])
            context = own(createdObjects[2])
            featureLevel = featureLevelOut.get(ValueLayout.JAVA_INT, 0L)
            check(featureLevel >= D3D_FEATURE_LEVEL_11_0) {
                "The D3D11 teapot shaders require feature level 11_0; got 0x${featureLevel.toString(16)}"
            }

            backBuffer = own(getBackBuffer())
            renderTargetView = own(createRenderTargetView(backBuffer))
            depthTexture = own(createTexture2D(depthTextureDescription(D3D11_USAGE_DEFAULT, D3D11_BIND_DEPTH_STENCIL, 0)))
            depthStencilView = own(createDepthStencilView(depthTexture))
            rasterizerState = own(createTeapotRasterizerState())
            stagingTexture = own(createTexture2D(colorTextureDescription(D3D11_USAGE_STAGING, 0, D3D11_CPU_ACCESS_READ)))

            val vertexBytecode = compileShader(VERTEX_SHADER_SOURCE, "VSMain", "vs_5_0")
            try {
                vertexShader = own(createShader(D3D11Slots.DEVICE_CREATE_VERTEX_SHADER, vertexBytecode, "ID3D11VertexShader"))
                pixelShader = compileShader(PIXEL_SHADER_SOURCE, "PSMain", "ps_5_0").useBytecode { pixelBytecode ->
                    own(createShader(D3D11Slots.DEVICE_CREATE_PIXEL_SHADER, pixelBytecode, "ID3D11PixelShader"))
                }
                inputLayout = own(createInputLayout(vertexBytecode))
            } finally {
                vertexBytecode.close()
            }

            vertexBuffer = own(createVertexBuffer())
            indexBuffer = own(createIndexBuffer())
            constantBuffer = own(createConstantBuffer())

            constantData = arena.allocate(CONSTANT_BUFFER_BYTES, 16L)
            renderTargets = arena.allocate(ValueLayout.ADDRESS)
            renderTargets.set(ValueLayout.ADDRESS, 0L, renderTargetView)
            vertexBuffers = arena.allocate(ValueLayout.ADDRESS)
            vertexBuffers.set(ValueLayout.ADDRESS, 0L, vertexBuffer)
            constantBuffers = arena.allocate(ValueLayout.ADDRESS)
            constantBuffers.set(ValueLayout.ADDRESS, 0L, constantBuffer)
            vertexStrides = arena.allocate(ValueLayout.JAVA_INT)
            vertexStrides.set(ValueLayout.JAVA_INT, 0L, TeapotMesh.FLOATS_PER_VERTEX * Float.SIZE_BYTES)
            vertexOffsets = arena.allocate(ValueLayout.JAVA_INT)
            vertexOffsets.set(ValueLayout.JAVA_INT, 0L, 0)
            clearColor = arena.allocate(4L * Float.SIZE_BYTES, ValueLayout.JAVA_FLOAT.byteAlignment())
            clearColor.setAtIndex(ValueLayout.JAVA_FLOAT, 0L, CLEAR_RED)
            clearColor.setAtIndex(ValueLayout.JAVA_FLOAT, 1L, CLEAR_GREEN)
            clearColor.setAtIndex(ValueLayout.JAVA_FLOAT, 2L, CLEAR_BLUE)
            clearColor.setAtIndex(ValueLayout.JAVA_FLOAT, 3L, 1f)
            viewport = arena.allocate(D3D11Layouts.D3D11_VIEWPORT)
            D3D11Layouts.initializeViewport(viewport, width, height)
        } catch (failure: Throwable) {
            closeAfterConstructionFailure(failure)
        }
    }

    internal fun render(timeSeconds: Float) {
        check(!closed) { "D3D11 renderer is closed" }
        val model = Mat4.rotationY(timeSeconds * 0.48f)
        val projection = Mat4.perspectiveRightHanded(
            fieldOfViewRadians = (42.0 * PI / 180.0).toFloat(),
            aspect = width.toFloat() / height,
            near = 0.1f,
            far = 40f,
        )
        val mvp = Mat4.multiply(projection, Mat4.multiply(view, model))
        constantData.writeFloats(MVP_OFFSET, mvp)
        constantData.writeFloats(MODEL_OFFSET, model)
        constantData.writeFloat4(LIGHT_OFFSET, 4.2f, 5.2f, 5.5f, 1f)
        constantData.writeFloat4(CAMERA_OFFSET, cameraPosition.x, cameraPosition.y, cameraPosition.z, 1f)
        constantData.writeFloat4(COLOR_OFFSET, 0.055f, 0.55f, 0.46f, 1f)

        D3D11Native.comVoid(
            context,
            D3D11Slots.CONTEXT_UPDATE_SUBRESOURCE,
            D3D11Descriptors.UPDATE_SUBRESOURCE,
            constantBuffer,
            0,
            MemorySegment.NULL,
            constantData,
            0,
            0,
        )
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_OM_SET_RENDER_TARGETS, D3D11Descriptors.OM_SET_RENDER_TARGETS, 1, renderTargets, depthStencilView)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_RS_SET_VIEWPORTS, D3D11Descriptors.RS_SET_VIEWPORTS, 1, viewport)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_RS_SET_STATE, D3D11Descriptors.ONE_OBJECT, rasterizerState)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_CLEAR_RTV, D3D11Descriptors.CLEAR_RTV, renderTargetView, clearColor)
        D3D11Native.comVoid(
            context,
            D3D11Slots.CONTEXT_CLEAR_DSV,
            D3D11Descriptors.CLEAR_DSV,
            depthStencilView,
            D3D11_CLEAR_DEPTH,
            1f,
            0.toByte(),
        )
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_IA_SET_INPUT_LAYOUT, D3D11Descriptors.ONE_OBJECT, inputLayout)
        D3D11Native.comVoid(
            context,
            D3D11Slots.CONTEXT_IA_SET_VERTEX_BUFFERS,
            D3D11Descriptors.IA_SET_VERTEX_BUFFERS,
            0,
            1,
            vertexBuffers,
            vertexStrides,
            vertexOffsets,
        )
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_IA_SET_INDEX_BUFFER, D3D11Descriptors.IA_SET_INDEX_BUFFER, indexBuffer, DXGI_FORMAT_R32_UINT, 0)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_IA_SET_PRIMITIVE_TOPOLOGY, D3D11Descriptors.ONE_INT, D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_VS_SET_SHADER, D3D11Descriptors.SET_SHADER, vertexShader, MemorySegment.NULL, 0)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_PS_SET_SHADER, D3D11Descriptors.SET_SHADER, pixelShader, MemorySegment.NULL, 0)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_VS_SET_CONSTANT_BUFFERS, D3D11Descriptors.SET_CONSTANT_BUFFERS, 0, 1, constantBuffers)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_PS_SET_CONSTANT_BUFFERS, D3D11Descriptors.SET_CONSTANT_BUFFERS, 0, 1, constantBuffers)
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_DRAW_INDEXED, D3D11Descriptors.DRAW_INDEXED, indexCount, 0, 0)
    }

    internal fun verifyRenderedFrame() {
        if (frameVerified) return
        D3D11Native.comVoid(context, D3D11Slots.CONTEXT_COPY_RESOURCE, D3D11Descriptors.COPY_RESOURCE, stagingTexture, backBuffer)
        val mapped = arena.allocate(D3D11Layouts.D3D11_MAPPED_SUBRESOURCE)
        val mapResult = D3D11Native.comInt(
            context,
            D3D11Slots.CONTEXT_MAP,
            D3D11Descriptors.MAP,
            stagingTexture,
            0,
            D3D11_MAP_READ,
            0,
            mapped,
        )
        checkHResult(mapResult, "ID3D11DeviceContext.Map(staging back buffer)")
        try {
            val pixels = mapped.get(ValueLayout.ADDRESS, 0L)
            val rowPitch = mapped.get(ValueLayout.JAVA_INT, 8L)
            check(pixels != MemorySegment.NULL && rowPitch >= width * BYTES_PER_PIXEL) {
                "D3D11 returned an invalid mapped back buffer"
            }
            val mappedPixels = pixels.reinterpret(rowPitch.toLong() * height)
            val centerOffset = (height / 2L) * rowPitch + (width / 2L) * BYTES_PER_PIXEL
            val center = mappedPixels.asSlice(centerOffset, BYTES_PER_PIXEL.toLong())
            val red = center.getAtIndex(ValueLayout.JAVA_BYTE, 0L).toInt() and 0xff
            val green = center.getAtIndex(ValueLayout.JAVA_BYTE, 1L).toInt() and 0xff
            val blue = center.getAtIndex(ValueLayout.JAVA_BYTE, 2L).toInt() and 0xff
            val clearRed = (CLEAR_RED * 255).toInt()
            val clearGreen = (CLEAR_GREEN * 255).toInt()
            val clearBlue = (CLEAR_BLUE * 255).toInt()
            var nonClearPixels = 0
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                val rowOffset = y.toLong() * rowPitch
                for (x in 0 until width) {
                    val offset = rowOffset + x.toLong() * BYTES_PER_PIXEL
                    val pixelRed = mappedPixels.get(ValueLayout.JAVA_BYTE, offset).toInt() and 0xff
                    val pixelGreen = mappedPixels.get(ValueLayout.JAVA_BYTE, offset + 1L).toInt() and 0xff
                    val pixelBlue = mappedPixels.get(ValueLayout.JAVA_BYTE, offset + 2L).toInt() and 0xff
                    val distance = kotlin.math.abs(pixelRed - clearRed) +
                        kotlin.math.abs(pixelGreen - clearGreen) +
                        kotlin.math.abs(pixelBlue - clearBlue)
                    if (distance > MIN_NON_CLEAR_PIXEL_DISTANCE) {
                        nonClearPixels++
                        minX = minOf(minX, x)
                        minY = minOf(minY, y)
                        maxX = maxOf(maxX, x)
                        maxY = maxOf(maxY, y)
                    }
                }
            }
            println("CENTER_PIXEL=$red,$green,$blue")
            println("NON_CLEAR_PIXELS=$nonClearPixels")
            if (nonClearPixels > 0) println("NON_CLEAR_BOUNDS=$minX,$minY-$maxX,$maxY")
            check(nonClearPixels >= MIN_NON_CLEAR_PIXELS) {
                "Only $nonClearPixels pixels differ from the D3D11 clear color; no teapot draw was visible"
            }
            frameVerified = true
        } finally {
            D3D11Native.comVoid(context, D3D11Slots.CONTEXT_UNMAP, D3D11Descriptors.UNMAP, stagingTexture, 0)
        }
    }

    internal fun present(syncInterval: Int) {
        val result = D3D11Native.comInt(swapChain, D3D11Slots.SWAP_CHAIN_PRESENT, D3D11Descriptors.PRESENT, syncInterval, 0)
        checkHResult(result, "IDXGISwapChain.Present")
    }

    internal fun checkDeviceHealth() {
        val result = D3D11Native.comInt(device, D3D11Slots.DEVICE_GET_REMOVED_REASON, D3D11Descriptors.NO_ARGUMENTS)
        checkHResult(result, "ID3D11Device.GetDeviceRemovedReason")
    }

    override fun close() {
        if (closed) return
        closed = true
        if (context != MemorySegment.NULL) {
            runCatching { D3D11Native.comVoid(context, D3D11Slots.CONTEXT_CLEAR_STATE, D3D11Descriptors.VOID_NO_ARGUMENTS) }
            runCatching { D3D11Native.comVoid(context, D3D11Slots.CONTEXT_FLUSH, D3D11Descriptors.VOID_NO_ARGUMENTS) }
        }
        ownedObjects.asReversed().forEach { pointer -> runCatching { D3D11Native.release(pointer) } }
        ownedObjects.clear()
        arena.close()
    }

    private fun closeAfterConstructionFailure(failure: Throwable): Nothing {
        closed = true
        ownedObjects.asReversed().forEach { pointer ->
            runCatching { D3D11Native.release(pointer) }.exceptionOrNull()?.let(failure::addSuppressed)
        }
        ownedObjects.clear()
        runCatching(arena::close).exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }

    private fun releaseComOutputs(vararg outputs: MemorySegment) {
        outputs.forEach { output ->
            val pointer = output.get(ValueLayout.ADDRESS, 0L)
            if (pointer != MemorySegment.NULL && pointer.address() != 0L) {
                D3D11Native.release(MemorySegment.ofAddress(pointer.address()))
                output.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
            }
        }
    }

    private fun getBackBuffer(): MemorySegment = Arena.ofConfined().use { temporary ->
        val iid = temporary.allocate(D3D11Layouts.GUID)
        D3D11Layouts.writeGuid(iid, IID_ID3D11_TEXTURE2D)
        val output = temporary.allocate(ValueLayout.ADDRESS)
        output.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
        takeComOutput(
            result = D3D11Native.comInt(
                    swapChain,
                    D3D11Slots.SWAP_CHAIN_GET_BUFFER,
                    D3D11Descriptors.GET_BUFFER,
                    0,
                    iid,
                    output,
                ),
            output = output,
            operation = "IDXGISwapChain.GetBuffer(ID3D11Texture2D)",
            objectName = "swap-chain ID3D11Texture2D",
        )
    }

    private fun createRenderTargetView(texture: MemorySegment): MemorySegment = createDeviceObject(
        slot = D3D11Slots.DEVICE_CREATE_RENDER_TARGET_VIEW,
        descriptor = D3D11Descriptors.CREATE_VIEW,
        operation = "ID3D11Device.CreateRenderTargetView",
        arguments = arrayOf(texture, MemorySegment.NULL),
    )

    private fun createDepthStencilView(texture: MemorySegment): MemorySegment = createDeviceObject(
        slot = D3D11Slots.DEVICE_CREATE_DEPTH_STENCIL_VIEW,
        descriptor = D3D11Descriptors.CREATE_VIEW,
        operation = "ID3D11Device.CreateDepthStencilView",
        arguments = arrayOf(texture, MemorySegment.NULL),
    )

    private fun createTeapotRasterizerState(): MemorySegment = Arena.ofConfined().use { temporary ->
        val description = temporary.allocate(D3D11Layouts.D3D11_RASTERIZER_DESC)
        D3D11Layouts.initializeTeapotRasterizerDesc(description)
        createDeviceObject(
            slot = D3D11Slots.DEVICE_CREATE_RASTERIZER_STATE,
            descriptor = D3D11Descriptors.CREATE_RASTERIZER_STATE,
            operation = "ID3D11Device.CreateRasterizerState(teapot CCW front faces)",
            arguments = arrayOf(description),
        )
    }

    private fun createTexture2D(description: MemorySegment): MemorySegment = createDeviceObject(
        slot = D3D11Slots.DEVICE_CREATE_TEXTURE2D,
        descriptor = D3D11Descriptors.CREATE_TEXTURE_2D,
        operation = "ID3D11Device.CreateTexture2D",
        arguments = arrayOf(description, MemorySegment.NULL),
    )

    private fun createShader(slot: Int, bytecode: ShaderBytecode, name: String): MemorySegment = createDeviceObject(
        slot = slot,
        descriptor = D3D11Descriptors.CREATE_SHADER,
        operation = "ID3D11Device.Create$name",
        arguments = arrayOf(bytecode.pointer, bytecode.size, MemorySegment.NULL),
    )

    private fun createInputLayout(bytecode: ShaderBytecode): MemorySegment = Arena.ofConfined().use { temporary ->
        val positionName = temporary.utf8("POSITION")
        val normalName = temporary.utf8("NORMAL")
        val elements = temporary.allocate(D3D11Layouts.D3D11_INPUT_ELEMENT_DESC.byteSize() * 2L, D3D11Layouts.D3D11_INPUT_ELEMENT_DESC.byteAlignment())
        D3D11Layouts.initializeInputElement(elements.asSlice(0L, D3D11Layouts.D3D11_INPUT_ELEMENT_DESC.byteSize()), positionName, 0, DXGI_FORMAT_R32G32B32_FLOAT, 0)
        D3D11Layouts.initializeInputElement(elements.asSlice(D3D11Layouts.D3D11_INPUT_ELEMENT_DESC.byteSize()), normalName, 0, DXGI_FORMAT_R32G32B32_FLOAT, 3 * Float.SIZE_BYTES)
        createDeviceObject(
            slot = D3D11Slots.DEVICE_CREATE_INPUT_LAYOUT,
            descriptor = D3D11Descriptors.CREATE_INPUT_LAYOUT,
            operation = "ID3D11Device.CreateInputLayout",
            arguments = arrayOf(elements, 2, bytecode.pointer, bytecode.size),
        )
    }

    private fun createVertexBuffer(): MemorySegment = Arena.ofConfined().use { temporary ->
        val bytes = temporary.allocate(mesh.interleavedVertices.size.toLong() * Float.SIZE_BYTES, ValueLayout.JAVA_FLOAT.byteAlignment())
        mesh.interleavedVertices.forEachIndexed { index, value -> bytes.setAtIndex(ValueLayout.JAVA_FLOAT, index.toLong(), value) }
        createBuffer(bytes, mesh.interleavedVertices.size * Float.SIZE_BYTES, D3D11_BIND_VERTEX_BUFFER, D3D11_USAGE_IMMUTABLE)
    }

    private fun createIndexBuffer(): MemorySegment = Arena.ofConfined().use { temporary ->
        val bytes = temporary.allocate(mesh.indices.size.toLong() * Int.SIZE_BYTES, ValueLayout.JAVA_INT.byteAlignment())
        mesh.indices.forEachIndexed { index, value -> bytes.setAtIndex(ValueLayout.JAVA_INT, index.toLong(), value) }
        createBuffer(bytes, mesh.indices.size * Int.SIZE_BYTES, D3D11_BIND_INDEX_BUFFER, D3D11_USAGE_IMMUTABLE)
    }

    private fun createConstantBuffer(): MemorySegment = createBuffer(MemorySegment.NULL, CONSTANT_BUFFER_BYTES.toInt(), D3D11_BIND_CONSTANT_BUFFER, D3D11_USAGE_DEFAULT)

    private fun createBuffer(initialBytes: MemorySegment, byteWidth: Int, bindFlags: Int, usage: Int): MemorySegment = Arena.ofConfined().use { temporary ->
        val description = temporary.allocate(D3D11Layouts.D3D11_BUFFER_DESC)
        D3D11Layouts.initializeBufferDesc(description, byteWidth, usage, bindFlags)
        val initialData = if (initialBytes == MemorySegment.NULL) {
            MemorySegment.NULL
        } else {
            temporary.allocate(D3D11Layouts.D3D11_SUBRESOURCE_DATA).also {
                it.set(ValueLayout.ADDRESS, 0L, initialBytes)
            }
        }
        createDeviceObject(
            slot = D3D11Slots.DEVICE_CREATE_BUFFER,
            descriptor = D3D11Descriptors.CREATE_BUFFER,
            operation = "ID3D11Device.CreateBuffer",
            arguments = arrayOf(description, initialData),
        )
    }

    private fun createDeviceObject(
        slot: Int,
        descriptor: java.lang.foreign.FunctionDescriptor,
        operation: String,
        arguments: Array<out Any>,
    ): MemorySegment = Arena.ofConfined().use { temporary ->
        val output = temporary.allocate(ValueLayout.ADDRESS)
        output.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
        takeComOutput(
            result = D3D11Native.comInt(device, slot, descriptor, *arguments, output),
            output = output,
            operation = operation,
            objectName = operation,
        )
    }

    private fun takeComOutput(
        result: Int,
        output: MemorySegment,
        operation: String,
        objectName: String,
    ): MemorySegment {
        if (result < 0) {
            releaseComOutputs(output)
            checkHResult(result, operation)
        }
        return try {
            output.requireComObject(objectName)
        } catch (failure: Throwable) {
            releaseComOutputs(output)
            throw failure
        }
    }

    private fun depthTextureDescription(usage: Int, bindFlags: Int, cpuAccess: Int): MemorySegment =
        textureDescription(DXGI_FORMAT_D24_UNORM_S8_UINT, usage, bindFlags, cpuAccess)

    private fun colorTextureDescription(usage: Int, bindFlags: Int, cpuAccess: Int): MemorySegment =
        textureDescription(DXGI_FORMAT_R8G8B8A8_UNORM, usage, bindFlags, cpuAccess)

    private fun textureDescription(format: Int, usage: Int, bindFlags: Int, cpuAccess: Int): MemorySegment =
        arena.allocate(D3D11Layouts.D3D11_TEXTURE2D_DESC).also {
            D3D11Layouts.initializeTexture2DDesc(it, width, height, format, usage, bindFlags, cpuAccess)
        }

    private fun own(pointer: MemorySegment): MemorySegment = pointer.also(ownedObjects::add)

    private companion object {
        const val CLEAR_RED = 0.025f
        const val CLEAR_GREEN = 0.04f
        const val CLEAR_BLUE = 0.075f
        const val MIN_NON_CLEAR_PIXEL_DISTANCE = 24
        const val MIN_NON_CLEAR_PIXELS = 64
        const val BYTES_PER_PIXEL = 4
        const val MVP_OFFSET = 0L
        const val MODEL_OFFSET = 64L
        const val LIGHT_OFFSET = 128L
        const val CAMERA_OFFSET = 144L
        const val COLOR_OFFSET = 160L
        const val CONSTANT_BUFFER_BYTES = 176L
    }
}

private class ShaderBytecode(
    private val blob: MemorySegment,
    val pointer: MemorySegment,
    val size: Long,
) : AutoCloseable {
    override fun close() {
        D3D11Native.release(blob)
    }
}

private inline fun <T> ShaderBytecode.useBytecode(block: (ShaderBytecode) -> T): T = use(block)

private fun compileShader(source: String, entryPoint: String, target: String): ShaderBytecode = Arena.ofConfined().use { arena ->
    val sourceBytes = source.encodeToByteArray()
    val sourceMemory = arena.allocate(sourceBytes.size.toLong(), 1L)
    sourceMemory.copyFrom(MemorySegment.ofArray(sourceBytes))
    val codeOut = arena.allocate(ValueLayout.ADDRESS)
    val errorsOut = arena.allocate(ValueLayout.ADDRESS)
    codeOut.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
    errorsOut.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
    val result = D3D11Native.d3dCompile(
        sourceMemory,
        sourceBytes.size.toLong(),
        arena.utf8(entryPoint),
        arena.utf8(target),
        codeOut,
        errorsOut,
    )
    val errors = errorsOut.get(ValueLayout.ADDRESS, 0L)
    try {
        if (result < 0) {
            val message = if (errors == MemorySegment.NULL) "no compiler diagnostic" else D3D11Native.blobText(errors)
            error("D3DCompile($entryPoint/$target) failed with ${hResultHex(result)}:\n$message")
        }
    } finally {
        if (errors != MemorySegment.NULL) D3D11Native.release(errors)
    }
    val blob = codeOut.requireComObject("compiled $target shader blob")
    try {
        ShaderBytecode(blob, D3D11Native.blobPointer(blob), D3D11Native.blobSize(blob))
    } catch (failure: Throwable) {
        runCatching { D3D11Native.release(blob) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}

private fun MemorySegment.writeFloats(offset: Long, values: FloatArray) {
    values.forEachIndexed { index, value -> set(ValueLayout.JAVA_FLOAT, offset + index * Float.SIZE_BYTES, value) }
}

private fun MemorySegment.writeFloat4(offset: Long, x: Float, y: Float, z: Float, w: Float) {
    set(ValueLayout.JAVA_FLOAT, offset, x)
    set(ValueLayout.JAVA_FLOAT, offset + 4L, y)
    set(ValueLayout.JAVA_FLOAT, offset + 8L, z)
    set(ValueLayout.JAVA_FLOAT, offset + 12L, w)
}

private val VERTEX_SHADER_SOURCE = """
    cbuffer Scene : register(b0) {
        column_major float4x4 mvp;
        column_major float4x4 model;
        float4 lightPosition;
        float4 cameraPosition;
        float4 baseColor;
    };

    struct VertexInput {
        float3 position : POSITION;
        float3 normal : NORMAL;
    };

    struct VertexOutput {
        float4 position : SV_POSITION;
        float3 worldPosition : TEXCOORD0;
        float3 normal : TEXCOORD1;
    };

    VertexOutput VSMain(VertexInput input) {
        VertexOutput output;
        float4 world = mul(model, float4(input.position, 1.0));
        output.position = mul(mvp, float4(input.position, 1.0));
        output.worldPosition = world.xyz;
        output.normal = normalize(mul((float3x3)model, input.normal));
        return output;
    }
""".trimIndent()

private val PIXEL_SHADER_SOURCE = """
    cbuffer Scene : register(b0) {
        column_major float4x4 mvp;
        column_major float4x4 model;
        float4 lightPosition;
        float4 cameraPosition;
        float4 baseColor;
    };

    struct PixelInput {
        float4 position : SV_POSITION;
        float3 worldPosition : TEXCOORD0;
        float3 normal : TEXCOORD1;
    };

    float4 PSMain(PixelInput input) : SV_TARGET {
        float3 normal = normalize(input.normal);
        float3 lightDirection = normalize(lightPosition.xyz - input.worldPosition);
        float3 viewDirection = normalize(cameraPosition.xyz - input.worldPosition);
        float3 halfDirection = normalize(lightDirection + viewDirection);
        float diffuse = max(dot(normal, lightDirection), 0.0);
        float specular = pow(max(dot(normal, halfDirection), 0.0), 96.0);
        float rim = pow(1.0 - max(dot(normal, viewDirection), 0.0), 3.0);
        float3 linearColor = baseColor.rgb * (0.16 + diffuse * 1.05) +
            float3(1.0, 0.93, 0.78) * specular * 0.85 +
            float3(0.12, 0.48, 0.58) * rim * 0.32;
        return float4(pow(saturate(linearColor), 1.0 / 2.2), 1.0);
    }
""".trimIndent()
