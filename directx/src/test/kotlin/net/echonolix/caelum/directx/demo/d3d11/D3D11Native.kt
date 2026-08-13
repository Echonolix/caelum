package net.echonolix.caelum.directx.demo

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

internal object D3D11Native {
    private val linker = Linker.nativeLinker()
    private val libraryArena = Arena.ofAuto()
    private val methodCache = ConcurrentHashMap<MethodKey, MethodHandle>()

    private val d3d11Lookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("d3d11.dll", libraryArena) }
    private val compilerLookup: SymbolLookup by lazy {
        runCatching { SymbolLookup.libraryLookup("d3dcompiler_47.dll", libraryArena) }
            .getOrElse { SymbolLookup.libraryLookup("d3dcompiler_43.dll", libraryArena) }
    }

    private val createDeviceAndSwapChain: MethodHandle by lazy {
        linker.downcallHandle(
            d3d11Lookup.find("D3D11CreateDeviceAndSwapChain").orElseThrow(),
            D3D11Descriptors.CREATE_DEVICE_AND_SWAP_CHAIN,
        )
    }

    private val compile: MethodHandle by lazy {
        linker.downcallHandle(
            compilerLookup.find("D3DCompile").orElseThrow(),
            D3D11Descriptors.D3D_COMPILE,
        )
    }

    internal fun ensureAvailable() {
        createDeviceAndSwapChain
        compile
    }

    internal fun createDeviceAndSwapChain(
        driverType: Int,
        swapChainDesc: MemorySegment,
        swapChainOut: MemorySegment,
        deviceOut: MemorySegment,
        featureLevelOut: MemorySegment,
        contextOut: MemorySegment,
    ): Int {
        swapChainOut.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
        deviceOut.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
        featureLevelOut.set(ValueLayout.JAVA_INT, 0L, 0)
        contextOut.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
        return createDeviceAndSwapChain.invokeWithArguments(
            MemorySegment.NULL,
            driverType,
            MemorySegment.NULL,
            0,
            FEATURE_LEVELS,
            FEATURE_LEVEL_COUNT,
            D3D11_SDK_VERSION,
            swapChainDesc,
            swapChainOut,
            deviceOut,
            featureLevelOut,
            contextOut,
        ) as Int
    }

    internal fun d3dCompile(
        source: MemorySegment,
        sourceSize: Long,
        entryPoint: MemorySegment,
        target: MemorySegment,
        codeOut: MemorySegment,
        errorsOut: MemorySegment,
    ): Int = compile.invokeWithArguments(
        source,
        sourceSize,
        MemorySegment.NULL,
        MemorySegment.NULL,
        MemorySegment.NULL,
        entryPoint,
        target,
        D3DCOMPILE_ENABLE_STRICTNESS,
        0,
        codeOut,
        errorsOut,
    ) as Int

    internal fun comInt(
        instance: MemorySegment,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg arguments: Any,
    ): Int = method(instance, slot, descriptor).invokeWithArguments(instance, *arguments) as Int

    internal fun comLong(
        instance: MemorySegment,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg arguments: Any,
    ): Long = method(instance, slot, descriptor).invokeWithArguments(instance, *arguments) as Long

    internal fun comAddress(
        instance: MemorySegment,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg arguments: Any,
    ): MemorySegment = method(instance, slot, descriptor).invokeWithArguments(instance, *arguments) as MemorySegment

    internal fun comVoid(
        instance: MemorySegment,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg arguments: Any,
    ) {
        method(instance, slot, descriptor).invokeWithArguments(instance, *arguments)
    }

    internal fun release(instance: MemorySegment) {
        if (instance == MemorySegment.NULL || instance.address() == 0L) return
        comInt(instance, D3D11Slots.UNKNOWN_RELEASE, D3D11Descriptors.RELEASE)
    }

    internal fun blobPointer(blob: MemorySegment): MemorySegment =
        comAddress(blob, D3D11Slots.BLOB_GET_BUFFER_POINTER, D3D11Descriptors.BLOB_GET_POINTER)

    internal fun blobSize(blob: MemorySegment): Long =
        comLong(blob, D3D11Slots.BLOB_GET_BUFFER_SIZE, D3D11Descriptors.BLOB_GET_SIZE)

    internal fun blobText(blob: MemorySegment): String {
        val size = blobSize(blob)
        if (size == 0L) return ""
        val bytes = blobPointer(blob).reinterpret(size).toArray(ValueLayout.JAVA_BYTE)
        val length = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return String(bytes, 0, length, StandardCharsets.UTF_8)
    }

    private fun method(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor): MethodHandle {
        require(instance != MemorySegment.NULL && instance.address() != 0L) { "COM interface pointer must be non-null" }
        require(slot >= 0) { "COM vtable slot must be non-negative" }
        val vtable = instance.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
        require(vtable != MemorySegment.NULL && vtable.address() != 0L) { "COM vtable pointer must be non-null" }
        val function = vtable.reinterpret((slot + 1L) * ValueLayout.ADDRESS.byteSize())
            .getAtIndex(ValueLayout.ADDRESS, slot.toLong())
        require(function != MemorySegment.NULL && function.address() != 0L) { "COM vtable slot $slot is null" }
        return methodCache.computeIfAbsent(MethodKey(function.address(), descriptor.toString())) {
            linker.downcallHandle(function, descriptor)
        }
    }

    private data class MethodKey(val address: Long, val descriptor: String)

    private val FEATURE_LEVELS: MemorySegment = Arena.global().allocate(4L * Int.SIZE_BYTES, ValueLayout.JAVA_INT.byteAlignment()).also {
        it.setAtIndex(ValueLayout.JAVA_INT, 0L, D3D_FEATURE_LEVEL_11_1)
        it.setAtIndex(ValueLayout.JAVA_INT, 1L, D3D_FEATURE_LEVEL_11_0)
        it.setAtIndex(ValueLayout.JAVA_INT, 2L, D3D_FEATURE_LEVEL_10_1)
        it.setAtIndex(ValueLayout.JAVA_INT, 3L, D3D_FEATURE_LEVEL_10_0)
    }

    private const val FEATURE_LEVEL_COUNT = 4
}

