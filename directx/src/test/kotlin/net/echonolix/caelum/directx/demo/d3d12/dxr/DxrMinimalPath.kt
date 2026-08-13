package net.echonolix.caelum.directx.demo.d3d12.dxr

import net.echonolix.caelum.directx.demo.d3d12.D3D12Descriptors
import net.echonolix.caelum.directx.demo.d3d12.D3D12Native
import net.echonolix.caelum.directx.demo.d3d12.checkHr
import net.echonolix.caelum.directx.demo.d3d12.guid
import net.echonolix.caelum.directx.demo.d3d12.guidValue
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder

/** A D3D12 buffer supplied by the raster demo's allocator. */
internal data class DxrBuffer(val resource: MemorySegment, val gpuAddress: Long, val size: Long)

/** GPU-produced RGBA8 image that the owning renderer copies directly into its swap chain. */
internal data class DxrFrameOutput(
    val buffer: DxrBuffer,
    val imageOffset: Long,
    val rowPitch: Int,
    val width: Int,
    val height: Int,
)

/**
 * Narrow bridge to the owning D3D12 renderer. The helper deliberately does not own a second queue or fence.
 * All resources returned here must stay alive until [submitAndWait] returns.
 */
internal interface DxrExecutionContext {
    val device: MemorySegment
    val openCommandList: MemorySegment
    val globalRootSignature: MemorySegment
    val outputWidth: Int
    val outputHeight: Int
    val teapotVertexBuffer: DxrBuffer
    val teapotIndexBuffer: DxrBuffer
    val teapotVertexCount: Int
    val teapotIndexCount: Int
    fun createBuffer(size: Long, heapType: Int, initialState: Int, resourceFlags: Int = 0): DxrBuffer
    fun upload(buffer: DxrBuffer, bytes: ByteArray)
    fun readUInt(buffer: DxrBuffer): Int
    fun submitAndWait()
    fun installRaytracedFrame(frame: DxrFrameOutput)
}

internal data class DxrExecutionEvidence(
    val raytracingTier: Int,
    val buildRaytracingCalls: Int,
    val dispatchRaysCalls: Int,
    val gpuMarker: Int,
    val frameOutput: DxrFrameOutput,
    val samplesPerPixel: Int,
    val primaryRayCount: Long,
    val maximumRecursionDepth: Int = 2,
) {
    val hit: Boolean get() = gpuMarker == HIT_MARKER
}

