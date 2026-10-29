package net.echonolix.caelum.directx.demo.jolt.dxr

import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.directx.demo.d3d12.*
import net.echonolix.caelum.directx.demo.jolt.FrameCapture
import net.echonolix.caelum.dxgi.api.NativeApiInvoker
import java.awt.image.BufferedImage
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.*
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Owns the native DXR frame pipeline. Every submission completes before upload memory is reused. */
internal class JoltDxrRenderer(
    hwnd: MemorySegment,
    private val width: Int,
    private val height: Int,
    private val meshes: List<DxrSceneMesh>,
    initialInstances: List<DxrSceneInstance>,
    private val camera: Vec3,
    private val target: Vec3,
    private val samplesPerPixel: Int = 2,
) : AutoCloseable {
    private data class Buffer(val resource: MemorySegment, val address: Long, val size: Long)
    private val owned = ArrayList<MemorySegment>()
    private var device = MemorySegment.NULL
    private var queue = MemorySegment.NULL
    private var allocator = MemorySegment.NULL
    private var list = MemorySegment.NULL
    private var swapChain = MemorySegment.NULL
    private var fence = MemorySegment.NULL
    private var root = MemorySegment.NULL
    private var pipeline = MemorySegment.NULL
    private val backBuffers = ArrayList<MemorySegment>()
    private lateinit var vertices: Buffer
    private lateinit var indices: Buffer
    private lateinit var instanceBuffer: Buffer
    private lateinit var metadataBuffer: Buffer
    private lateinit var tlas: Buffer
    private lateinit var tlasScratch: Buffer
    private lateinit var output: Buffer
    private lateinit var readback: Buffer
    private lateinit var zeroHeader: Buffer
    private lateinit var table: Buffer
    private val blasAddresses = LongArray(meshes.size)
    private val vertexOffsets = IntArray(meshes.size)
    private val indexOffsets = IntArray(meshes.size)
    private val vertexByteOffsets = IntArray(meshes.size)
    private val indexByteOffsets = IntArray(meshes.size)
    private val capacity = initialInstances.size
    private val rowPitch = align(width.toLong() * 4, 256).toInt()
    private val outputBytes = DxrSceneData.IMAGE_OFFSET + rowPitch.toLong() * height
    private var fenceValue = 0L
    private var closed = false
    private var outputState = UAV
    private var frames = 0L
    private var tlasBuilds = 0L
    private var lastInstanceCount = 0
    private var tier = 0
    private var adapterVendorId = 0
    private var adapterDeviceId = 0
    private var lastCounters = IntArray(0)
    private var lastReflectionHits = 0
    private val lastMeshIndices = IntArray(capacity)

    // The extra DXR surface is resolved from the pinned catalog, including inherited vtable slots.
    private val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12)
    private data class Call(val slot: Int, val descriptor: FunctionDescriptor)
    private fun call(iface: String, method: String): Call {
        val declaration = profile.primary.requireInterface(iface).requireMethod(method)
        return Call(declaration.slot, declaration.functionDescriptor(profile.types))
    }
    private val feature = call("ID3D12Device", "CheckFeatureSupport")
    private val prebuildCall = call("ID3D12Device5", "GetRaytracingAccelerationStructurePrebuildInfo")
    private val buildCall = call("ID3D12GraphicsCommandList4", "BuildRaytracingAccelerationStructure")
    private val stateCall = call("ID3D12Device5", "CreateStateObject")
    private val pipelineCall = call("ID3D12GraphicsCommandList4", "SetPipelineState1")
    private val rootCall = call("ID3D12GraphicsCommandList", "SetComputeRootSignature")
    private val uavCall = call("ID3D12GraphicsCommandList", "SetComputeRootUnorderedAccessView")
    private val srvCall = call("ID3D12GraphicsCommandList", "SetComputeRootShaderResourceView")
    private val constantsCall = call("ID3D12GraphicsCommandList", "SetComputeRoot32BitConstants")
    private val dispatchCall = call("ID3D12GraphicsCommandList4", "DispatchRays")
    private val copyCall = call("ID3D12GraphicsCommandList", "CopyBufferRegion")
    private val identifierCall = call("ID3D12StateObjectProperties", "GetShaderIdentifier")

    init {
        try {
            DxrSdkLayoutCheck.verify()
            require(width > 0 && height > 0 && samplesPerPixel in 1..32)
            require(capacity in 1..DxrSceneData.MAX_INSTANCES && meshes.isNotEmpty())
            for ((name, size) in listOf("D3D12_RAYTRACING_INSTANCE_DESC" to 64L,
                "D3D12_RAYTRACING_GEOMETRY_DESC" to 56L, "D3D12_DISPATCH_RAYS_DESC" to 104L,
                "D3D12_TEXTURE_COPY_LOCATION" to 48L)) {
                check(profile.types.layout(name).byteSize() == size) { "$name ABI size mismatch" }
            }
            Arena.ofConfined().use { a ->
                D3D12Native.ensureAvailable()
                val factory = own(out(a, IID_FACTORY, "CreateDXGIFactory2") { iid, out -> D3D12Native.createFactory(iid, out) })
                val adapter = own(selectAdapter(a, factory))
                device = own(out(a, IID_DEVICE5, "D3D12CreateDevice(ID3D12Device5)") { iid, out -> D3D12Native.createDevice(adapter, iid, out) })
                val options = a.allocate(12, 4)
                checkHr(D3D12Native.comInt(device, feature.slot, feature.descriptor, 27, options, 12), "D3D12_OPTIONS5")
                tier = options.get(JAVA_INT, 8)
                check(tier != 0) { "The selected high-performance adapter does not support hardware DXR" }
                val queueDesc = a.allocate(16, 4)
                queue = own(out(a, IID_QUEUE, "CreateCommandQueue") { iid, out ->
                    D3D12Native.comInt(device, D3D12Slots.DEVICE_CREATE_QUEUE, D3D12Descriptors.HR_DESC_IID_OUT, queueDesc, iid, out)
                })
                swapChain = own(createSwapChain(a, factory, hwnd))
                repeat(2) { index ->
                    backBuffers += own(out(a, IID_RESOURCE, "GetBuffer($index)") { iid, out ->
                        D3D12Native.comInt(swapChain, D3D12Slots.SWAP_CHAIN_GET_BUFFER, D3D12Descriptors.GET_BUFFER, index, iid, out)
                    })
                }
                allocator = own(out(a, IID_ALLOCATOR, "CreateCommandAllocator") { iid, out ->
                    D3D12Native.comInt(device, D3D12Slots.DEVICE_CREATE_ALLOCATOR, D3D12Descriptors.HR_INT_IID_OUT, 0, iid, out)
                })
                list = own(out(a, IID_LIST4, "CreateCommandList(ID3D12GraphicsCommandList4)") { iid, out ->
                    D3D12Native.comInt(device, D3D12Slots.DEVICE_CREATE_LIST, D3D12Descriptors.HR_CREATE_LIST, 0, 0, allocator, MemorySegment.NULL, iid, out)
                })
                fence = own(out(a, IID_FENCE, "CreateFence") { iid, out ->
                    D3D12Native.comInt(device, D3D12Slots.DEVICE_CREATE_FENCE, D3D12Descriptors.HR_FENCE, 0L, 0, iid, out)
                })
                root = own(createRoot(a))
                val shader = requireNotNull(javaClass.getResourceAsStream("JoltRayTracing.dxil")) { "JoltRayTracing.dxil is missing" }.use { it.readAllBytes() }
                pipeline = own(createPipeline(a, shader))
                val properties = out(a, IID_PROPERTIES, "state properties") { iid, out -> D3D12Native.queryInterface(pipeline, iid, out) }
                try {
                    val identifiers = ByteArray(192)
                    listOf("RayGen", "Miss", "HitGroup").forEachIndexed { index, name ->
                        val id = D3D12Native.comAddress(properties, identifierCall.slot, identifierCall.descriptor, wide(a, name))
                        check(id.address() != 0L) { "DXR shader export missing: $name" }
                        id.reinterpret(32).toArray(JAVA_BYTE).copyInto(identifiers, index * 64)
                    }
                    table = buffer(a, 192, 2, GENERIC_READ)
                    upload(a, table, MemorySegment.ofArray(identifiers))
                } finally { D3D12Native.release(properties) }
                createGeometry(a)
                instanceBuffer = buffer(a, capacity * 64L, 2, GENERIC_READ)
                metadataBuffer = buffer(a, capacity * 32L, 2, GENERIC_READ)
                uploadScene(a, initialInstances)
                val tlasInputs = inputs(a, 0, capacity, instanceBuffer.address)
                val sizes = prebuild(a, tlasInputs)
                tlas = buffer(a, sizes.first, 1, AS_STATE, 4)
                tlasScratch = buffer(a, sizes.second, 1, UAV, 4)
                output = buffer(a, outputBytes, 1, UAV, 4)
                readback = buffer(a, outputBytes, 3, COPY_DEST)
                zeroHeader = buffer(a, DxrSceneData.IMAGE_OFFSET.toLong(), 2, GENERIC_READ)
                upload(a, zeroHeader, a.allocate(DxrSceneData.IMAGE_OFFSET.toLong(), 4))
                // Complete static BLAS construction before the scratch allocations can be reused or freed.
                submit(a)
            }
        } catch (failure: Throwable) {
            runCatching(::close).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    internal fun render(instances: List<DxrSceneInstance>, hidden: Boolean) {
        check(!closed)
        require(instances.size in 1..capacity) { "Instance count exceeds the renderer's initial capacity" }
        Arena.ofConfined().use { a ->
            reset()
            uploadScene(a, instances)
            if (outputState != COPY_DEST) transition(a, output.resource, outputState, COPY_DEST)
            D3D12Native.comVoid(list, copyCall.slot, copyCall.descriptor, output.resource, 0L, zeroHeader.resource, 0L, DxrSceneData.IMAGE_OFFSET.toLong())
            transition(a, output.resource, COPY_DEST, UAV)
            build(a, inputs(a, 0, instances.size, instanceBuffer.address), tlas, tlasScratch)
            tlasBuilds++
            uavBarrier(a, tlas.resource)
            D3D12Native.comVoid(list, pipelineCall.slot, pipelineCall.descriptor, pipeline)
            D3D12Native.comVoid(list, rootCall.slot, rootCall.descriptor, root)
            D3D12Native.comVoid(list, uavCall.slot, uavCall.descriptor, 0, output.address)
            longArrayOf(tlas.address, vertices.address, indices.address, metadataBuffer.address).forEachIndexed { i, address ->
                D3D12Native.comVoid(list, srvCall.slot, srvCall.descriptor, i + 1, address)
            }
            val constants = a.allocate(48, 4)
            intArrayOf(width, height, samplesPerPixel, rowPitch).forEachIndexed { i, value -> constants.set(JAVA_INT, i * 4L, value) }
            floatArrayOf(camera.x, camera.y, camera.z, .41421356f, target.x, target.y, target.z).forEachIndexed { i, value -> constants.set(JAVA_FLOAT, 16 + i * 4L, value) }
            constants.set(JAVA_INT, 44, instances.size)
            D3D12Native.comVoid(list, constantsCall.slot, constantsCall.descriptor, 5, 12, constants, 0)
            val dispatch = a.allocate(104, 8)
            dispatch.set(JAVA_LONG, 0, table.address); dispatch.set(JAVA_LONG, 8, 32)
            dispatch.set(JAVA_LONG, 16, table.address + 64); dispatch.set(JAVA_LONG, 24, 32); dispatch.set(JAVA_LONG, 32, 32)
            dispatch.set(JAVA_LONG, 40, table.address + 128); dispatch.set(JAVA_LONG, 48, 32); dispatch.set(JAVA_LONG, 56, 32)
            dispatch.set(JAVA_INT, 88, width); dispatch.set(JAVA_INT, 92, height); dispatch.set(JAVA_INT, 96, 1)
            D3D12Native.comVoid(list, dispatchCall.slot, dispatchCall.descriptor, dispatch)
            uavBarrier(a, output.resource)
            transition(a, output.resource, UAV, COPY_SOURCE)
            outputState = COPY_SOURCE
            val index = D3D12Native.comInt(swapChain, D3D12Slots.SWAP_CHAIN3_GET_CURRENT_BACK_BUFFER_INDEX, D3D12Descriptors.UINT_NO_ARGS)
            val back = backBuffers[index]
            transition(a, back, 0, COPY_DEST)
            D3D12Native.comVoid(list, D3D12Slots.LIST_COPY_TEXTURE_REGION, D3D12Descriptors.VOID_COPY_TEXTURE_REGION,
                textureLocation(a, back, false), 0, 0, 0, textureLocation(a, output.resource, true), MemorySegment.NULL)
            transition(a, back, COPY_DEST, 0)
            // Retain the full frame and counters for evidence, including frames before capture().
            D3D12Native.comVoid(list, copyCall.slot, copyCall.descriptor, readback.resource, 0L, output.resource, 0L, outputBytes)
            submit(a)
            checkHr(D3D12Native.comInt(swapChain, D3D12Slots.SWAP_CHAIN_PRESENT, D3D12Descriptors.PRESENT, if (hidden) 0 else 1, 0), "Present(DXR)")
            frames++
            lastInstanceCount = instances.size
            instances.forEachIndexed { i, instance -> lastMeshIndices[i] = instance.meshIndex }
        }
    }

    internal fun capture(path: Path): FrameCapture {
        check(!closed && frames > 0) { "Render a DXR frame before capture" }
        return Arena.ofConfined().use { a ->
            val bytes = map(a, readback)
            try {
                lastCounters = IntArray(4 + lastInstanceCount) { bytes.get(JAVA_INT, it * 4L) }
                lastReflectionHits = bytes.get(JAVA_INT, DxrSceneData.REFLECTION_HITS_OFFSET.toLong())
                val pixels = IntArray(width * height)
                for (y in 0 until height) for (x in 0 until width) {
                    val rgba = bytes.get(JAVA_INT, DxrSceneData.IMAGE_OFFSET + y * rowPitch.toLong() + x * 4L)
                    pixels[y * width + x] = (rgba and -0x1000000) or ((rgba and 255) shl 16) or (rgba and 0xff00) or ((rgba ushr 16) and 255)
                }
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                image.setRGB(0, 0, width, height, pixels, 0, width)
                path.toAbsolutePath().parent?.let(Files::createDirectories)
                check(ImageIO.write(image, "png", path.toFile())) { "No PNG writer is available" }
                FrameCapture(pixels, pixels.count { (it and 0xffffff) != 0 })
            } finally { unmap(readback) }
        }
    }

    internal fun checkHealth() {
        check(!closed)
        checkHr(D3D12Native.comInt(device, D3D12Slots.DEVICE_REMOVED_REASON, D3D12Descriptors.HR_NO_ARGS), "GetDeviceRemovedReason")
    }

    internal fun evidence(): Map<String, Number> {
        check(!closed)
        if (frames > 0) Arena.ofConfined().use { a ->
            val mapped = map(a, readback)
            try {
                lastCounters = IntArray(4 + lastInstanceCount) { mapped.get(JAVA_INT, it * 4L) }
                lastReflectionHits = mapped.get(JAVA_INT, DxrSceneData.REFLECTION_HITS_OFFSET.toLong())
            }
            finally { unmap(readback) }
        }
        return linkedMapOf<String, Number>(
            "raytracingTier" to tier, "blasBuilds" to meshes.size, "tlasBuilds" to tlasBuilds,
            "adapterVendorId" to adapterVendorId, "adapterDeviceId" to adapterDeviceId,
            "tlasUpdates" to 0, "dispatchRaysCalls" to frames, "samplesPerPixel" to samplesPerPixel,
            "primaryRaysPerFrame" to width.toLong() * height * samplesPerPixel,
            "primaryHits" to (lastCounters.getOrNull(0)?.toUInt()?.toLong() ?: 0),
            "shadowRays" to (lastCounters.getOrNull(1)?.toUInt()?.toLong() ?: 0),
            "reflectionRays" to (lastCounters.getOrNull(2)?.toUInt()?.toLong() ?: 0),
            "shadowOcclusions" to (lastCounters.getOrNull(3)?.toUInt()?.toLong() ?: 0),
            "reflectionHits" to lastReflectionHits.toUInt().toLong(),
            "instanceCount" to lastInstanceCount, "meshCount" to meshes.size, "maximumRecursionDepth" to 2,
        ).apply {
            repeat(lastInstanceCount) { put("instance${it}PrimaryHits", lastCounters.getOrElse(4 + it) { 0 }.toUInt().toLong()) }
            meshes.forEachIndexed { i, mesh ->
                put("mesh${i}Vertices", mesh.vertices.size / 6); put("mesh${i}Triangles", mesh.indices.size / 3)
                var hits = 0L
                repeat(lastInstanceCount) { if (lastMeshIndices[it] == i) hits += lastCounters.getOrElse(4 + it) { 0 }.toUInt().toLong() }
                put("mesh${i}PrimaryHits", hits)
            }
        }
    }

    private fun createGeometry(a: Arena) {
        var vertexCount = 0
        var indexCount = 0
        meshes.forEachIndexed { i, mesh ->
            require(mesh.vertices.isNotEmpty() && mesh.vertices.size % 6 == 0 && mesh.vertices.all(Float::isFinite)) { "Invalid vertices: ${mesh.name}" }
            require(mesh.indices.isNotEmpty() && mesh.indices.size % 3 == 0 && mesh.indices.all { it in 0 until mesh.vertices.size / 6 }) { "Invalid indices: ${mesh.name}" }
            vertexOffsets[i] = vertexCount; indexOffsets[i] = indexCount
            vertexByteOffsets[i] = vertexCount * 24; indexByteOffsets[i] = indexCount * 4
            vertexCount += mesh.vertices.size / 6; indexCount += mesh.indices.size
        }
        vertices = buffer(a, vertexCount * 24L, 2, GENERIC_READ)
        indices = buffer(a, indexCount * 4L, 2, GENERIC_READ)
        val vertexMap = map(a, vertices)
        try { meshes.forEachIndexed { i, mesh -> MemorySegment.copy(MemorySegment.ofArray(mesh.vertices), 0, vertexMap, vertexOffsets[i] * 24L, mesh.vertices.size * 4L) } }
        finally { unmap(vertices) }
        val indexMap = map(a, indices)
        try { meshes.forEachIndexed { i, mesh -> MemorySegment.copy(MemorySegment.ofArray(mesh.indices), 0, indexMap, indexOffsets[i] * 4L, mesh.indices.size * 4L) } }
        finally { unmap(indices) }
        meshes.forEachIndexed { i, mesh ->
            val geometry = a.allocate(56, 8)
            geometry.set(JAVA_INT, 0, 0); geometry.set(JAVA_INT, 4, 1)
            geometry.set(JAVA_INT, 16, 42); geometry.set(JAVA_INT, 20, 6)
            geometry.set(JAVA_INT, 24, mesh.indices.size); geometry.set(JAVA_INT, 28, mesh.vertices.size / 6)
            geometry.set(JAVA_LONG, 32, indices.address + indexOffsets[i] * 4L)
            geometry.set(JAVA_LONG, 40, vertices.address + vertexOffsets[i] * 24L); geometry.set(JAVA_LONG, 48, 24)
            val input = inputs(a, 1, 1, geometry.address())
            val sizes = prebuild(a, input)
            val blas = buffer(a, sizes.first, 1, AS_STATE, 4)
            val scratch = buffer(a, sizes.second, 1, UAV, 4)
            build(a, input, blas, scratch)
            uavBarrier(a, blas.resource)
            blasAddresses[i] = blas.address
        }
    }

    private fun uploadScene(a: Arena, instances: List<DxrSceneInstance>) {
        upload(a, instanceBuffer, MemorySegment.ofArray(DxrSceneData.instanceDescriptions(instances, blasAddresses)))
        upload(a, metadataBuffer, MemorySegment.ofArray(DxrSceneData.metadata(instances, vertexByteOffsets, indexByteOffsets)))
    }

    private fun inputs(a: Arena, type: Int, count: Int, address: Long): MemorySegment = a.allocate(24, 8).also {
        it.set(JAVA_INT, 0, type); it.set(JAVA_INT, 8, count); it.set(JAVA_LONG, 16, address)
    }
    private fun prebuild(a: Arena, input: MemorySegment): Pair<Long, Long> {
        val info = a.allocate(24, 8)
        D3D12Native.comVoid(device, prebuildCall.slot, prebuildCall.descriptor, input, info)
        val result = info.get(JAVA_LONG, 0); val scratch = info.get(JAVA_LONG, 8)
        check(result > 0 && scratch > 0) { "Invalid DXR prebuild allocation sizes" }
        return align(result, 256) to align(scratch, 256)
    }
    private fun build(a: Arena, input: MemorySegment, result: Buffer, scratch: Buffer) {
        val desc = a.allocate(48, 8)
        desc.set(JAVA_LONG, 0, result.address); MemorySegment.copy(input, 0, desc, 8, 24)
        desc.set(JAVA_LONG, 40, scratch.address)
        D3D12Native.comVoid(list, buildCall.slot, buildCall.descriptor, desc, 0, MemorySegment.NULL)
    }

    private fun createRoot(a: Arena): MemorySegment {
        val parameters = a.allocate(192, 8)
        for (i in 0..4) {
            parameters.set(JAVA_INT, i * 32L, if (i == 0) 4 else 3)
            parameters.set(JAVA_INT, i * 32L + 8, if (i == 0) 0 else i - 1)
        }
        parameters.set(JAVA_INT, 160, 1); parameters.set(JAVA_INT, 176, 12)
        val desc = a.allocate(40, 8)
        desc.set(JAVA_INT, 0, 6); desc.set(ADDRESS, 8, parameters)
        val blobOut = a.allocate(ADDRESS); val errorOut = a.allocate(ADDRESS)
        val hr = D3D12Native.serializeRootSignature(desc, blobOut, errorOut)
        val errors = errorOut.get(ADDRESS, 0)
        val message = try { if (errors.address() != 0L) D3D12Native.blobText(errors) else "" }
        finally { D3D12Native.release(errors) }
        check(hr >= 0) { "Serialize DXR root signature: $message" }
        val blob = blobOut.outObject("root blob")
        return try { out(a, IID_ROOT, "CreateRootSignature") { iid, result ->
            D3D12Native.comInt(device, D3D12Slots.DEVICE_CREATE_ROOT_SIGNATURE, D3D12Descriptors.HR_ROOT_SIGNATURE,
                0, D3D12Native.blobPointer(blob), D3D12Native.blobSize(blob), iid, result)
        } } finally { D3D12Native.release(blob) }
    }

    private fun createPipeline(a: Arena, dxil: ByteArray): MemorySegment {
        val bytecode = a.allocateFrom(JAVA_BYTE, *dxil)
        val library = a.allocate(32, 8)
        library.set(ADDRESS, 0, bytecode); library.set(JAVA_LONG, 8, dxil.size.toLong())
        val hitGroup = a.allocate(40, 8)
        hitGroup.set(ADDRESS, 0, wide(a, "HitGroup")); hitGroup.set(ADDRESS, 24, wide(a, "ClosestHit"))
        val shaderConfig = a.allocate(8, 4)
        shaderConfig.set(JAVA_INT, 0, 16); shaderConfig.set(JAVA_INT, 4, 8)
        val rootPointer = a.allocate(ADDRESS).also { it.set(ADDRESS, 0, root) }
        // Primary closest-hit launches shadow and reflection rays; those hits do not recurse further.
        val pipelineConfig = a.allocate(JAVA_INT).also { it.set(JAVA_INT, 0, 2) }
        val subobjects = a.allocate(80, 8)
        listOf(5 to library, 11 to hitGroup, 9 to shaderConfig, 1 to rootPointer, 10 to pipelineConfig).forEachIndexed { i, (type, value) ->
            subobjects.set(JAVA_INT, i * 16L, type); subobjects.set(ADDRESS, i * 16L + 8, value)
        }
        val desc = a.allocate(16, 8)
        desc.set(JAVA_INT, 0, 3); desc.set(JAVA_INT, 4, 5); desc.set(ADDRESS, 8, subobjects)
        return out(a, IID_STATE, "CreateStateObject") { iid, result -> D3D12Native.comInt(device, stateCall.slot, stateCall.descriptor, desc, iid, result) }
    }

    private fun createSwapChain(a: Arena, factory: MemorySegment, hwnd: MemorySegment): MemorySegment {
        val desc = a.allocate(48, 4)
        desc.set(JAVA_INT, 0, width); desc.set(JAVA_INT, 4, height); desc.set(JAVA_INT, 8, 28)
        desc.set(JAVA_INT, 16, 1); desc.set(JAVA_INT, 24, 0x20); desc.set(JAVA_INT, 28, 2); desc.set(JAVA_INT, 36, 4)
        val result = a.allocate(ADDRESS)
        checkHr(D3D12Native.comInt(factory, D3D12Slots.FACTORY_CREATE_SWAP_CHAIN_FOR_HWND, D3D12Descriptors.CREATE_SWAP_CHAIN_FOR_HWND,
            queue, hwnd, desc, MemorySegment.NULL, MemorySegment.NULL, result), "CreateSwapChainForHwnd")
        val base = result.outObject("swap chain")
        return try { out(a, IID_SWAP_CHAIN, "IDXGISwapChain3") { iid, out -> D3D12Native.queryInterface(base, iid, out) } }
        finally { D3D12Native.release(base) }
    }

    private fun selectAdapter(a: Arena, factory: MemorySegment): MemorySegment {
        val requested = System.getProperty("caelum.museum.gpu", "auto").lowercase()
        val vendor = when (requested) {
            "auto" -> null
            "amd" -> 0x1002
            "nvidia" -> 0x10de
            "intel" -> 0x8086
            else -> error("demoGpu must be auto, amd, nvidia or intel; got '$requested'")
        }
        val dxgi = profile.catalogs.first { it.interfaceDeclaration("IDXGIAdapter1") != null }
        val invoker = NativeApiInvoker(dxgi, types = profile.types)
        val layout = dxgi.records.getValue("DXGI_ADAPTER_DESC1")
        fun offset(name: String): Long = layout.fields.single { it.name == name }.offsetBits!! / 8
        var index = 0
        while (true) {
            val result = a.allocate(ADDRESS)
            val status = D3D12Native.comInt(factory, D3D12Slots.FACTORY6_ENUM_ADAPTER_BY_GPU_PREFERENCE,
                D3D12Descriptors.ENUM_ADAPTER_BY_GPU_PREFERENCE, index, 2, a.guid(IID_ADAPTER.guidValue()), result)
            if (status == 0x887a0002.toInt()) break // DXGI_ERROR_NOT_FOUND
            val adapter = result.get(ADDRESS, 0)
            if (status < 0) {
                if (adapter.address() != 0L) D3D12Native.release(adapter)
                checkHr(status, "EnumAdapterByGpuPreference($index)")
            }
            check(adapter.address() != 0L) { "DXGI returned a null adapter" }
            var selected = false
            try {
                val desc = a.allocate(layout.size!!, layout.alignment!!)
                checkHr(invoker.comMethod(adapter, "IDXGIAdapter1", "GetDesc1").invokeWithArguments(adapter, desc) as Int, "IDXGIAdapter1.GetDesc1")
                val name = desc.asSlice(offset("Description"), 256).getString(0, Charsets.UTF_16LE)
                val candidateVendor = desc.get(JAVA_INT, offset("VendorId"))
                val candidateDevice = desc.get(JAVA_INT, offset("DeviceId"))
                val software = desc.get(JAVA_INT, offset("Flags")) and 2 != 0
                println("DXGI_ADAPTER[$index]=$name VENDOR=0x${candidateVendor.toString(16)} DEVICE=0x${candidateDevice.toString(16)} SOFTWARE=$software")
                if (!software && (vendor == null || candidateVendor == vendor)) {
                    adapterVendorId = candidateVendor
                    adapterDeviceId = candidateDevice
                    selected = true
                    println("SELECTED_GPU=$name REQUEST=$requested VENDOR=0x${candidateVendor.toString(16)}")
                    return adapter
                }
            } finally {
                if (!selected) D3D12Native.release(adapter)
            }
            index++
        }
        error("No hardware DXGI adapter matches demoGpu=$requested; no other vendor was selected")
    }

    private fun buffer(a: Arena, size: Long, heapType: Int, state: Int, flags: Int = 0): Buffer {
        val heap = a.allocate(20, 4)
        heap.set(JAVA_INT, 0, heapType); heap.set(JAVA_INT, 12, 1); heap.set(JAVA_INT, 16, 1)
        val desc = a.allocate(56, 8)
        desc.set(JAVA_INT, 0, 1); desc.set(JAVA_LONG, 16, size); desc.set(JAVA_INT, 24, 1)
        desc.set(JAVA_SHORT, 28, 1); desc.set(JAVA_SHORT, 30, 1)
        desc.set(JAVA_INT, 36, 1); desc.set(JAVA_INT, 44, 1); desc.set(JAVA_INT, 48, flags)
        val resource = own(out(a, IID_RESOURCE, "CreateCommittedResource($size bytes)") { iid, result ->
            D3D12Native.comInt(device, D3D12Slots.DEVICE_CREATE_COMMITTED_RESOURCE, D3D12Descriptors.HR_CREATE_RESOURCE,
                heap, 0, desc, state, MemorySegment.NULL, iid, result)
        })
        val address = D3D12Native.comLong(resource, D3D12Slots.RESOURCE_GPU_ADDRESS, D3D12Descriptors.ULONG64_RESOURCE)
        return Buffer(resource, address, size)
    }
    private fun map(a: Arena, buffer: Buffer): MemorySegment {
        val result = a.allocate(ADDRESS)
        checkHr(D3D12Native.comInt(buffer.resource, D3D12Slots.RESOURCE_MAP, D3D12Descriptors.HR_MAP, 0, MemorySegment.NULL, result), "Map")
        return result.outObject("Map").reinterpret(buffer.size)
    }
    private fun unmap(buffer: Buffer) = D3D12Native.comVoid(buffer.resource, D3D12Slots.RESOURCE_UNMAP, D3D12Descriptors.VOID_UNMAP, 0, MemorySegment.NULL)
    private fun upload(a: Arena, buffer: Buffer, source: MemorySegment) {
        require(source.byteSize() <= buffer.size)
        val destination = map(a, buffer)
        try { MemorySegment.copy(source, 0, destination, 0, source.byteSize()) } finally { unmap(buffer) }
    }
    private fun textureLocation(a: Arena, resource: MemorySegment, placed: Boolean): MemorySegment = a.allocate(48, 8).also {
        it.set(ADDRESS, 0, resource)
        if (placed) {
            it.set(JAVA_INT, 8, 1); it.set(JAVA_LONG, 16, DxrSceneData.IMAGE_OFFSET.toLong())
            it.set(JAVA_INT, 24, 28); it.set(JAVA_INT, 28, width); it.set(JAVA_INT, 32, height)
            it.set(JAVA_INT, 36, 1); it.set(JAVA_INT, 40, rowPitch)
        }
    }
    private fun transition(a: Arena, resource: MemorySegment, before: Int, after: Int) {
        val barrier = a.allocate(32, 8)
        barrier.set(ADDRESS, 8, resource); barrier.set(JAVA_INT, 16, -1)
        barrier.set(JAVA_INT, 20, before); barrier.set(JAVA_INT, 24, after)
        D3D12Native.comVoid(list, D3D12Slots.LIST_RESOURCE_BARRIER, D3D12Descriptors.VOID_BARRIERS, 1, barrier)
    }
    private fun uavBarrier(a: Arena, resource: MemorySegment) {
        val barrier = a.allocate(32, 8)
        barrier.set(JAVA_INT, 0, 2); barrier.set(ADDRESS, 8, resource)
        D3D12Native.comVoid(list, D3D12Slots.LIST_RESOURCE_BARRIER, D3D12Descriptors.VOID_BARRIERS, 1, barrier)
    }
    private fun reset() {
        checkHr(D3D12Native.comInt(allocator, D3D12Slots.ALLOCATOR_RESET, D3D12Descriptors.HR_RESET_ALLOCATOR), "Reset allocator")
        checkHr(D3D12Native.comInt(list, D3D12Slots.LIST_RESET, D3D12Descriptors.HR_RESET_LIST, allocator, MemorySegment.NULL), "Reset command list")
    }
    private fun submit(a: Arena) {
        checkHr(D3D12Native.comInt(list, D3D12Slots.LIST_CLOSE, D3D12Descriptors.HR_NO_ARGS), "Close command list")
        val lists = a.allocate(ADDRESS).also { it.set(ADDRESS, 0, list) }
        D3D12Native.comVoid(queue, D3D12Slots.QUEUE_EXECUTE, D3D12Descriptors.VOID_EXECUTE, 1, lists)
        val value = ++fenceValue
        checkHr(D3D12Native.comInt(queue, D3D12Slots.QUEUE_SIGNAL, D3D12Descriptors.HR_SIGNAL, fence, value), "Signal fence")
        waitForFence()
    }
    private fun waitForFence() {
        if (fence.address() == 0L || fenceValue == 0L) return
        val deadline = System.nanoTime() + 30_000_000_000L
        while (true) {
            val completed = D3D12Native.comLong(fence, D3D12Slots.FENCE_COMPLETED_VALUE, D3D12Descriptors.ULONG64_NO_ARGS)
            check(completed != -1L) { "DXR device was removed while waiting for the GPU" }
            if (completed >= fenceValue) return
            if (System.nanoTime() >= deadline) {
                checkHealth()
                error("DXR GPU fence timed out (completed=$completed, target=$fenceValue)")
            }
            Thread.sleep(1)
        }
    }
    override fun close() {
        if (closed) return
        var failure: Throwable? = null
        fun cleanup(block: () -> Unit) {
            try { block() } catch (next: Throwable) {
                if (failure == null) failure = next else failure.addSuppressed(next)
            }
        }
        cleanup { waitForFence() }
        closed = true
        owned.asReversed().forEach { resource -> cleanup { D3D12Native.release(resource) } }
        owned.clear()
        failure?.let { throw it }
    }
    private fun own(resource: MemorySegment): MemorySegment = resource.also { owned += it }
    private fun out(a: Arena, iid: String, operation: String, native: (MemorySegment, MemorySegment) -> Int): MemorySegment {
        val result = a.allocate(ADDRESS)
        val status = native(a.guid(iid.guidValue()), result)
        if (status < 0) {
            val pointer = result.get(ADDRESS, 0)
            if (pointer.address() != 0L) D3D12Native.release(pointer)
            checkHr(status, operation)
        }
        return result.outObject(operation)
    }
    private fun wide(a: Arena, text: String): MemorySegment = a.allocate((text.length + 1L) * 2, 2).also {
        text.forEachIndexed { i, character -> it.set(JAVA_SHORT, i * 2L, character.code.toShort()) }
    }
}

private fun align(value: Long, alignment: Long): Long = (value + alignment - 1) and -alignment
private const val GENERIC_READ = 0xAC3
private const val UAV = 0x8
private const val AS_STATE = 0x400000
private const val COPY_DEST = 0x400
private const val COPY_SOURCE = 0x800
private const val IID_FACTORY = "c1b6694f-ff09-44a9-b03c-77900a0a1d17"
private const val IID_ADAPTER = "29038f61-3839-4626-91fd-086879011a05"
private const val IID_DEVICE5 = "8b4f173b-2fea-4b80-8f58-4307191ab95d"
private const val IID_LIST4 = "8754318e-d3a9-4541-98cf-645b50dc4874"
private const val IID_QUEUE = "0ec870a6-5d7e-4c22-8cfc-5baae07616ed"
private const val IID_ALLOCATOR = "6102dee4-af59-4b09-b999-b44d73f09b24"
private const val IID_SWAP_CHAIN = "94d99bdb-f1f8-4ab0-b236-7da0170edab1"
private const val IID_RESOURCE = "696442be-a72e-4059-bc79-5b5c98040fad"
private const val IID_ROOT = "c54a6b66-72df-4ee8-8be5-a946a1429214"
private const val IID_FENCE = "0a753dcf-c4d8-4b91-adf6-be5a60d95a76"
private const val IID_STATE = "47016943-fca8-4594-93ea-af258b55346d"
private const val IID_PROPERTIES = "de5fa827-9bf9-4f26-89ff-d7f56fde3860"