internal object D3D11Descriptors {
    private val ADDRESS = ValueLayout.ADDRESS
    private val INT = ValueLayout.JAVA_INT
    private val LONG = ValueLayout.JAVA_LONG
    private val FLOAT = ValueLayout.JAVA_FLOAT
    private val BYTE = ValueLayout.JAVA_BYTE

    val CREATE_DEVICE_AND_SWAP_CHAIN: FunctionDescriptor = FunctionDescriptor.of(
        INT,
        ADDRESS,
        INT,
        ADDRESS,
        INT,
        ADDRESS,
        INT,
        INT,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
    )
    val D3D_COMPILE: FunctionDescriptor = FunctionDescriptor.of(
        INT,
        ADDRESS,
        LONG,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        INT,
        INT,
        ADDRESS,
        ADDRESS,
    )
    val RELEASE: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS)
    val BLOB_GET_POINTER: FunctionDescriptor = FunctionDescriptor.of(ADDRESS, ADDRESS)
    val BLOB_GET_SIZE: FunctionDescriptor = FunctionDescriptor.of(LONG, ADDRESS)
    val NO_ARGUMENTS: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS)
    val VOID_NO_ARGUMENTS: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS)
    val PRESENT: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, INT, INT)
    val GET_BUFFER: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS, ADDRESS)

    val CREATE_BUFFER: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
    val CREATE_TEXTURE_2D: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
    val CREATE_VIEW: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
    val CREATE_RASTERIZER_STATE: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS)
    val CREATE_INPUT_LAYOUT: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, INT, ADDRESS, LONG, ADDRESS)
    val CREATE_SHADER: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, LONG, ADDRESS, ADDRESS)

    val SET_CONSTANT_BUFFERS: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, INT, INT, ADDRESS)
    val SET_SHADER: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, INT)
    val DRAW_INDEXED: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, INT, INT, INT)
    val MAP: FunctionDescriptor = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, INT, INT, INT, ADDRESS)
    val UNMAP: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, INT)
    val ONE_OBJECT: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS)
    val ONE_INT: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, INT)
    val IA_SET_VERTEX_BUFFERS: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, INT, INT, ADDRESS, ADDRESS, ADDRESS)
    val IA_SET_INDEX_BUFFER: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, INT, INT)
    val OM_SET_RENDER_TARGETS: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, INT, ADDRESS, ADDRESS)
    val RS_SET_VIEWPORTS: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, INT, ADDRESS)
    val UPDATE_SUBRESOURCE: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, INT, ADDRESS, ADDRESS, INT, INT)
    val COPY_RESOURCE: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS)
    val CLEAR_RTV: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS)
    val CLEAR_DSV: FunctionDescriptor = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, INT, FLOAT, BYTE)
}