/** Visible DXR path: indexed teapot BLAS, full-window multi-sample rays, reflection rays and GPU image. */
internal object DxrMinimalPath {
    fun execute(context: DxrExecutionContext, dxilLibrary: ByteArray, samplesPerPixel: Int = 8): DxrExecutionEvidence = Arena.ofConfined().use { arena ->
        require(dxilLibrary.isNotEmpty()) { "The embedded lib_6_3 DXIL library is missing" }
        require(samplesPerPixel in 1..32) { "DXR samples per pixel must be in 1..32" }
        require(context.outputWidth > 0 && context.outputHeight > 0)
        require(context.teapotVertexCount > 0 && context.teapotIndexCount >= 3 && context.teapotIndexCount % 3 == 0)
        val device5 = query(context.device, IID_DEVICE5, "ID3D12Device5", arena)
        val list4 = query(context.openCommandList, IID_COMMAND_LIST4, "ID3D12GraphicsCommandList4", arena)
        var stateObject = MemorySegment.NULL
        var stateProperties = MemorySegment.NULL
        try {
            val options5 = arena.allocate(12, 4)
            checkHr(D3D12Native.comInt(device5, DEVICE_CHECK_FEATURE_SUPPORT, CHECK_FEATURE_SUPPORT,
                D3D12_FEATURE_D3D12_OPTIONS5, options5, 12), "CheckFeatureSupport(D3D12_OPTIONS5)")
            val tier = options5.get(ValueLayout.JAVA_INT, 8)
            check(tier != D3D12_RAYTRACING_TIER_NOT_SUPPORTED) {
                "Hardware ray tracing was requested, but D3D12_OPTIONS5 reports RAYTRACING_TIER_NOT_SUPPORTED"
            }

            val blasInputs = bottomLevelInputs(arena, context.teapotVertexBuffer.gpuAddress,
                context.teapotIndexBuffer.gpuAddress, context.teapotVertexCount, context.teapotIndexCount)
            val blasInfo = prebuild(device5, arena, blasInputs)
            val blasScratch = context.createBuffer(align(blasInfo.second, 256), D3D12_HEAP_TYPE_DEFAULT, D3D12_RESOURCE_STATE_UNORDERED_ACCESS, D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS)
            val blas = context.createBuffer(align(blasInfo.first, 256), D3D12_HEAP_TYPE_DEFAULT, D3D12_RESOURCE_STATE_RAYTRACING_ACCELERATION_STRUCTURE, D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS)
            build(list4, arena, blasInputs, blas, blasScratch)
            uavBarrier(list4, arena, blas.resource)

            val instance = context.createBuffer(64, D3D12_HEAP_TYPE_UPLOAD, D3D12_RESOURCE_STATE_GENERIC_READ)
            context.upload(instance, DxrLayoutContract.instanceDescription(blas.gpuAddress))
            val tlasInfo = prebuild(device5, arena, topLevelInputs(arena, instance.gpuAddress))
            val tlasScratch = context.createBuffer(align(tlasInfo.second, 256), D3D12_HEAP_TYPE_DEFAULT, D3D12_RESOURCE_STATE_UNORDERED_ACCESS, D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS)
            val tlas = context.createBuffer(align(tlasInfo.first, 256), D3D12_HEAP_TYPE_DEFAULT, D3D12_RESOURCE_STATE_RAYTRACING_ACCELERATION_STRUCTURE, D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS)
            build(list4, arena, topLevelInputs(arena, instance.gpuAddress), tlas, tlasScratch)
            uavBarrier(list4, arena, tlas.resource)

            stateObject = createStateObject(device5, context.globalRootSignature, dxilLibrary, arena)
            stateProperties = query(stateObject, IID_STATE_OBJECT_PROPERTIES, "ID3D12StateObjectProperties", arena)
            val shaderTable = context.createBuffer(192, D3D12_HEAP_TYPE_UPLOAD, D3D12_RESOURCE_STATE_GENERIC_READ)
            context.upload(shaderTable, shaderTable(stateProperties, arena))
            val rowPitch = align(context.outputWidth.toLong() * 4, 256).toInt()
            val outputSize = IMAGE_OFFSET + rowPitch.toLong() * context.outputHeight
            val output = context.createBuffer(outputSize, D3D12_HEAP_TYPE_DEFAULT, D3D12_RESOURCE_STATE_UNORDERED_ACCESS, D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS)
            val readback = context.createBuffer(4, D3D12_HEAP_TYPE_READBACK, D3D12_RESOURCE_STATE_COPY_DEST)

            D3D12Native.comVoid(list4, LIST_SET_PIPELINE_STATE1, SET_PIPELINE_STATE1, stateObject)
            D3D12Native.comVoid(list4, LIST_SET_COMPUTE_ROOT_SIGNATURE, SET_ROOT_SIGNATURE, context.globalRootSignature)
            D3D12Native.comVoid(list4, LIST_SET_COMPUTE_ROOT_UAV, SET_ROOT_GPU_ADDRESS, 0, output.gpuAddress)
            D3D12Native.comVoid(list4, LIST_SET_COMPUTE_ROOT_SRV, SET_ROOT_GPU_ADDRESS, 1, tlas.gpuAddress)
            D3D12Native.comVoid(list4, LIST_SET_COMPUTE_ROOT_SRV, SET_ROOT_GPU_ADDRESS, 2, context.teapotVertexBuffer.gpuAddress)
            D3D12Native.comVoid(list4, LIST_SET_COMPUTE_ROOT_SRV, SET_ROOT_GPU_ADDRESS, 3, context.teapotIndexBuffer.gpuAddress)
            val constants = rayConstants(arena, context.outputWidth, context.outputHeight, samplesPerPixel, rowPitch)
            D3D12Native.comVoid(list4, LIST_SET_COMPUTE_ROOT_CONSTANTS, SET_ROOT_CONSTANTS, 4, 16, constants, 0)
            val dispatch = dispatchDesc(arena, shaderTable.gpuAddress, context.outputWidth, context.outputHeight)
            D3D12Native.comVoid(list4, LIST_DISPATCH_RAYS, DISPATCH_RAYS, dispatch)
            uavBarrier(list4, arena, output.resource)
            transition(list4, arena, output.resource, D3D12_RESOURCE_STATE_UNORDERED_ACCESS, D3D12_RESOURCE_STATE_COPY_SOURCE)
            D3D12Native.comVoid(list4, LIST_COPY_BUFFER_REGION, COPY_BUFFER_REGION, readback.resource, 0L, output.resource, 0L, 4L)
            context.submitAndWait()
            val marker = context.readUInt(readback)
            check(marker == HIT_MARKER) {
                "DXR DispatchRays completed, but GPU readback was 0x${marker.toUInt().toString(16)} instead of hit marker 0x${HIT_MARKER.toUInt().toString(16)}"
            }
            val frame = DxrFrameOutput(output, IMAGE_OFFSET, rowPitch, context.outputWidth, context.outputHeight)
            context.installRaytracedFrame(frame)
            DxrExecutionEvidence(tier, 2, 1, marker, frame, samplesPerPixel,
                context.outputWidth.toLong() * context.outputHeight * samplesPerPixel)
        } finally {
            D3D12Native.release(stateProperties)
            D3D12Native.release(stateObject)
            D3D12Native.release(list4)
            D3D12Native.release(device5)
        }
    }

