package net.echonolix.caelum.directx.demo.d3d12

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

internal object D3D12Native {
    private val linker = Linker.nativeLinker()
    private val libraries = Arena.ofAuto()
    private val cache = ConcurrentHashMap<String, MethodHandle>()
    private val d3d12 = SymbolLookup.libraryLookup("d3d12.dll", libraries)
    private val dxgi = SymbolLookup.libraryLookup("dxgi.dll", libraries)
    private val compiler = runCatching { SymbolLookup.libraryLookup("d3dcompiler_47.dll", libraries) }
        .getOrElse { SymbolLookup.libraryLookup("d3dcompiler_43.dll", libraries) }

    private val createDevice = downcall(d3d12, "D3D12CreateDevice", D3D12Descriptors.CREATE_DEVICE)
    private val serializeRootSignature = downcall(d3d12, "D3D12SerializeRootSignature", D3D12Descriptors.SERIALIZE_ROOT_SIGNATURE)
    private val createFactory = downcall(dxgi, "CreateDXGIFactory2", D3D12Descriptors.CREATE_FACTORY)
    private val compile = downcall(compiler, "D3DCompile", D3D12Descriptors.D3D_COMPILE)

    fun ensureAvailable() { createDevice; serializeRootSignature; createFactory; compile }

    fun createDevice(adapter: MemorySegment, iid: MemorySegment, output: MemorySegment): Int =
        createDevice.invokeWithArguments(adapter, D3D_FEATURE_LEVEL_11_0, iid, output) as Int

    fun createFactory(iid: MemorySegment, output: MemorySegment): Int =
        createFactory.invokeWithArguments(0, iid, output) as Int

    fun serializeRootSignature(desc: MemorySegment, blob: MemorySegment, errors: MemorySegment): Int =
        serializeRootSignature.invokeWithArguments(desc, D3D_ROOT_SIGNATURE_VERSION_1, blob, errors) as Int

    fun compile(source: MemorySegment, size: Long, entry: MemorySegment, target: MemorySegment, blob: MemorySegment, errors: MemorySegment): Int =
        compile.invokeWithArguments(source, size, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
            entry, target, D3DCOMPILE_ENABLE_STRICTNESS, 0, blob, errors) as Int

    fun comInt(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor, vararg args: Any): Int =
        method(instance, slot, descriptor).invokeWithArguments(instance, *args) as Int
    fun comLong(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor, vararg args: Any): Long =
        method(instance, slot, descriptor).invokeWithArguments(instance, *args) as Long
    fun comAddress(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor, vararg args: Any): MemorySegment =
        method(instance, slot, descriptor).invokeWithArguments(instance, *args) as MemorySegment
    fun comStruct(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor, allocator: SegmentAllocator, vararg args: Any): MemorySegment =
        method(instance, slot, descriptor).invokeWithArguments(allocator, instance, *args) as MemorySegment
    fun comVoid(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor, vararg args: Any) {
        method(instance, slot, descriptor).invokeWithArguments(instance, *args)
    }

    fun queryInterface(instance: MemorySegment, iid: MemorySegment, output: MemorySegment): Int =
        comInt(instance, 0, D3D12Descriptors.QUERY_INTERFACE, iid, output)
    fun release(instance: MemorySegment) {
        if (instance.address() != 0L) comInt(instance, 2, D3D12Descriptors.RELEASE)
    }
    fun blobPointer(blob: MemorySegment): MemorySegment = comAddress(blob, 3, D3D12Descriptors.BLOB_POINTER)
    fun blobSize(blob: MemorySegment): Long = comLong(blob, 4, D3D12Descriptors.BLOB_SIZE)
    fun blobText(blob: MemorySegment): String {
        val bytes = blobPointer(blob).reinterpret(blobSize(blob)).toArray(ValueLayout.JAVA_BYTE)
        val length = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return String(bytes, 0, length, StandardCharsets.UTF_8)
    }

    private fun method(instance: MemorySegment, slot: Int, descriptor: FunctionDescriptor): MethodHandle {
        require(instance.address() != 0L)
        val table = instance.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0)
        val function = table.reinterpret((slot + 1L) * 8).getAtIndex(ValueLayout.ADDRESS, slot.toLong())
        return cache.computeIfAbsent("${function.address()}|$descriptor") { linker.downcallHandle(function, descriptor) }
    }
    private fun downcall(lookup: SymbolLookup, name: String, descriptor: FunctionDescriptor) =
        linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor)
}