internal object D3D11Layouts {
    private val ADDRESS = ValueLayout.ADDRESS
    private val INT = ValueLayout.JAVA_INT
    private val FLOAT = ValueLayout.JAVA_FLOAT

    val GUID: MemoryLayout = MemoryLayout.structLayout(
        INT.withName("Data1"),
        ValueLayout.JAVA_SHORT.withName("Data2"),
        ValueLayout.JAVA_SHORT.withName("Data3"),
        MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE).withName("Data4"),
    ).withName("GUID")

    val DXGI_RATIONAL: MemoryLayout = MemoryLayout.structLayout(INT, INT)
    val DXGI_MODE_DESC: MemoryLayout = MemoryLayout.structLayout(
        INT, INT, DXGI_RATIONAL, INT, INT, INT,
    ).withName("DXGI_MODE_DESC")
    val DXGI_SAMPLE_DESC: MemoryLayout = MemoryLayout.structLayout(INT, INT).withName("DXGI_SAMPLE_DESC")
    val DXGI_SWAP_CHAIN_DESC: MemoryLayout = MemoryLayout.structLayout(
        DXGI_MODE_DESC,
        DXGI_SAMPLE_DESC,
        INT,
        INT,
        MemoryLayout.paddingLayout(4),
        ADDRESS,
        INT,
        INT,
        INT,
        INT,
        MemoryLayout.paddingLayout(4),
    ).withName("DXGI_SWAP_CHAIN_DESC")

    val D3D11_BUFFER_DESC: MemoryLayout = MemoryLayout.structLayout(INT, INT, INT, INT, INT, INT)
    val D3D11_SUBRESOURCE_DATA: MemoryLayout = MemoryLayout.structLayout(ADDRESS, INT, INT)
        .withByteAlignment(8)
    val D3D11_TEXTURE2D_DESC: MemoryLayout = MemoryLayout.structLayout(
        INT, INT, INT, INT, INT, DXGI_SAMPLE_DESC, INT, INT, INT, INT,
    )
    val D3D11_RASTERIZER_DESC: MemoryLayout = MemoryLayout.structLayout(
        INT, INT, INT, INT, FLOAT, FLOAT, INT, INT, INT, INT,
    ).withName("D3D11_RASTERIZER_DESC")
    val D3D11_INPUT_ELEMENT_DESC: MemoryLayout = MemoryLayout.structLayout(
        ADDRESS, INT, INT, INT, INT, INT, INT,
    ).withByteAlignment(8)
    val D3D11_VIEWPORT: MemoryLayout = MemoryLayout.structLayout(FLOAT, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT)
    val D3D11_MAPPED_SUBRESOURCE: MemoryLayout = MemoryLayout.structLayout(ADDRESS, INT, INT).withByteAlignment(8)

    fun initializeSwapChainDesc(desc: MemorySegment, hwnd: MemorySegment, width: Int, height: Int) {
        desc.fill(0)
        desc.set(INT, 0L, width)
        desc.set(INT, 4L, height)
        desc.set(INT, 16L, DXGI_FORMAT_R8G8B8A8_UNORM)
        desc.set(INT, 20L, DXGI_MODE_SCANLINE_ORDER_UNSPECIFIED)
        desc.set(INT, 24L, DXGI_MODE_SCALING_UNSPECIFIED)
        desc.set(INT, 28L, 1)
        desc.set(INT, 32L, 0)
        desc.set(INT, 36L, DXGI_USAGE_RENDER_TARGET_OUTPUT)
        desc.set(INT, 40L, 2)
        desc.set(ADDRESS, 48L, hwnd)
        desc.set(INT, 56L, 1)
        desc.set(INT, 60L, DXGI_SWAP_EFFECT_DISCARD)
        desc.set(INT, 64L, 0)
    }

    fun initializeTexture2DDesc(
        desc: MemorySegment,
        width: Int,
        height: Int,
        format: Int,
        usage: Int,
        bindFlags: Int,
        cpuAccessFlags: Int,
    ) {
        desc.fill(0)
        desc.set(INT, 0L, width)
        desc.set(INT, 4L, height)
        desc.set(INT, 8L, 1)
        desc.set(INT, 12L, 1)
        desc.set(INT, 16L, format)
        desc.set(INT, 20L, 1)
        desc.set(INT, 24L, 0)
        desc.set(INT, 28L, usage)
        desc.set(INT, 32L, bindFlags)
        desc.set(INT, 36L, cpuAccessFlags)
        desc.set(INT, 40L, 0)
    }

    fun initializeBufferDesc(desc: MemorySegment, byteWidth: Int, usage: Int, bindFlags: Int) {
        desc.fill(0)
        desc.set(INT, 0L, byteWidth)
        desc.set(INT, 4L, usage)
        desc.set(INT, 8L, bindFlags)
    }

    /**
     * Matches the mesh contract: outward-facing triangles are counter-clockwise.
     * D3D11's null/default rasterizer state uses FrontCounterClockwise=FALSE,
     * so relying on it would cull the teapot's exterior and expose its far side.
     */
    fun initializeTeapotRasterizerDesc(desc: MemorySegment) {
        require(desc.byteSize() >= D3D11_RASTERIZER_DESC.byteSize())
        desc.fill(0)
        desc.set(INT, 0L, D3D11_FILL_SOLID)
        desc.set(INT, 4L, D3D11_CULL_BACK)
        desc.set(INT, 8L, 1)
        desc.set(INT, 24L, 1)
    }

    fun initializeInputElement(
        element: MemorySegment,
        semanticName: MemorySegment,
        semanticIndex: Int,
        format: Int,
        alignedByteOffset: Int,
    ) {
        element.fill(0)
        element.set(ADDRESS, 0L, semanticName)
        element.set(INT, 8L, semanticIndex)
        element.set(INT, 12L, format)
        element.set(INT, 16L, 0)
        element.set(INT, 20L, alignedByteOffset)
        element.set(INT, 24L, D3D11_INPUT_PER_VERTEX_DATA)
        element.set(INT, 28L, 0)
    }

    fun initializeViewport(viewport: MemorySegment, width: Int, height: Int) {
        viewport.set(FLOAT, 0L, 0f)
        viewport.set(FLOAT, 4L, 0f)
        viewport.set(FLOAT, 8L, width.toFloat())
        viewport.set(FLOAT, 12L, height.toFloat())
        viewport.set(FLOAT, 16L, 0f)
        viewport.set(FLOAT, 20L, 1f)
    }

    fun writeGuid(segment: MemorySegment, value: GuidValue) {
        segment.set(INT, 0L, value.data1)
        segment.set(ValueLayout.JAVA_SHORT, 4L, value.data2.toShort())
        segment.set(ValueLayout.JAVA_SHORT, 6L, value.data3.toShort())
        value.data4.forEachIndexed { index, byte -> segment.setAtIndex(ValueLayout.JAVA_BYTE, 8L + index, byte) }
    }
}