    private fun prebuild(device5: MemorySegment, arena: Arena, inputs: MemorySegment): Pair<Long, Long> {
        val info = arena.allocate(24, 8)
        D3D12Native.comVoid(device5, DEVICE_GET_PREBUILD_INFO, GET_PREBUILD_INFO, inputs, info)
        val result = info.get(ValueLayout.JAVA_LONG, 0)
        val scratch = info.get(ValueLayout.JAVA_LONG, 8)
        check(result > 0 && scratch > 0) { "DXR acceleration-structure prebuild sizes are invalid" }
        return result to scratch
    }

    private fun bottomLevelInputs(arena: Arena, vertices: Long, indices: Long, vertexCount: Int, indexCount: Int): MemorySegment {
        val geometry = arena.allocate(56, 8)
        geometry.set(ValueLayout.JAVA_INT, 0, 0) // TRIANGLES
        geometry.set(ValueLayout.JAVA_INT, 4, 1) // OPAQUE
        geometry.set(ValueLayout.JAVA_LONG, 8, 0) // Transform3x4
        geometry.set(ValueLayout.JAVA_INT, 16, 42) // DXGI_FORMAT_R32_UINT
        geometry.set(ValueLayout.JAVA_INT, 20, 6) // R32G32B32_FLOAT
        geometry.set(ValueLayout.JAVA_INT, 24, indexCount)
        geometry.set(ValueLayout.JAVA_INT, 28, vertexCount)
        geometry.set(ValueLayout.JAVA_LONG, 32, indices)
        geometry.set(ValueLayout.JAVA_LONG, 40, vertices)
        geometry.set(ValueLayout.JAVA_LONG, 48, 24)
        return inputs(arena, 1, geometry.address())
    }

    private fun topLevelInputs(arena: Arena, instance: Long): MemorySegment = inputs(arena, 0, instance)

