package net.echonolix.caelum.directx.demo.jolt

import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import net.echonolix.caelum.directx.demo.Mat4
import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.dxgi.api.NativeApiInvoker
import net.echonolix.caelum.dxgi.api.NativeRecordDeclaration
import net.echonolix.caelum.dxgi.com.Guid
import java.awt.image.BufferedImage
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs

/** All DirectX signatures, COM slots, IIDs and record offsets come from Caelum's public catalog. */
internal class JoltSceneRenderer(
    hwnd: MemorySegment,
    private val width: Int,
    private val height: Int,
    geometries: List<Geometry>,
    private val camera: Vec3,
    target: Vec3,
) : AutoCloseable {
    private val arena = Arena.ofConfined()
    private val api = CatalogCalls(arena)
    private val owned = ArrayList<Pair<MemorySegment, String>>()
    private var context = MemorySegment.NULL
    private var device = MemorySegment.NULL
    private var swapChain = MemorySegment.NULL
    private var backBuffer = MemorySegment.NULL
    private var renderTarget = MemorySegment.NULL
    private var depthView = MemorySegment.NULL
    private var staging = MemorySegment.NULL
    private var constants = MemorySegment.NULL
    private var vertexShader = MemorySegment.NULL
    private var pixelShader = MemorySegment.NULL
    private var inputLayout = MemorySegment.NULL
    private var rasterizer = MemorySegment.NULL
    private val gpuMeshes = HashMap<Geometry, GpuMesh>()
    private val matrix = Mat4.multiply(
        Mat4.perspectiveRightHanded(0.78f, width.toFloat() / height, 0.1f, 150f),
        Mat4.lookAtRightHanded(camera, target, Vec3(0f, 1f, 0f)),
    )
    private val constantData = arena.allocate(176, 16)
    private val onePointer = arena.allocate(ADDRESS)
    private val stride = arena.allocate(JAVA_INT).also { it.set(JAVA_INT, 0, 24) }
    private val offset = arena.allocate(JAVA_INT)
    private val clear = arena.allocate(16, 4).also { it.floats(0, floatArrayOf(.025f, .04f, .075f, 1f)) }
    private val viewport = api.record("D3D11_VIEWPORT").apply {
        set("Width", width.toFloat()); set("Height", height.toFloat()); set("MaxDepth", 1f)
    }
    private val mapped = api.record("D3D11_MAPPED_SUBRESOURCE")
    private var closed = false
    var driver: String = "hardware"
        private set

    init {
        try {
            val desc = api.record("DXGI_SWAP_CHAIN_DESC").apply {
                set("BufferDesc.Width", width); set("BufferDesc.Height", height)
                set("BufferDesc.Format", 28) // R8G8B8A8_UNORM
                set("SampleDesc.Count", 1); set("BufferUsage", 0x20)
                set("BufferCount", 2); set("OutputWindow", hwnd); set("Windowed", 1)
            }
            val outputs = List(3) { arena.allocate(ADDRESS) }
            val feature = arena.allocate(JAVA_INT)
            val levels = arena.allocate(JAVA_INT).also { it.set(JAVA_INT, 0, 0xb000) }
            fun createDevice(driverType: Int): Int = api.function("D3D11CreateDeviceAndSwapChain", "d3d11.dll")
                .invokeWithArguments(MemorySegment.NULL, driverType, MemorySegment.NULL, 0, levels, 1, 7,
                    desc.memory, outputs[0], outputs[1], feature, outputs[2]) as Int
            fun discardOutputs() {
                outputs.zip(listOf("IDXGISwapChain", "ID3D11Device", "ID3D11DeviceContext")).forEach { (out, type) ->
                    val p = out.get(ADDRESS, 0)
                    if (p.address() != 0L) api.call(p, type, "Release")
                    out.set(ADDRESS, 0, MemorySegment.NULL)
                }
            }
            var result = createDevice(1)
            if (result < 0) {
                discardOutputs()
                driver = "WARP"
                result = createDevice(5)
            }
            if (result < 0) { discardOutputs(); hr(result, "D3D11CreateDeviceAndSwapChain") }
            swapChain = own(outputs[0].get(ADDRESS, 0), "IDXGISwapChain")
            device = own(outputs[1].get(ADDRESS, 0), "ID3D11Device")
            context = own(outputs[2].get(ADDRESS, 0), "ID3D11DeviceContext")
            println("DIRECTX_BACKEND=D3D11 DRIVER=$driver FEATURE_LEVEL=0x${feature.get(JAVA_INT, 0).toString(16)}")
            val iid = Guid.parse(api.interfaceIid("ID3D11Texture2D")).allocate(arena)
            backBuffer = output("ID3D11Texture2D") { api.call(swapChain, "IDXGISwapChain", "GetBuffer", 0, iid, it) }
            renderTarget = deviceObject("CreateRenderTargetView", "ID3D11RenderTargetView", backBuffer, MemorySegment.NULL)
            val depth = texture(45, 0, 0x40, 0) // D24_UNORM_S8_UINT / DEFAULT / DEPTH_STENCIL
            depthView = deviceObject("CreateDepthStencilView", "ID3D11DepthStencilView", depth, MemorySegment.NULL)
            staging = texture(28, 3, 0, 0x20000) // STAGING / CPU_ACCESS_READ
            val rasterDesc = api.record("D3D11_RASTERIZER_DESC").apply {
                set("FillMode", 3); set("CullMode", 3); set("FrontCounterClockwise", 1); set("DepthClipEnable", 1)
            }
            rasterizer = deviceObject("CreateRasterizerState", "ID3D11RasterizerState", rasterDesc.memory)
            compile("VSMain", "vs_5_0") { bytes, size ->
                vertexShader = deviceObject("CreateVertexShader", "ID3D11VertexShader", bytes, size, MemorySegment.NULL)
                val first = api.record("D3D11_INPUT_ELEMENT_DESC")
                val elements = arena.allocate(first.memory.byteSize() * 2, first.definition.alignment!!)
                for ((i, name) in listOf("POSITION", "NORMAL").withIndex()) {
                    val element = api.record("D3D11_INPUT_ELEMENT_DESC").apply {
                        set("SemanticName", arena.allocateFrom(name)); set("Format", 6); set("AlignedByteOffset", i * 12)
                    }
                    elements.asSlice(i * first.memory.byteSize(), first.memory.byteSize()).copyFrom(element.memory)
                }
                inputLayout = deviceObject("CreateInputLayout", "ID3D11InputLayout", elements, 2, bytes, size)
            }
            compile("PSMain", "ps_5_0") { bytes, size ->
                pixelShader = deviceObject("CreatePixelShader", "ID3D11PixelShader", bytes, size, MemorySegment.NULL)
            }
            constants = buffer(176, 4, null)
            for (geometry in geometries.distinct()) {
                val mesh = geometry.mesh()
                gpuMeshes[geometry] = GpuMesh(
                    buffer(mesh.interleavedVertices.size * 4, 1, MemorySegment.ofArray(mesh.interleavedVertices)),
                    buffer(mesh.indices.size * 4, 2, MemorySegment.ofArray(mesh.indices)), mesh.indexCount,
                )
            }
        } catch (failure: Throwable) {
            runCatching(::close).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    fun render(objects: List<VisualBody>) {
        check(!closed)
        onePointer.set(ADDRESS, 0, renderTarget)
        ctx("OMSetRenderTargets", 1, onePointer, depthView)
        ctx("RSSetViewports", 1, viewport.memory)
        ctx("RSSetState", rasterizer)
        ctx("ClearRenderTargetView", renderTarget, clear)
        ctx("ClearDepthStencilView", depthView, 1, 1f, 0.toByte())
        ctx("IASetInputLayout", inputLayout)
        ctx("IASetPrimitiveTopology", 4)
        ctx("VSSetShader", vertexShader, MemorySegment.NULL, 0)
        ctx("PSSetShader", pixelShader, MemorySegment.NULL, 0)
        onePointer.set(ADDRESS, 0, constants)
        ctx("VSSetConstantBuffers", 0, 1, onePointer)
        ctx("PSSetConstantBuffers", 0, 1, onePointer)
        for (body in objects) {
            if (!body.visible) continue
            val mesh = gpuMeshes.getValue(body.geometry)
            val model = modelMatrix(body.position, body.rotation)
            constantData.floats(0, Mat4.multiply(matrix, model))
            constantData.floats(64, model)
            constantData.floats(128, floatArrayOf(-8f, 18f, 10f, 1f))
            constantData.floats(144, floatArrayOf(camera.x, camera.y, camera.z, 1f))
            constantData.floats(160, floatArrayOf(body.color.x, body.color.y, body.color.z, if (body.grid) 1f else 0f))
            ctx("UpdateSubresource", constants, 0, MemorySegment.NULL, constantData, 0, 0)
            onePointer.set(ADDRESS, 0, mesh.vertices)
            ctx("IASetVertexBuffers", 0, 1, onePointer, stride, offset)
            ctx("IASetIndexBuffer", mesh.indices, 42, 0)
            ctx("DrawIndexed", mesh.count, 0, 0)
        }
    }

    /** Captures actual GPU output before Present. RowPitch is supplied by the D3D driver. */
    fun capture(path: Path): FrameCapture {
        ctx("CopyResource", staging, backBuffer)
        hr(ctx("Map", staging, 0, 1, 0, mapped.memory) as Int, "Map(readback)")
        val pixels = IntArray(width * height)
        try {
            val rowPitch = mapped.memory.get(JAVA_INT, mapped.offset("RowPitch"))
            val pointer = mapped.memory.get(ADDRESS, mapped.offset("pData"))
            check(pointer.address() != 0L && rowPitch >= width * 4)
            val bytes = pointer.reinterpret(rowPitch.toLong() * height)
            for (y in 0 until height) for (x in 0 until width) {
                val at = y.toLong() * rowPitch + x * 4
                fun component(i: Int) = bytes.get(JAVA_BYTE, at + i).toInt() and 255
                pixels[y * width + x] = (component(0) shl 16) or (component(1) shl 8) or component(2)
            }
        } finally { ctx("Unmap", staging, 0) }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        check(ImageIO.write(image, "png", path.toFile()))
        val count = pixels.count { colorDistance(it, 0x060a13) > 24 }
        check(count > 5000) { "GPU frame is empty: only $count non-clear pixels ($path)" }
        println("GPU_CAPTURE=$path NON_CLEAR_PIXELS=$count")
        return FrameCapture(pixels, count)
    }

    fun present(hidden: Boolean) = hr(api.call(swapChain, "IDXGISwapChain", "Present", if (hidden) 0 else 1, 0) as Int, "Present")
    fun checkHealth() = hr(api.call(device, "ID3D11Device", "GetDeviceRemovedReason") as Int, "GetDeviceRemovedReason")

    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        fun release(block: () -> Unit) { try { block() } catch (next: Throwable) {
            if (failure == null) failure = next else failure.addSuppressed(next)
        } }
        if (context.address() != 0L) { release { ctx("ClearState") }; release { ctx("Flush") } }
        owned.asReversed().forEach { (pointer, type) -> release { api.call(pointer, type, "Release") } }
        owned.clear()
        release { arena.close() }
        failure?.let { throw it }
    }

    private fun ctx(method: String, vararg args: Any): Any? = api.call(context, "ID3D11DeviceContext", method, *args)
    private fun own(pointer: MemorySegment, type: String): MemorySegment {
        check(pointer.address() != 0L) { "$type returned null" }
        owned.add(pointer to type)
        return pointer
    }
    private fun output(type: String, call: (MemorySegment) -> Any?): MemorySegment = Arena.ofConfined().use { temporary ->
        val out = temporary.allocate(ADDRESS)
        val result = call(out) as Int
        val pointer = out.get(ADDRESS, 0)
        if (result < 0) {
            if (pointer.address() != 0L) api.call(pointer, type, "Release")
            hr(result, "create $type")
        }
        own(pointer, type)
    }
    private fun deviceObject(method: String, type: String, vararg args: Any): MemorySegment =
        output(type) { api.call(device, "ID3D11Device", method, *args, it) }

    private fun texture(format: Int, usage: Int, flags: Int, cpu: Int): MemorySegment {
        val desc = api.record("D3D11_TEXTURE2D_DESC").apply {
            set("Width", width); set("Height", height); set("MipLevels", 1); set("ArraySize", 1)
            set("Format", format); set("SampleDesc.Count", 1); set("Usage", usage)
            set("BindFlags", flags); set("CPUAccessFlags", cpu)
        }
        return deviceObject("CreateTexture2D", "ID3D11Texture2D", desc.memory, MemorySegment.NULL)
    }

    private fun buffer(size: Int, flags: Int, initial: MemorySegment?): MemorySegment = Arena.ofConfined().use { temporary ->
        val desc = api.record("D3D11_BUFFER_DESC").apply {
            set("ByteWidth", size); set("Usage", if (initial == null) 0 else 1); set("BindFlags", flags)
        }
        val data = if (initial == null) MemorySegment.NULL else {
            val native = temporary.allocate(size.toLong(), 4).also { it.copyFrom(initial) }
            api.record("D3D11_SUBRESOURCE_DATA").apply { set("pSysMem", native) }.memory
        }
        deviceObject("CreateBuffer", "ID3D11Buffer", desc.memory, data)
    }

    private fun compile(entry: String, target: String, use: (MemorySegment, Long) -> Unit) {
        Arena.ofConfined().use { temporary ->
            val source = temporary.allocateFrom(SHADER)
            val code = temporary.allocate(ADDRESS)
            val errors = temporary.allocate(ADDRESS)
            val result = api.function("D3DCompile", "d3dcompiler_47.dll").invokeWithArguments(source,
                SHADER.toByteArray().size.toLong(), MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
                temporary.allocateFrom(entry), temporary.allocateFrom(target), 1 shl 11, 0, code, errors) as Int
            val errorBlob = errors.get(ADDRESS, 0)
            val blob = code.get(ADDRESS, 0)
            try {
                if (result < 0) {
                    val diagnostic = if (errorBlob.address() == 0L) "No compiler diagnostic" else {
                        val p = api.call(errorBlob, "ID3D10Blob", "GetBufferPointer") as MemorySegment
                        val n = api.call(errorBlob, "ID3D10Blob", "GetBufferSize") as Long
                        p.reinterpret(n).toArray(JAVA_BYTE).decodeToString()
                    }
                    error("D3DCompile($entry): $diagnostic")
                }
                check(blob.address() != 0L)
                use(api.call(blob, "ID3D10Blob", "GetBufferPointer") as MemorySegment,
                    api.call(blob, "ID3D10Blob", "GetBufferSize") as Long)
            } finally {
                if (errorBlob.address() != 0L) api.call(errorBlob, "ID3D10Blob", "Release")
                if (blob.address() != 0L) api.call(blob, "ID3D10Blob", "Release")
            }
        }
    }
    private data class GpuMesh(val vertices: MemorySegment, val indices: MemorySegment, val count: Int)
}

private class CatalogCalls(private val arena: Arena) {
    private val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_11)
    private val methods = HashMap<Triple<Long, String, String>, MethodHandle>()
    private val functions = HashMap<Pair<String, String>, MethodHandle>()
    private val invokers = profile.catalogs.associateWith { NativeApiInvoker(it, types = profile.types) }
    fun interfaceIid(name: String): String = profile.catalogs.firstNotNullOf { it.interfaceDeclaration(name)?.iid }
    fun call(pointer: MemorySegment, type: String, method: String, vararg args: Any): Any? {
        val handle = methods.getOrPut(Triple(pointer.address(), type, method)) {
            val catalog = profile.catalogs.first { it.interfaceDeclaration(type) != null }
            invokers.getValue(catalog).comMethod(pointer, type, method)
        }
        return handle.invokeWithArguments(pointer, *args)
    }
    fun function(name: String, dll: String): MethodHandle = functions.getOrPut(name to dll) {
        val catalog = profile.catalogs.first { it.function(name) != null }
        NativeApiInvoker(catalog, SymbolLookup.libraryLookup(dll, arena), types = profile.types).function(name)
    }
    private fun definition(name: String): NativeRecordDeclaration = profile.catalogs.firstNotNullOf { it.records[name] }
    fun record(name: String): Record = definition(name).let { Record(it, arena.allocate(it.size!!, it.alignment!!)) }
    inner class Record(val definition: NativeRecordDeclaration, val memory: MemorySegment) {
        fun offset(path: String): Long {
            var record = definition
            var offset = 0L
            val names = path.split('.')
            names.forEachIndexed { index, name ->
                val field = record.fields.single { it.name == name }
                offset += field.offsetBits!! / 8
                if (index < names.lastIndex) record = definition(field.type.removePrefix("struct "))
            }
            return offset
        }
        fun set(name: String, value: Int) = memory.set(JAVA_INT, offset(name), value)
        fun set(name: String, value: Float) = memory.set(JAVA_FLOAT, offset(name), value)
        fun set(name: String, value: MemorySegment) = memory.set(ADDRESS, offset(name), value)
    }
}