internal object D3D11Slots {
    const val UNKNOWN_RELEASE = 2
    const val BLOB_GET_BUFFER_POINTER = 3
    const val BLOB_GET_BUFFER_SIZE = 4

    const val SWAP_CHAIN_PRESENT = 8
    const val SWAP_CHAIN_GET_BUFFER = 9

    const val DEVICE_CREATE_BUFFER = 3
    const val DEVICE_CREATE_TEXTURE2D = 5
    const val DEVICE_CREATE_RENDER_TARGET_VIEW = 9
    const val DEVICE_CREATE_DEPTH_STENCIL_VIEW = 10
    const val DEVICE_CREATE_INPUT_LAYOUT = 11
    const val DEVICE_CREATE_VERTEX_SHADER = 12
    const val DEVICE_CREATE_PIXEL_SHADER = 15
    const val DEVICE_CREATE_RASTERIZER_STATE = 22
    const val DEVICE_GET_REMOVED_REASON = 39

    const val CONTEXT_VS_SET_CONSTANT_BUFFERS = 7
    const val CONTEXT_PS_SET_SHADER = 9
    const val CONTEXT_VS_SET_SHADER = 11
    const val CONTEXT_DRAW_INDEXED = 12
    const val CONTEXT_MAP = 14
    const val CONTEXT_UNMAP = 15
    const val CONTEXT_PS_SET_CONSTANT_BUFFERS = 16
    const val CONTEXT_IA_SET_INPUT_LAYOUT = 17
    const val CONTEXT_IA_SET_VERTEX_BUFFERS = 18
    const val CONTEXT_IA_SET_INDEX_BUFFER = 19
    const val CONTEXT_IA_SET_PRIMITIVE_TOPOLOGY = 24
    const val CONTEXT_OM_SET_RENDER_TARGETS = 33
    const val CONTEXT_RS_SET_STATE = 43
    const val CONTEXT_RS_SET_VIEWPORTS = 44
    const val CONTEXT_COPY_RESOURCE = 47
    const val CONTEXT_UPDATE_SUBRESOURCE = 48
    const val CONTEXT_CLEAR_RTV = 50
    const val CONTEXT_CLEAR_DSV = 53
    const val CONTEXT_CLEAR_STATE = 110
    const val CONTEXT_FLUSH = 111
}