    private fun inputs(arena: Arena, type: Int, descs: Long): MemorySegment = arena.allocate(24, 8).also {
        it.set(ValueLayout.JAVA_INT, 0, type)
        it.set(ValueLayout.JAVA_INT, 4, 0)
        it.set(ValueLayout.JAVA_INT, 8, 1)
        it.set(ValueLayout.JAVA_INT, 12, 0) // ARRAY
        it.set(ValueLayout.JAVA_LONG, 16, descs)
    }

    private fun build(list4: MemorySegment, arena: Arena, inputs: MemorySegment, result: DxrBuffer, scratch: DxrBuffer) {
        val desc = arena.allocate(48, 8)
        desc.set(ValueLayout.JAVA_LONG, 0, result.gpuAddress)
        MemorySegment.copy(inputs, 0, desc, 8, 24)
        desc.set(ValueLayout.JAVA_LONG, 32, 0)
        desc.set(ValueLayout.JAVA_LONG, 40, scratch.gpuAddress)
        D3D12Native.comVoid(list4, LIST_BUILD_RAYTRACING_AS, BUILD_RAYTRACING_AS, desc, 0, MemorySegment.NULL)
    }

    private fun createStateObject(device5: MemorySegment, root: MemorySegment, dxil: ByteArray, arena: Arena): MemorySegment {
        val bytecode = arena.allocateFrom(ValueLayout.JAVA_BYTE, *dxil)
        val library = arena.allocate(32, 8).also {
            it.set(ValueLayout.ADDRESS, 0, bytecode); it.set(ValueLayout.JAVA_LONG, 8, dxil.size.toLong())
            it.set(ValueLayout.JAVA_INT, 16, 0); it.set(ValueLayout.ADDRESS, 24, MemorySegment.NULL)
        }
        val hitGroup = arena.allocate(40, 8).also {
            it.set(ValueLayout.ADDRESS, 0, wide(arena, "HitGroup")); it.set(ValueLayout.JAVA_INT, 8, 0)
            it.set(ValueLayout.ADDRESS, 16, MemorySegment.NULL); it.set(ValueLayout.ADDRESS, 24, wide(arena, "ClosestHit"))
            it.set(ValueLayout.ADDRESS, 32, MemorySegment.NULL)
        }
        val shaderConfig = arena.allocate(8, 4).also { it.set(ValueLayout.JAVA_INT, 0, 16); it.set(ValueLayout.JAVA_INT, 4, 8) }
        val rootPtr = arena.allocate(ValueLayout.ADDRESS).also { it.set(ValueLayout.ADDRESS, 0, root) }
        val pipelineConfig = arena.allocate(4, 4).also { it.set(ValueLayout.JAVA_INT, 0, 2) }
        val subobjects = arena.allocate(5L * 16, 8)
        listOf(5 to library, 11 to hitGroup, 9 to shaderConfig, DxrLayoutContract.GLOBAL_ROOT_SIGNATURE_SUBOBJECT_TYPE to rootPtr, 10 to pipelineConfig).forEachIndexed { i, (type, ptr) ->
            subobjects.set(ValueLayout.JAVA_INT, i * 16L, type)
            subobjects.set(ValueLayout.ADDRESS, i * 16L + 8, ptr)
        }
        val desc = arena.allocate(16, 8).also {
            it.set(ValueLayout.JAVA_INT, 0, 3) // RAYTRACING_PIPELINE
            it.set(ValueLayout.JAVA_INT, 4, 5)
            it.set(ValueLayout.ADDRESS, 8, subobjects)
        }
        val output = arena.allocate(ValueLayout.ADDRESS)
        checkHr(D3D12Native.comInt(device5, DEVICE_CREATE_STATE_OBJECT, CREATE_STATE_OBJECT, desc,
            arena.guid(IID_STATE_OBJECT.guidValue()), output), "ID3D12Device5.CreateStateObject")
        return output.get(ValueLayout.ADDRESS, 0)
    }