internal object D3D12Descriptors {
    private val A = ValueLayout.ADDRESS; private val I = ValueLayout.JAVA_INT
    private val L = ValueLayout.JAVA_LONG; private val F = ValueLayout.JAVA_FLOAT
    private val CPU_HANDLE = java.lang.foreign.MemoryLayout.structLayout(L.withName("ptr"))
    val CREATE_DEVICE = FunctionDescriptor.of(I, A, I, A, A)
    val CREATE_FACTORY = FunctionDescriptor.of(I, I, A, A)
    val SERIALIZE_ROOT_SIGNATURE = FunctionDescriptor.of(I, A, I, A, A)
    val D3D_COMPILE = FunctionDescriptor.of(I, A, L, A, A, A, A, A, I, I, A, A)
    val QUERY_INTERFACE = FunctionDescriptor.of(I, A, A, A)
    val RELEASE = FunctionDescriptor.of(I, A)
    val BLOB_POINTER = FunctionDescriptor.of(A, A)
    val BLOB_SIZE = FunctionDescriptor.of(L, A)
    val HR_DESC_IID_OUT = FunctionDescriptor.of(I, A, A, A, A)
    val HR_INT_IID_OUT = FunctionDescriptor.of(I, A, I, A, A)
    val HR_CREATE_LIST = FunctionDescriptor.of(I, A, I, I, A, A, A, A)
    val HR_CREATE_RESOURCE = FunctionDescriptor.of(I, A, A, I, A, I, A, A, A)
    val HR_ROOT_SIGNATURE = FunctionDescriptor.of(I, A, I, A, L, A, A)
    val HR_FENCE = FunctionDescriptor.of(I, A, L, I, A, A)
    val HR_NO_ARGS = FunctionDescriptor.of(I, A)
    val HR_RESET_ALLOCATOR = HR_NO_ARGS
    val HR_RESET_LIST = FunctionDescriptor.of(I, A, A, A)
    val HR_SIGNAL = FunctionDescriptor.of(I, A, A, L)
    val HR_MAP = FunctionDescriptor.of(I, A, I, A, A)
    val VOID_UNMAP = FunctionDescriptor.ofVoid(A, I, A)
    val VOID_EXECUTE = FunctionDescriptor.ofVoid(A, I, A)
    val VOID_CREATE_VIEW = FunctionDescriptor.ofVoid(A, A, A, CPU_HANDLE)
    val UINT_ONE_INT = FunctionDescriptor.of(I, A, I)
    val UINT_NO_ARGS = FunctionDescriptor.of(I, A)
    val ULONG64_NO_ARGS = FunctionDescriptor.of(L, A)
    val ULONG64_RESOURCE = FunctionDescriptor.of(L, A)
    val CPU_HANDLE_RETURN = FunctionDescriptor.of(A, A, A)
    val PRESENT = FunctionDescriptor.of(I, A, I, I)
    val GET_BUFFER = FunctionDescriptor.of(I, A, I, A, A)
    val CREATE_SWAP_CHAIN_FOR_HWND = FunctionDescriptor.of(I, A, A, A, A, A, A, A)
    val ENUM_ADAPTER_BY_GPU_PREFERENCE = FunctionDescriptor.of(I, A, I, I, A, A)
    val VOID_BARRIERS = FunctionDescriptor.ofVoid(A, I, A)
    val VOID_VIEWPORTS = FunctionDescriptor.ofVoid(A, I, A)
    val VOID_ROOT_SIG = FunctionDescriptor.ofVoid(A, A)
    val VOID_ROOT_CONSTANTS = FunctionDescriptor.ofVoid(A, I, I, A, I)
    val VOID_TOPOLOGY = FunctionDescriptor.ofVoid(A, I)
    val VOID_VERTEX_BUFFERS = FunctionDescriptor.ofVoid(A, I, I, A)
    val VOID_INDEX_BUFFER = FunctionDescriptor.ofVoid(A, A)
    val VOID_RENDER_TARGETS = FunctionDescriptor.ofVoid(A, I, A, I, A)
    val VOID_CLEAR_RTV = FunctionDescriptor.ofVoid(A, CPU_HANDLE, A, I, A)
    val VOID_CLEAR_DSV = FunctionDescriptor.ofVoid(A, CPU_HANDLE, I, F, ValueLayout.JAVA_BYTE, I, A)
    val VOID_DRAW_INDEXED = FunctionDescriptor.ofVoid(A, I, I, I, I, I)
    val VOID_COPY_TEXTURE_REGION = FunctionDescriptor.ofVoid(A, A, I, I, I, A, A)
}