internal data class FrameCapture(val pixels: IntArray, val nonClearPixels: Int) {
    fun changedPixels(other: FrameCapture): Int = pixels.indices.count { colorDistance(pixels[it], other.pixels[it]) > 30 }
}
private fun colorDistance(a: Int, b: Int): Int = abs((a shr 16 and 255) - (b shr 16 and 255)) +
    abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))
private fun hr(result: Int, operation: String) { check(result >= 0) { "$operation failed: 0x${result.toUInt().toString(16)}" } }
private fun MemorySegment.floats(offset: Long, values: FloatArray) {
    values.forEachIndexed { index, value -> set(JAVA_FLOAT, offset + index * 4, value) }
}

private val SHADER = """
    cbuffer Scene : register(b0) {
        column_major float4x4 mvp;
        column_major float4x4 model;
        float4 lightPosition;
        float4 cameraPosition;
        float4 baseColor;
    };
    struct VIn { float3 position : POSITION; float3 normal : NORMAL; };
    struct VOut { float4 position : SV_POSITION; float3 world : TEXCOORD0; float3 normal : TEXCOORD1; };
    VOut VSMain(VIn v) {
        VOut o;
        o.position = mul(mvp, float4(v.position, 1));
        o.world = mul(model, float4(v.position, 1)).xyz;
        o.normal = mul((float3x3)model, v.normal);
        return o;
    }
    float4 PSMain(VOut v) : SV_TARGET {
        float3 n = normalize(v.normal);
        float3 l = normalize(lightPosition.xyz - v.world);
        float3 eye = normalize(cameraPosition.xyz - v.world);
        float3 color = baseColor.rgb;
        if (baseColor.w > 0.5 && n.y > 0.9) {
            float2 d = abs(frac(v.world.xz - 0.5) - 0.5) / max(fwidth(v.world.xz), 0.0001);
            float gridLine = 1 - saturate(min(d.x, d.y));
            color *= 0.8 + 0.55 * gridLine;
        }
        float light = 0.28 + 0.72 * max(dot(n, l), 0);
        float shine = pow(max(dot(n, normalize(l + eye)), 0), 48) * 0.2;
        return float4(pow(saturate(color * light + shine), 1.0 / 2.2), 1);
    }
""".trimIndent()