    private fun shaderTable(properties: MemorySegment, arena: Arena): ByteArray {
        val bytes = ByteArray(192)
        listOf("RayGen" to 0, "Miss" to 64, "HitGroup" to 128).forEach { (name, offset) ->
            val identifier = D3D12Native.comAddress(properties, STATE_PROPERTIES_GET_SHADER_IDENTIFIER,
                GET_SHADER_IDENTIFIER, wide(arena, name))
            check(identifier.address() != 0L) { "State object did not export $name" }
            val id = identifier.reinterpret(32).toArray(ValueLayout.JAVA_BYTE)
            id.copyInto(bytes, offset)
        }
        return bytes
    }

    private fun dispatchDesc(arena: Arena, address: Long, width: Int, height: Int): MemorySegment = arena.allocate(104, 8).also {
        it.set(ValueLayout.JAVA_LONG, 0, address); it.set(ValueLayout.JAVA_LONG, 8, 32)
        it.set(ValueLayout.JAVA_LONG, 16, address + 64); it.set(ValueLayout.JAVA_LONG, 24, 32); it.set(ValueLayout.JAVA_LONG, 32, DxrLayoutContract.SHADER_RECORD_SIZE.toLong())
        it.set(ValueLayout.JAVA_LONG, 40, address + 128); it.set(ValueLayout.JAVA_LONG, 48, 32); it.set(ValueLayout.JAVA_LONG, 56, DxrLayoutContract.SHADER_RECORD_SIZE.toLong())
        it.set(ValueLayout.JAVA_INT, 88, width); it.set(ValueLayout.JAVA_INT, 92, height); it.set(ValueLayout.JAVA_INT, 96, 1)
    }

    private fun rayConstants(arena: Arena, width: Int, height: Int, spp: Int, rowPitch: Int): MemorySegment =
        arena.allocate(64, 4).also {
            it.set(ValueLayout.JAVA_INT, 0, width); it.set(ValueLayout.JAVA_INT, 4, height)
            it.set(ValueLayout.JAVA_INT, 8, spp); it.set(ValueLayout.JAVA_INT, 12, rowPitch)
            it.set(ValueLayout.JAVA_FLOAT, 16, 0f); it.set(ValueLayout.JAVA_FLOAT, 20, 0.15f)
            it.set(ValueLayout.JAVA_FLOAT, 24, 4.6f); it.set(ValueLayout.JAVA_FLOAT, 28, 0.383864f)
        }

    private fun uavBarrier(list: MemorySegment, arena: Arena, resource: MemorySegment) {
        val barrier = arena.allocate(32, 8)
        barrier.set(ValueLayout.JAVA_INT, 0, 2); barrier.set(ValueLayout.ADDRESS, 8, resource)
        D3D12Native.comVoid(list, LIST_RESOURCE_BARRIER, D3D12Descriptors.VOID_BARRIERS, 1, barrier)
    }

    private fun transition(list: MemorySegment, arena: Arena, resource: MemorySegment, before: Int, after: Int) {
        val barrier = arena.allocate(32, 8)
        barrier.set(ValueLayout.JAVA_INT, 0, 0); barrier.set(ValueLayout.ADDRESS, 8, resource)
        barrier.set(ValueLayout.JAVA_INT, 16, 0xffffffff.toInt()); barrier.set(ValueLayout.JAVA_INT, 20, before); barrier.set(ValueLayout.JAVA_INT, 24, after)
        D3D12Native.comVoid(list, LIST_RESOURCE_BARRIER, D3D12Descriptors.VOID_BARRIERS, 1, barrier)
    }

    private fun query(source: MemorySegment, iid: String, label: String, arena: Arena): MemorySegment {
        val out = arena.allocate(ValueLayout.ADDRESS)
        checkHr(D3D12Native.queryInterface(source, arena.guid(iid.guidValue()), out), "QueryInterface($label)")
        return out.get(ValueLayout.ADDRESS, 0).also { check(it.address() != 0L) { "$label was null" } }
    }

    private fun wide(arena: Arena, text: String): MemorySegment = arena.allocate((text.length + 1L) * 2, 2).also { segment ->
        text.forEachIndexed { index, char -> segment.set(ValueLayout.JAVA_SHORT, index * 2L, char.code.toShort()) }
    }