internal object D3D12Slots {
    const val FACTORY6_ENUM_ADAPTER_BY_GPU_PREFERENCE = 29
    const val FACTORY_CREATE_SWAP_CHAIN_FOR_HWND = 15
    const val SWAP_CHAIN_PRESENT = 8; const val SWAP_CHAIN_GET_BUFFER = 9
    const val SWAP_CHAIN3_GET_CURRENT_BACK_BUFFER_INDEX = 36
    const val DEVICE_CREATE_QUEUE = 8; const val DEVICE_CREATE_ALLOCATOR = 9
    const val DEVICE_CREATE_GRAPHICS_PSO = 10; const val DEVICE_CREATE_LIST = 12
    const val DEVICE_CREATE_DESCRIPTOR_HEAP = 14; const val DEVICE_GET_DESCRIPTOR_INCREMENT = 15
    const val DEVICE_CREATE_ROOT_SIGNATURE = 16; const val DEVICE_CREATE_RTV = 20
    const val DEVICE_CREATE_DSV = 21; const val DEVICE_CREATE_COMMITTED_RESOURCE = 27
    const val DEVICE_CREATE_FENCE = 36; const val DEVICE_REMOVED_REASON = 37
    const val HEAP_GET_CPU_START = 9
    const val RESOURCE_MAP = 8; const val RESOURCE_UNMAP = 9; const val RESOURCE_GPU_ADDRESS = 11
    const val ALLOCATOR_RESET = 8
    const val LIST_CLOSE = 9; const val LIST_RESET = 10; const val LIST_DRAW_INDEXED = 13
    const val LIST_COPY_TEXTURE_REGION = 16
    const val LIST_IA_TOPOLOGY = 20; const val LIST_RS_VIEWPORTS = 21; const val LIST_RS_SCISSORS = 22
    const val LIST_RESOURCE_BARRIER = 26; const val LIST_SET_ROOT_SIGNATURE = 30
    const val LIST_SET_ROOT_CONSTANTS = 36; const val LIST_IA_INDEX_BUFFER = 43
    const val LIST_IA_VERTEX_BUFFERS = 44; const val LIST_OM_RENDER_TARGETS = 46
    const val LIST_CLEAR_DSV = 47; const val LIST_CLEAR_RTV = 48
    const val QUEUE_EXECUTE = 10; const val QUEUE_SIGNAL = 14
    const val FENCE_COMPLETED_VALUE = 8
}

internal data class D3D12RasterizerConfig(val fillMode:Int,val cullMode:Int,val frontCounterClockwise:Boolean,val depthClipEnable:Boolean)
internal val TEAPOT_RASTERIZER = D3D12RasterizerConfig(3,3,true,true)
internal fun writeTeapotRasterizerState(target: MemorySegment, offset: Long = 0) {
    require(target.byteSize() >= offset + 44) { "D3D12_RASTERIZER_DESC requires 44 bytes" }
    target.set(ValueLayout.JAVA_INT, offset, TEAPOT_RASTERIZER.fillMode)
    target.set(ValueLayout.JAVA_INT, offset + 4, TEAPOT_RASTERIZER.cullMode)
    target.set(ValueLayout.JAVA_INT, offset + 8, if (TEAPOT_RASTERIZER.frontCounterClockwise) 1 else 0)
    target.set(ValueLayout.JAVA_INT, offset + 24, if (TEAPOT_RASTERIZER.depthClipEnable) 1 else 0)
}

internal data class GuidValue(val data1: Int, val data2: Int, val data3: Int, val data4: ByteArray)
internal fun Arena.guid(value: GuidValue): MemorySegment = allocate(16, 4).also {
    it.set(ValueLayout.JAVA_INT, 0, value.data1); it.set(ValueLayout.JAVA_SHORT, 4, value.data2.toShort())
    it.set(ValueLayout.JAVA_SHORT, 6, value.data3.toShort())
    value.data4.forEachIndexed { index, byte -> it.set(ValueLayout.JAVA_BYTE, 8L + index, byte) }
}
internal fun String.guidValue(): GuidValue {
    val p = split('-'); val tail = (p[3] + p[4]).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return GuidValue(p[0].toUInt(16).toInt(), p[1].toInt(16), p[2].toInt(16), tail)
}
internal fun MemorySegment.outObject(name: String): MemorySegment = get(ValueLayout.ADDRESS, 0).also {
    check(it.address() != 0L) { "$name returned null" }
}
internal fun checkHr(hr: Int, operation: String) { check(hr >= 0) { "$operation failed: 0x${hr.toUInt().toString(16).padStart(8, '0')}" } }

internal const val D3D_FEATURE_LEVEL_11_0 = 0xb000
internal const val D3D_ROOT_SIGNATURE_VERSION_1 = 1
internal const val D3DCOMPILE_ENABLE_STRICTNESS = 1 shl 11