internal data class GuidValue(
    val data1: Int,
    val data2: Int,
    val data3: Int,
    val data4: ByteArray,
)

internal val IID_ID3D11_TEXTURE2D = GuidValue(
    data1 = 0x6f15aaf2,
    data2 = 0xd208,
    data3 = 0x4e89,
    data4 = byteArrayOf(0x9a.toByte(), 0xb4.toByte(), 0x48, 0x95.toByte(), 0x35, 0xd3.toByte(), 0x4f, 0x9c.toByte()),
)

internal fun MemorySegment.requireComObject(name: String): MemorySegment {
    val pointer = get(ValueLayout.ADDRESS, 0L)
    check(pointer != MemorySegment.NULL && pointer.address() != 0L) { "$name returned a null COM object" }
    return MemorySegment.ofAddress(pointer.address())
}

internal fun Arena.utf8(value: String): MemorySegment {
    val bytes = (value + '\u0000').encodeToByteArray()
    return allocate(bytes.size.toLong(), 1L).also { it.copyFrom(MemorySegment.ofArray(bytes)) }
}

internal fun checkHResult(result: Int, operation: String) {
    check(result >= 0) { "$operation failed with ${hResultHex(result)}" }
}

internal fun hResultHex(result: Int): String = "0x${result.toUInt().toString(16).padStart(8, '0')}"

internal const val D3D_DRIVER_TYPE_HARDWARE = 1
internal const val D3D_DRIVER_TYPE_WARP = 5
internal const val D3D11_SDK_VERSION = 7
internal const val D3D_FEATURE_LEVEL_10_0 = 0xa000
internal const val D3D_FEATURE_LEVEL_10_1 = 0xa100
internal const val D3D_FEATURE_LEVEL_11_0 = 0xb000
internal const val D3D_FEATURE_LEVEL_11_1 = 0xb100
internal const val D3DCOMPILE_ENABLE_STRICTNESS = 1 shl 11

internal const val DXGI_FORMAT_R8G8B8A8_UNORM = 28
internal const val DXGI_FORMAT_R32G32B32_FLOAT = 6
internal const val DXGI_FORMAT_R32_UINT = 42
internal const val DXGI_FORMAT_D24_UNORM_S8_UINT = 45
internal const val DXGI_MODE_SCANLINE_ORDER_UNSPECIFIED = 0
internal const val DXGI_MODE_SCALING_UNSPECIFIED = 0
internal const val DXGI_USAGE_RENDER_TARGET_OUTPUT = 0x20
internal const val DXGI_SWAP_EFFECT_DISCARD = 0

internal const val D3D11_USAGE_DEFAULT = 0
internal const val D3D11_USAGE_IMMUTABLE = 1
internal const val D3D11_USAGE_STAGING = 3
internal const val D3D11_BIND_VERTEX_BUFFER = 0x1
internal const val D3D11_BIND_INDEX_BUFFER = 0x2
internal const val D3D11_BIND_CONSTANT_BUFFER = 0x4
internal const val D3D11_BIND_DEPTH_STENCIL = 0x40
internal const val D3D11_CPU_ACCESS_READ = 0x20000
internal const val D3D11_INPUT_PER_VERTEX_DATA = 0
internal const val D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST = 4
internal const val D3D11_FILL_SOLID = 3
internal const val D3D11_CULL_BACK = 3
internal const val D3D11_CLEAR_DEPTH = 0x1
internal const val D3D11_MAP_READ = 1