    private fun align(value: Long, alignment: Long): Long = (value + alignment - 1) and -alignment
}

/** Pure ABI contracts kept executable so regressions do not require a DXR-capable adapter to catch. */
internal object DxrLayoutContract {
    const val INSTANCE_DESCRIPTION_SIZE = 64
    const val GLOBAL_ROOT_SIGNATURE_SUBOBJECT_TYPE = 1
    const val SHADER_RECORD_SIZE = 32

    fun instanceDescription(blasAddress: Long, instanceMask: Int = 0xff): ByteArray {
        require(instanceMask in 0..0xff)
        return java.nio.ByteBuffer.allocate(INSTANCE_DESCRIPTION_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f).forEach(::putFloat)
            putInt(instanceMask shl 24) // InstanceID:24 | InstanceMask:8
            putInt(0) // InstanceContributionToHitGroupIndex:24 | Flags:8
            putLong(blasAddress)
        }.array()
    }
}

internal const val HIT_MARKER: Int = 0x48595421
internal const val MISS_MARKER: Int = 0x4d495353
internal const val D3D12_FEATURE_D3D12_OPTIONS5 = 27
internal const val D3D12_RAYTRACING_TIER_NOT_SUPPORTED = 0
internal const val D3D12_HEAP_TYPE_DEFAULT = 1
internal const val D3D12_HEAP_TYPE_UPLOAD = 2
internal const val D3D12_HEAP_TYPE_READBACK = 3
internal const val D3D12_RESOURCE_STATE_GENERIC_READ = 0xAC3
internal const val D3D12_RESOURCE_STATE_UNORDERED_ACCESS = 0x8
internal const val D3D12_RESOURCE_STATE_RAYTRACING_ACCELERATION_STRUCTURE = 0x400000
internal const val D3D12_RESOURCE_STATE_COPY_DEST = 0x400
internal const val D3D12_RESOURCE_STATE_COPY_SOURCE = 0x800
internal const val D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS = 0x4
internal const val IMAGE_OFFSET = 512L

private const val IID_DEVICE5 = "8b4f173b-2fea-4b80-8f58-4307191ab95d"
private const val IID_COMMAND_LIST4 = "8754318e-d3a9-4541-98cf-645b50dc4874"
private const val IID_STATE_OBJECT = "47016943-fca8-4594-93ea-af258b55346d"
private const val IID_STATE_OBJECT_PROPERTIES = "de5fa827-9bf9-4f26-89ff-d7f56fde3860"
private const val DEVICE_CHECK_FEATURE_SUPPORT = 13
private const val DEVICE_CREATE_STATE_OBJECT = 62
private const val DEVICE_GET_PREBUILD_INFO = 63
private const val LIST_COPY_BUFFER_REGION = 15
private const val LIST_RESOURCE_BARRIER = 26
private const val LIST_SET_COMPUTE_ROOT_SIGNATURE = 29
private const val LIST_SET_COMPUTE_ROOT_SRV = 39
private const val LIST_SET_COMPUTE_ROOT_UAV = 41
private const val LIST_SET_COMPUTE_ROOT_CONSTANTS = 35
private const val LIST_BUILD_RAYTRACING_AS = 72
private const val LIST_SET_PIPELINE_STATE1 = 75
private const val LIST_DISPATCH_RAYS = 76
private const val STATE_PROPERTIES_GET_SHADER_IDENTIFIER = 3

private val CHECK_FEATURE_SUPPORT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
private val GET_PREBUILD_INFO = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val CREATE_STATE_OBJECT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val BUILD_RAYTRACING_AS = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
private val SET_PIPELINE_STATE1 = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val SET_ROOT_SIGNATURE = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val SET_ROOT_GPU_ADDRESS = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
private val DISPATCH_RAYS = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val COPY_BUFFER_REGION = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
private val SET_ROOT_CONSTANTS = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
private val GET_SHADER_IDENTIFIER = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
