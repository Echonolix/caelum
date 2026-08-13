package net.echonolix.caelum.directx.demo.d3d12

import net.echonolix.caelum.directx.demo.Mat4
import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.directx.demo.Win32DemoWindow
import net.echonolix.caelum.directx.demo.d3d12.dxr.DxrBuffer
import net.echonolix.caelum.directx.demo.d3d12.dxr.DxrExecutionContext
import net.echonolix.caelum.directx.demo.d3d12.dxr.DxrExecutionEvidence
import net.echonolix.caelum.directx.demo.d3d12.dxr.DxrFrameOutput
import net.echonolix.caelum.directx.demo.d3d12.dxr.DxrMinimalPath
import net.echonolix.caelum.directx.demo.model.StanfordDragonMesh
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.security.MessageDigest
import kotlin.math.PI

private const val WIDTH = 960
private const val HEIGHT = 720
private const val FRAME_COUNT = 2
private const val FENCE_TIMEOUT_SECONDS = 10L
private const val DXR_LIBRARY_SHA256 = "C1C77EAEB770F7680C755E01BC87E3A37393EF929CDDD99B8B4A2BFEF1341276"

public fun main(): Unit {
    check(System.getProperty("os.name").startsWith("Windows", true)) { "Direct3D 12 requires Windows" }
    require(System.getProperty("caelum.directx.version", "12") == "12") { "This is the Direct3D 12 launcher" }
    val hidden = System.getProperty("caelum.demo.hidden", "false").toBooleanStrict()
    val requestedSeconds = System.getProperty("caelum.demo.seconds", "0").toDouble()
    val seconds = if (hidden && requestedSeconds == 0.0) 0.35 else requestedSeconds
    Win32DemoWindow("Caelum DirectX 12 - Stanford Dragon", WIDTH, HEIGHT, hidden).use { window ->
        D3D12TeapotRenderer(window.hwnd, WIDTH, HEIGHT).use { renderer ->
            println("DIRECTX_BACKEND=D3D12")
            println("MODEL_VERTICES=${renderer.vertexCount}")
            println("MODEL_INDICES=${renderer.indexCount}")
            if (System.getProperty("caelum.demo.dxr", "false").toBooleanStrict()) {
                val resource = requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("net/echonolix/caelum/directx/demo/d3d12/dxr/MinimalRayTracing.dxil")) { "DXR requested but MinimalRayTracing.dxil was not packaged" }
                val evidence = resource.use { it.readAllBytes() }
                val digest = MessageDigest.getInstance("SHA-256").digest(evidence).joinToString("") { "%02X".format(it.toInt() and 0xff) }
                check(digest == DXR_LIBRARY_SHA256) { "MinimalRayTracing.dxil SHA-256 mismatch: $digest" }
                val samples = System.getProperty("caelum.demo.dxr.samples", "8").toInt()
                val result = renderer.executeDxr(evidence, samples)
                println("DXR_RAYTRACING_TIER=${result.raytracingTier}")
                println("DXR_BUILD_RAYTRACING_CALLS=${result.buildRaytracingCalls}")
                println("DXR_DISPATCH_RAYS_CALLS=${result.dispatchRaysCalls}")
                println("DXR_SAMPLES_PER_PIXEL=${result.samplesPerPixel}")
                println("DXR_PRIMARY_RAY_COUNT=${result.primaryRayCount}")
                println("DXR_MAX_RECURSION_DEPTH=${result.maximumRecursionDepth}")
                println("DXR_GPU_MARKER=0x${result.gpuMarker.toUInt().toString(16)}")
                println("DXR_HARDWARE_PATH_OK=${result.hit}")
            } else println("DXR_HARDWARE_PATH=DISABLED")
            val start = System.nanoTime(); var frames = 0
            while (!window.pollCloseRequested()) {
                val elapsed = (System.nanoTime() - start) / 1e9
                if (seconds > 0.0 && frames > 0 && elapsed >= seconds) break
                renderer.render(elapsed.toFloat(), hidden)
                frames++
            }
            check(frames > 0)
            renderer.checkDeviceHealth()
            println("RENDERED_FRAMES=$frames")
            println("CAELUM_DIRECTX12_DEMO_OK")
            println("CAELUM_STANFORD_DRAGON_DEMO_OK")
        }
    }
}

internal class D3D12TeapotRenderer(hwnd: MemorySegment, private val width: Int, private val height: Int) : AutoCloseable {
    private val arena = Arena.ofConfined()
    private val owned = ArrayList<MemorySegment>()
    private val mesh = StanfordDragonMesh.load()
    private val camera = Vec3(0f, 0.15f, 4.6f)
    private val view = Mat4.lookAtRightHanded(camera, Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f))
    internal val vertexCount get() = mesh.vertexCount
    internal val indexCount get() = mesh.indexCount

    internal lateinit var device: MemorySegment; private lateinit var factory: MemorySegment; private lateinit var adapter: MemorySegment
    internal lateinit var queue: MemorySegment; private lateinit var swapChain: MemorySegment
    internal lateinit var commandAllocator: MemorySegment; internal lateinit var commandList: MemorySegment
    private lateinit var rootSignature: MemorySegment; private lateinit var pipelineState: MemorySegment
    private lateinit var rtvHeap: MemorySegment; private lateinit var dsvHeap: MemorySegment
    private lateinit var depth: MemorySegment; private lateinit var vertexBuffer: MemorySegment
    private lateinit var indexBuffer: MemorySegment; private lateinit var fence: MemorySegment
    private val backBuffers = arrayOfNulls<MemorySegment>(FRAME_COUNT)
    private var rtvStart = 0L; private var rtvIncrement = 0; private var dsv = 0L
    private var fenceValue = 0L; private var closed = false
    private lateinit var viewport: MemorySegment; private lateinit var scissor: MemorySegment
    private lateinit var vbv: MemorySegment; private lateinit var ibv: MemorySegment
    private lateinit var constants: MemorySegment; private lateinit var clearColor: MemorySegment
    private lateinit var commandLists: MemorySegment
    private var dxrFrame: DxrFrameOutput? = null

    init {
        try {
            D3D12Native.ensureAvailable()
            factory = own(createFactory())
            adapter = own(selectHighPerformanceAdapter())
            device = own(createDevice(adapter))
            queue = own(createQueue())
            swapChain = own(createSwapChain(hwnd))
            commandAllocator = own(createAllocator())
            rtvHeap = own(createDescriptorHeap(D3D12_DESCRIPTOR_HEAP_TYPE_RTV, FRAME_COUNT))
            dsvHeap = own(createDescriptorHeap(D3D12_DESCRIPTOR_HEAP_TYPE_DSV, 1))
            rtvIncrement = D3D12Native.comInt(device, D3D12Slots.DEVICE_GET_DESCRIPTOR_INCREMENT, D3D12Descriptors.UINT_ONE_INT, D3D12_DESCRIPTOR_HEAP_TYPE_RTV)
            rtvStart = descriptorHeapStart(rtvHeap)
            dsv = descriptorHeapStart(dsvHeap)
            createBackBufferViews()
            depth = own(createDepthBuffer())
            D3D12Native.comVoid(device, D3D12Slots.DEVICE_CREATE_DSV, D3D12Descriptors.VOID_CREATE_VIEW, depth, MemorySegment.NULL, handle(dsv))
            rootSignature = own(createRootSignature())
            pipelineState = own(createPipelineState())
            commandList = own(createCommandList())
            checkHr(D3D12Native.comInt(commandList, D3D12Slots.LIST_CLOSE, D3D12Descriptors.HR_NO_ARGS), "initial command-list close")
            vertexBuffer = own(createUploadBuffer(mesh.interleavedVertices.size.toLong() * 4, MemorySegment.ofArray(mesh.interleavedVertices)))
            indexBuffer = own(createUploadBuffer(mesh.indices.size.toLong() * 4, MemorySegment.ofArray(mesh.indices)))
            fence = own(createFence())
            initializeFrameData()
        } catch (failure: Throwable) { close(); throw failure }
    }

    internal fun render(time: Float, hidden: Boolean) {
        check(!closed)
        waitForGpu()
        checkHr(D3D12Native.comInt(commandAllocator, D3D12Slots.ALLOCATOR_RESET, D3D12Descriptors.HR_RESET_ALLOCATOR), "allocator reset")
        checkHr(D3D12Native.comInt(commandList, D3D12Slots.LIST_RESET, D3D12Descriptors.HR_RESET_LIST, commandAllocator, pipelineState), "command-list reset")
        val frame = D3D12Native.comInt(swapChain, D3D12Slots.SWAP_CHAIN3_GET_CURRENT_BACK_BUFFER_INDEX, D3D12Descriptors.UINT_NO_ARGS)
        val backBuffer = backBuffers[frame]!!; val rtv = rtvStart + frame * rtvIncrement.toLong()
        dxrFrame?.let {
            copyRaytracedFrame(backBuffer, it)
            submitOpenCommandListAndWait()
            checkHr(D3D12Native.comInt(swapChain, D3D12Slots.SWAP_CHAIN_PRESENT, D3D12Descriptors.PRESENT, if (hidden) 0 else 1, 0), "Present DXR frame")
            return
        }
        transition(backBuffer, D3D12_RESOURCE_STATE_PRESENT, D3D12_RESOURCE_STATE_RENDER_TARGET)
        writeConstants(time)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_SET_ROOT_SIGNATURE, D3D12Descriptors.VOID_ROOT_SIG, rootSignature)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_SET_ROOT_CONSTANTS, D3D12Descriptors.VOID_ROOT_CONSTANTS, 0, ROOT_DWORDS, constants, 0)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_RS_VIEWPORTS, D3D12Descriptors.VOID_VIEWPORTS, 1, viewport)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_RS_SCISSORS, D3D12Descriptors.VOID_VIEWPORTS, 1, scissor)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_OM_RENDER_TARGETS, D3D12Descriptors.VOID_RENDER_TARGETS, 1, handle(rtv), 1, handle(dsv))
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_CLEAR_RTV, D3D12Descriptors.VOID_CLEAR_RTV, handle(rtv), clearColor, 0, MemorySegment.NULL)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_CLEAR_DSV, D3D12Descriptors.VOID_CLEAR_DSV, handle(dsv), D3D12_CLEAR_FLAG_DEPTH, 1f, 0.toByte(), 0, MemorySegment.NULL)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_IA_TOPOLOGY, D3D12Descriptors.VOID_TOPOLOGY, D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_IA_VERTEX_BUFFERS, D3D12Descriptors.VOID_VERTEX_BUFFERS, 0, 1, vbv)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_IA_INDEX_BUFFER, D3D12Descriptors.VOID_INDEX_BUFFER, ibv)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_DRAW_INDEXED, D3D12Descriptors.VOID_DRAW_INDEXED, mesh.indexCount, 1, 0, 0, 0)
        transition(backBuffer, D3D12_RESOURCE_STATE_RENDER_TARGET, D3D12_RESOURCE_STATE_PRESENT)
        submitOpenCommandListAndWait()
        checkHr(D3D12Native.comInt(swapChain, D3D12Slots.SWAP_CHAIN_PRESENT, D3D12Descriptors.PRESENT, if (hidden) 0 else 1, 0), "Present")
    }

    /** Copies the GPU-produced DXR image directly into the swap-chain texture; it never falls back to raster in DXR mode. */
    private fun copyRaytracedFrame(backBuffer: MemorySegment, frame: DxrFrameOutput) {
        require(frame.width == width && frame.height == height) { "DXR frame dimensions ${frame.width}x${frame.height} do not match swap chain ${width}x${height}" }
        require(frame.rowPitch >= width * 4 && frame.rowPitch % 256 == 0) { "DXR frame row pitch must satisfy D3D12 texture-copy alignment" }
        transition(backBuffer, D3D12_RESOURCE_STATE_PRESENT, D3D12_RESOURCE_STATE_COPY_DEST)
        D3D12Native.comVoid(commandList, D3D12Slots.LIST_COPY_TEXTURE_REGION, D3D12Descriptors.VOID_COPY_TEXTURE_REGION,
            textureCopyLocation(backBuffer), 0, 0, 0, placedFootprintCopyLocation(frame), MemorySegment.NULL)
        transition(backBuffer, D3D12_RESOURCE_STATE_COPY_DEST, D3D12_RESOURCE_STATE_PRESENT)
    }

    /** Submission boundary intentionally exposed to the optional real-DXR recorder. */
    internal fun submitOpenCommandListAndWait(afterSubmit: () -> Unit = {}) {
        checkHr(D3D12Native.comInt(commandList, D3D12Slots.LIST_CLOSE, D3D12Descriptors.HR_NO_ARGS), "command-list close")
        commandLists.set(ValueLayout.ADDRESS, 0, commandList)
        D3D12Native.comVoid(queue, D3D12Slots.QUEUE_EXECUTE, D3D12Descriptors.VOID_EXECUTE, 1, commandLists)
        signalAndWait(); afterSubmit()
    }

    private fun initializeFrameData() {
        viewport = arena.allocate(24, 4).also { it.set(ValueLayout.JAVA_FLOAT,0,0f);it.set(ValueLayout.JAVA_FLOAT,4,0f);it.set(ValueLayout.JAVA_FLOAT,8,width.toFloat());it.set(ValueLayout.JAVA_FLOAT,12,height.toFloat());it.set(ValueLayout.JAVA_FLOAT,16,0f);it.set(ValueLayout.JAVA_FLOAT,20,1f) }
        scissor = arena.allocate(16,4).also { it.set(ValueLayout.JAVA_INT,0,0);it.set(ValueLayout.JAVA_INT,4,0);it.set(ValueLayout.JAVA_INT,8,width);it.set(ValueLayout.JAVA_INT,12,height) }
        constants = arena.allocate(ROOT_DWORDS * 4L, 16); clearColor = arena.allocate(16,4)
        floatArrayOf(.025f,.04f,.065f,1f).forEachIndexed { i,v -> clearColor.setAtIndex(ValueLayout.JAVA_FLOAT,i.toLong(),v) }
        vbv = arena.allocate(16,8).also { it.set(ValueLayout.JAVA_LONG,0,gpuAddress(vertexBuffer));it.set(ValueLayout.JAVA_INT,8,mesh.interleavedVertices.size*4);it.set(ValueLayout.JAVA_INT,12,24) }
        ibv = arena.allocate(16,8).also { it.set(ValueLayout.JAVA_LONG,0,gpuAddress(indexBuffer));it.set(ValueLayout.JAVA_INT,8,mesh.indices.size*4);it.set(ValueLayout.JAVA_INT,12,DXGI_FORMAT_R32_UINT) }
        commandLists = arena.allocate(ValueLayout.ADDRESS)
    }

    private fun writeConstants(time: Float) {
        val model=Mat4.rotationY(time*.48f); val projection=Mat4.perspectiveRightHanded((42*PI/180).toFloat(),width.toFloat()/height,.1f,20f)
        val values=Mat4.multiply(projection,Mat4.multiply(view,model))+model+floatArrayOf(4.2f,5.2f,5.5f,1f,camera.x,camera.y,camera.z,1f,GOLD_MATERIAL.baseRed,GOLD_MATERIAL.baseGreen,GOLD_MATERIAL.baseBlue,GOLD_MATERIAL.roughness)
        values.forEachIndexed { i,v -> constants.setAtIndex(ValueLayout.JAVA_FLOAT,i.toLong(),v) }
    }

    private fun createDevice(adapter:MemorySegment): MemorySegment = output("D3D12CreateDevice") { iid,out -> D3D12Native.createDevice(adapter,iid,out) }
    private fun createFactory(): MemorySegment = output("CreateDXGIFactory2", IID_FACTORY6) { iid,out -> D3D12Native.createFactory(iid,out) }
    private fun selectHighPerformanceAdapter():MemorySegment {val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(factory,D3D12Slots.FACTORY6_ENUM_ADAPTER_BY_GPU_PREFERENCE,D3D12Descriptors.ENUM_ADAPTER_BY_GPU_PREFERENCE,0,DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,arena.guid(IID_ADAPTER1),out),"EnumAdapterByGpuPreference(HIGH_PERFORMANCE)");return out.outObject("high-performance adapter")}
    private fun createQueue(): MemorySegment { val d=arena.allocate(16,4); return deviceOutput(D3D12Slots.DEVICE_CREATE_QUEUE,D3D12Descriptors.HR_DESC_IID_OUT,d,IID_QUEUE,"CreateCommandQueue") }
    private fun createAllocator(): MemorySegment = deviceOutput(D3D12Slots.DEVICE_CREATE_ALLOCATOR,D3D12Descriptors.HR_INT_IID_OUT,D3D12_COMMAND_LIST_TYPE_DIRECT,IID_ALLOCATOR,"CreateCommandAllocator")
    private fun createDescriptorHeap(type:Int,count:Int):MemorySegment { val d=arena.allocate(16,4);d.set(ValueLayout.JAVA_INT,0,type);d.set(ValueLayout.JAVA_INT,4,count);return deviceOutput(D3D12Slots.DEVICE_CREATE_DESCRIPTOR_HEAP,D3D12Descriptors.HR_DESC_IID_OUT,d,IID_DESCRIPTOR_HEAP,"CreateDescriptorHeap") }
    private fun createSwapChain(hwnd:MemorySegment):MemorySegment {
        val desc=arena.allocate(48,4);desc.set(ValueLayout.JAVA_INT,0,width);desc.set(ValueLayout.JAVA_INT,4,height);desc.set(ValueLayout.JAVA_INT,8,DXGI_FORMAT_R8G8B8A8_UNORM);desc.set(ValueLayout.JAVA_INT,16,1);desc.set(ValueLayout.JAVA_INT,24,DXGI_USAGE_RENDER_TARGET_OUTPUT);desc.set(ValueLayout.JAVA_INT,28,FRAME_COUNT);desc.set(ValueLayout.JAVA_INT,32,DXGI_SCALING_STRETCH);desc.set(ValueLayout.JAVA_INT,36,DXGI_SWAP_EFFECT_FLIP_DISCARD);desc.set(ValueLayout.JAVA_INT,40,DXGI_ALPHA_MODE_UNSPECIFIED)
        val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(factory,D3D12Slots.FACTORY_CREATE_SWAP_CHAIN_FOR_HWND,D3D12Descriptors.CREATE_SWAP_CHAIN_FOR_HWND,queue,hwnd,desc,MemorySegment.NULL,MemorySegment.NULL,out),"CreateSwapChainForHwnd")
        val base=out.outObject("IDXGISwapChain1"); return try { query(base,IID_SWAP_CHAIN3,"IDXGISwapChain3") } finally { D3D12Native.release(base) }
    }
    private fun createBackBufferViews(){for(i in 0 until FRAME_COUNT){val out=arena.allocate(ValueLayout.ADDRESS);val iid=arena.guid(IID_RESOURCE);checkHr(D3D12Native.comInt(swapChain,D3D12Slots.SWAP_CHAIN_GET_BUFFER,D3D12Descriptors.GET_BUFFER,i,iid,out),"GetBuffer($i)");val b=own(out.outObject("back buffer"));backBuffers[i]=b;D3D12Native.comVoid(device,D3D12Slots.DEVICE_CREATE_RTV,D3D12Descriptors.VOID_CREATE_VIEW,b,MemorySegment.NULL,handle(rtvStart+i*rtvIncrement.toLong()))}}
    private fun createDepthBuffer():MemorySegment { val clear=arena.allocate(20,4);clear.set(ValueLayout.JAVA_INT,0,DXGI_FORMAT_D24_UNORM_S8_UINT);clear.set(ValueLayout.JAVA_FLOAT,4,1f);return committedResource(D3D12_HEAP_TYPE_DEFAULT,textureDesc(width,height,DXGI_FORMAT_D24_UNORM_S8_UINT,D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL),D3D12_RESOURCE_STATE_DEPTH_WRITE,clear) }
    private fun createUploadBuffer(bytes:Long,data:MemorySegment):MemorySegment {val resource=committedResource(D3D12_HEAP_TYPE_UPLOAD,bufferDesc(bytes),D3D12_RESOURCE_STATE_GENERIC_READ,MemorySegment.NULL);val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(resource,D3D12Slots.RESOURCE_MAP,D3D12Descriptors.HR_MAP,0,MemorySegment.NULL,out),"Map upload");out.get(ValueLayout.ADDRESS,0).reinterpret(bytes).copyFrom(data.asSlice(0,bytes));D3D12Native.comVoid(resource,D3D12Slots.RESOURCE_UNMAP,D3D12Descriptors.VOID_UNMAP,0,MemorySegment.NULL);return resource}
    private fun committedResource(heapType:Int,desc:MemorySegment,state:Int,clear:MemorySegment):MemorySegment {val heap=arena.allocate(20,4);heap.set(ValueLayout.JAVA_INT,0,heapType);heap.set(ValueLayout.JAVA_INT,12,1);heap.set(ValueLayout.JAVA_INT,16,1);val iid=arena.guid(IID_RESOURCE);val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(device,D3D12Slots.DEVICE_CREATE_COMMITTED_RESOURCE,D3D12Descriptors.HR_CREATE_RESOURCE,heap,0,desc,state,clear,iid,out),"CreateCommittedResource");return out.outObject("ID3D12Resource")}
    private fun createRootSignature():MemorySegment {val param=arena.allocate(32,8);param.set(ValueLayout.JAVA_INT,0,D3D12_ROOT_PARAMETER_TYPE_32BIT_CONSTANTS);param.set(ValueLayout.JAVA_INT,8,0);param.set(ValueLayout.JAVA_INT,12,0);param.set(ValueLayout.JAVA_INT,16,ROOT_DWORDS);param.set(ValueLayout.JAVA_INT,24,D3D12_SHADER_VISIBILITY_ALL);val desc=arena.allocate(40,8);desc.set(ValueLayout.JAVA_INT,0,1);desc.set(ValueLayout.ADDRESS,8,param);desc.set(ValueLayout.JAVA_INT,32,D3D12_ROOT_SIGNATURE_FLAG_ALLOW_INPUT_ASSEMBLER_INPUT_LAYOUT);val blobOut=arena.allocate(ValueLayout.ADDRESS);val errorsOut=arena.allocate(ValueLayout.ADDRESS);val hr=D3D12Native.serializeRootSignature(desc,blobOut,errorsOut);val errors=errorsOut.get(ValueLayout.ADDRESS,0);if(hr<0){val msg=if(errors.address()!=0L)D3D12Native.blobText(errors) else "";if(errors.address()!=0L)D3D12Native.release(errors);error("SerializeRootSignature failed: $msg")};if(errors.address()!=0L)D3D12Native.release(errors);val blob=blobOut.outObject("root signature blob");return try {val iid=arena.guid(IID_ROOT_SIGNATURE);val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(device,D3D12Slots.DEVICE_CREATE_ROOT_SIGNATURE,D3D12Descriptors.HR_ROOT_SIGNATURE,0,D3D12Native.blobPointer(blob),D3D12Native.blobSize(blob),iid,out),"CreateRootSignature");out.outObject("root signature")}finally{D3D12Native.release(blob)}}
    private fun createPipelineState():MemorySegment {val vs=compile(VS,"VSMain","vs_5_0");val ps=compile(PS,"PSMain","ps_5_0");try{val p=arena.allocate(656,8);p.set(ValueLayout.ADDRESS,0,rootSignature);shader(p,8,vs);shader(p,24,ps);p.set(ValueLayout.JAVA_INT,136,D3D12_BLEND_ONE);p.set(ValueLayout.JAVA_INT,140,D3D12_BLEND_ZERO);p.set(ValueLayout.JAVA_INT,144,D3D12_BLEND_OP_ADD);p.set(ValueLayout.JAVA_INT,148,D3D12_BLEND_ONE);p.set(ValueLayout.JAVA_INT,152,D3D12_BLEND_ZERO);p.set(ValueLayout.JAVA_INT,156,D3D12_BLEND_OP_ADD);p.set(ValueLayout.JAVA_INT,160,D3D12_LOGIC_OP_NOOP);p.set(ValueLayout.JAVA_BYTE,164,D3D12_COLOR_WRITE_ENABLE_ALL.toByte());p.set(ValueLayout.JAVA_INT,448,-1);writeTeapotRasterizerState(p,452);p.set(ValueLayout.JAVA_INT,496,1);p.set(ValueLayout.JAVA_INT,500,D3D12_DEPTH_WRITE_MASK_ALL);p.set(ValueLayout.JAVA_INT,504,D3D12_COMPARISON_FUNC_LESS);p.set(ValueLayout.JAVA_BYTE,512,0xff.toByte());p.set(ValueLayout.JAVA_BYTE,513,0xff.toByte());initializeStencilFace(p,516);initializeStencilFace(p,532);val elements=inputElements();p.set(ValueLayout.ADDRESS,552,elements);p.set(ValueLayout.JAVA_INT,560,2);p.set(ValueLayout.JAVA_INT,572,D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE);p.set(ValueLayout.JAVA_INT,576,1);p.set(ValueLayout.JAVA_INT,580,DXGI_FORMAT_R8G8B8A8_UNORM);p.set(ValueLayout.JAVA_INT,612,DXGI_FORMAT_D24_UNORM_S8_UINT);p.set(ValueLayout.JAVA_INT,616,1);return deviceOutput(D3D12Slots.DEVICE_CREATE_GRAPHICS_PSO,D3D12Descriptors.HR_DESC_IID_OUT,p,IID_PIPELINE_STATE,"CreateGraphicsPipelineState")}finally{D3D12Native.release(ps);D3D12Native.release(vs)}}
    private fun createCommandList():MemorySegment {val iid=arena.guid(IID_GRAPHICS_LIST);val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(device,D3D12Slots.DEVICE_CREATE_LIST,D3D12Descriptors.HR_CREATE_LIST,0,D3D12_COMMAND_LIST_TYPE_DIRECT,commandAllocator,pipelineState,iid,out),"CreateCommandList");return out.outObject("command list")}
    private fun createFence():MemorySegment {val iid=arena.guid(IID_FENCE);val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(device,D3D12Slots.DEVICE_CREATE_FENCE,D3D12Descriptors.HR_FENCE,0L,0,iid,out),"CreateFence");return out.outObject("fence")}
    private fun transition(resource:MemorySegment,before:Int,after:Int){val b=arena.allocate(32,8);b.set(ValueLayout.JAVA_INT,0,0);b.set(ValueLayout.ADDRESS,8,resource);b.set(ValueLayout.JAVA_INT,16,-1);b.set(ValueLayout.JAVA_INT,20,before);b.set(ValueLayout.JAVA_INT,24,after);D3D12Native.comVoid(commandList,D3D12Slots.LIST_RESOURCE_BARRIER,D3D12Descriptors.VOID_BARRIERS,1,b)}
    private fun signalAndWait(){val value=++fenceValue;checkHr(D3D12Native.comInt(queue,D3D12Slots.QUEUE_SIGNAL,D3D12Descriptors.HR_SIGNAL,fence,value),"queue Signal");waitForFence(value,"queue submission")}
    private fun waitForGpu(){if(::fence.isInitialized && fenceValue>0)waitForFence(fenceValue,"GPU shutdown")}
    private fun waitForFence(target:Long,operation:String){val deadline=System.nanoTime()+FENCE_TIMEOUT_SECONDS*1_000_000_000L;while(true){val completed=D3D12Native.comLong(fence,D3D12Slots.FENCE_COMPLETED_VALUE,D3D12Descriptors.ULONG64_NO_ARGS);if(completed>=target)return;if(System.nanoTime()>=deadline){val reason=if(::device.isInitialized)runCatching{D3D12Native.comInt(device,D3D12Slots.DEVICE_REMOVED_REASON,D3D12Descriptors.HR_NO_ARGS)}.getOrNull()else null;val diagnostic=reason?.let{", deviceRemovedReason=0x${it.toUInt().toString(16).padStart(8,'0')}"}.orEmpty();error("$operation fence timed out after $FENCE_TIMEOUT_SECONDS seconds (completed=$completed, target=$target$diagnostic)")};Thread.onSpinWait()}}
    internal fun checkDeviceHealth()=checkHr(D3D12Native.comInt(device,D3D12Slots.DEVICE_REMOVED_REASON,D3D12Descriptors.HR_NO_ARGS),"GetDeviceRemovedReason")
    override fun close(){if(closed)return;closed=true;runCatching{waitForGpu()};owned.asReversed().forEach{runCatching{D3D12Native.release(it)}};owned.clear();arena.close()}
    private fun own(p:MemorySegment)=p.also{owned+=it}
    private fun output(name:String,iidValue:GuidValue=IID_DEVICE,call:(MemorySegment,MemorySegment)->Int):MemorySegment{val out=arena.allocate(ValueLayout.ADDRESS);checkHr(call(arena.guid(iidValue),out),name);return out.outObject(name)}
    private fun query(base:MemorySegment,iid:GuidValue,name:String)=output(name,iid){id,out->D3D12Native.queryInterface(base,id,out)}
    private fun deviceOutput(slot:Int,fd:java.lang.foreign.FunctionDescriptor,arg:Any,iid:GuidValue,name:String):MemorySegment{val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(device,slot,fd,arg,arena.guid(iid),out),name);return out.outObject(name)}
    private fun handle(value:Long)=arena.allocate(8,8).also{it.set(ValueLayout.JAVA_LONG,0,value)}
    private fun descriptorHeapStart(heap:MemorySegment):Long {val out=arena.allocate(8,8);D3D12Native.comAddress(heap,D3D12Slots.HEAP_GET_CPU_START,D3D12Descriptors.CPU_HANDLE_RETURN,out);return out.get(ValueLayout.JAVA_LONG,0)}
    private fun initializeStencilFace(p:MemorySegment,offset:Long){p.set(ValueLayout.JAVA_INT,offset,D3D12_STENCIL_OP_KEEP);p.set(ValueLayout.JAVA_INT,offset+4,D3D12_STENCIL_OP_KEEP);p.set(ValueLayout.JAVA_INT,offset+8,D3D12_STENCIL_OP_KEEP);p.set(ValueLayout.JAVA_INT,offset+12,D3D12_COMPARISON_FUNC_ALWAYS)}
    private fun gpuAddress(r:MemorySegment)=D3D12Native.comLong(r,D3D12Slots.RESOURCE_GPU_ADDRESS,D3D12Descriptors.ULONG64_RESOURCE)
    private fun bufferDesc(bytes:Long,flags:Int=0)=arena.allocate(56,8).also{it.set(ValueLayout.JAVA_INT,0,D3D12_RESOURCE_DIMENSION_BUFFER);it.set(ValueLayout.JAVA_LONG,16,bytes);it.set(ValueLayout.JAVA_INT,24,1);it.set(ValueLayout.JAVA_SHORT,28,1);it.set(ValueLayout.JAVA_SHORT,30,1);it.set(ValueLayout.JAVA_INT,36,1);it.set(ValueLayout.JAVA_INT,44,D3D12_TEXTURE_LAYOUT_ROW_MAJOR);it.set(ValueLayout.JAVA_INT,48,flags)}
    private fun textureDesc(w:Int,h:Int,format:Int,flags:Int)=arena.allocate(56,8).also{it.set(ValueLayout.JAVA_INT,0,D3D12_RESOURCE_DIMENSION_TEXTURE2D);it.set(ValueLayout.JAVA_LONG,16,w.toLong());it.set(ValueLayout.JAVA_INT,24,h);it.set(ValueLayout.JAVA_SHORT,28,1);it.set(ValueLayout.JAVA_SHORT,30,1);it.set(ValueLayout.JAVA_INT,32,format);it.set(ValueLayout.JAVA_INT,36,1);it.set(ValueLayout.JAVA_INT,48,flags)}
    private fun compile(source:String,entry:String,target:String):MemorySegment{val bytes=source.encodeToByteArray();val nativeSource=arena.allocate(bytes.size.toLong(),1).also{it.copyFrom(MemorySegment.ofArray(bytes))};val out=arena.allocate(ValueLayout.ADDRESS);val errors=arena.allocate(ValueLayout.ADDRESS);val hr=D3D12Native.compile(nativeSource,bytes.size.toLong(),arena.allocateFrom(entry),arena.allocateFrom(target),out,errors);val e=errors.get(ValueLayout.ADDRESS,0);if(hr<0){val msg=if(e.address()!=0L)D3D12Native.blobText(e)else"";if(e.address()!=0L)D3D12Native.release(e);error("D3DCompile $entry failed: $msg")};if(e.address()!=0L)D3D12Native.release(e);return out.outObject("shader blob")}
    private fun shader(p:MemorySegment,offset:Long,blob:MemorySegment){p.set(ValueLayout.ADDRESS,offset,D3D12Native.blobPointer(blob));p.set(ValueLayout.JAVA_LONG,offset+8,D3D12Native.blobSize(blob))}
    private fun inputElements():MemorySegment{val a=arena.allocate(64,8);element(a,0,arena.allocateFrom("POSITION"),DXGI_FORMAT_R32G32B32_FLOAT,0);element(a,32,arena.allocateFrom("NORMAL"),DXGI_FORMAT_R32G32B32_FLOAT,12);return a}
    private fun element(a:MemorySegment,o:Long,name:MemorySegment,format:Int,offset:Int){a.set(ValueLayout.ADDRESS,o,name);a.set(ValueLayout.JAVA_INT,o+12,format);a.set(ValueLayout.JAVA_INT,o+20,offset)}

    internal fun executeDxr(dxilLibrary:ByteArray, samplesPerPixel: Int):DxrExecutionEvidence {
        require(dxrFrame == null) { "DXR frame is already installed; the acceleration structures are intentionally built once" }
        waitForGpu();checkHr(D3D12Native.comInt(commandAllocator,D3D12Slots.ALLOCATOR_RESET,D3D12Descriptors.HR_RESET_ALLOCATOR),"DXR allocator reset")
        checkHr(D3D12Native.comInt(commandList,D3D12Slots.LIST_RESET,D3D12Descriptors.HR_RESET_LIST,commandAllocator,MemorySegment.NULL),"DXR list reset")
        val dxrRoot=own(createDxrRootSignature())
        return DxrMinimalPath.execute(object:DxrExecutionContext{
            override val device get()=this@D3D12TeapotRenderer.device
            override val openCommandList get()=commandList
            override val globalRootSignature get()=dxrRoot
            override val outputWidth get()=width
            override val outputHeight get()=height
            override val teapotVertexBuffer get()=DxrBuffer(vertexBuffer,gpuAddress(vertexBuffer),mesh.interleavedVertices.size.toLong()*4)
            override val teapotIndexBuffer get()=DxrBuffer(indexBuffer,gpuAddress(indexBuffer),mesh.indices.size.toLong()*4)
            override val teapotVertexCount get()=mesh.vertexCount
            override val teapotIndexCount get()=mesh.indexCount
            override fun createBuffer(size:Long,heapType:Int,initialState:Int,resourceFlags:Int):DxrBuffer {val r=own(committedResource(heapType,bufferDesc(size,resourceFlags),initialState,MemorySegment.NULL));return DxrBuffer(r,gpuAddress(r),size)}
            override fun upload(buffer:DxrBuffer,bytes:ByteArray){require(bytes.size.toLong()<=buffer.size);val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(buffer.resource,D3D12Slots.RESOURCE_MAP,D3D12Descriptors.HR_MAP,0,MemorySegment.NULL,out),"DXR upload Map");out.get(ValueLayout.ADDRESS,0).reinterpret(bytes.size.toLong()).copyFrom(MemorySegment.ofArray(bytes));D3D12Native.comVoid(buffer.resource,D3D12Slots.RESOURCE_UNMAP,D3D12Descriptors.VOID_UNMAP,0,MemorySegment.NULL)}
            override fun readUInt(buffer:DxrBuffer):Int{val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(buffer.resource,D3D12Slots.RESOURCE_MAP,D3D12Descriptors.HR_MAP,0,MemorySegment.NULL,out),"DXR readback Map");val value=out.get(ValueLayout.ADDRESS,0).reinterpret(4).get(ValueLayout.JAVA_INT,0);D3D12Native.comVoid(buffer.resource,D3D12Slots.RESOURCE_UNMAP,D3D12Descriptors.VOID_UNMAP,0,MemorySegment.NULL);return value}
            override fun submitAndWait()=submitOpenCommandListAndWait()
            override fun installRaytracedFrame(frame:DxrFrameOutput){require(dxrFrame==null);dxrFrame=frame}
        },dxilLibrary,samplesPerPixel)
    }
    private fun createDxrRootSignature():MemorySegment {val params=arena.allocate(160,8);rootDescriptor(params,0,D3D12_ROOT_PARAMETER_TYPE_UAV,0);rootDescriptor(params,32,D3D12_ROOT_PARAMETER_TYPE_SRV,0);rootDescriptor(params,64,D3D12_ROOT_PARAMETER_TYPE_SRV,1);rootDescriptor(params,96,D3D12_ROOT_PARAMETER_TYPE_SRV,2);params.set(ValueLayout.JAVA_INT,128,D3D12_ROOT_PARAMETER_TYPE_32BIT_CONSTANTS);params.set(ValueLayout.JAVA_INT,136,0);params.set(ValueLayout.JAVA_INT,140,0);params.set(ValueLayout.JAVA_INT,144,16);params.set(ValueLayout.JAVA_INT,152,D3D12_SHADER_VISIBILITY_ALL);val desc=arena.allocate(40,8);desc.set(ValueLayout.JAVA_INT,0,5);desc.set(ValueLayout.ADDRESS,8,params);return serializeRoot(desc)}
    private fun rootDescriptor(params:MemorySegment,offset:Long,type:Int,shaderRegister:Int){params.set(ValueLayout.JAVA_INT,offset,type);params.set(ValueLayout.JAVA_INT,offset+8,shaderRegister);params.set(ValueLayout.JAVA_INT,offset+12,0);params.set(ValueLayout.JAVA_INT,offset+24,D3D12_SHADER_VISIBILITY_ALL)}
    private fun serializeRoot(desc:MemorySegment):MemorySegment{val blobOut=arena.allocate(ValueLayout.ADDRESS);val errorsOut=arena.allocate(ValueLayout.ADDRESS);val hr=D3D12Native.serializeRootSignature(desc,blobOut,errorsOut);val errors=errorsOut.get(ValueLayout.ADDRESS,0);if(hr<0){val msg=if(errors.address()!=0L)D3D12Native.blobText(errors)else"";if(errors.address()!=0L)D3D12Native.release(errors);error("DXR root signature serialization failed: $msg")};if(errors.address()!=0L)D3D12Native.release(errors);val blob=blobOut.outObject("DXR root signature blob");return try{val out=arena.allocate(ValueLayout.ADDRESS);checkHr(D3D12Native.comInt(device,D3D12Slots.DEVICE_CREATE_ROOT_SIGNATURE,D3D12Descriptors.HR_ROOT_SIGNATURE,0,D3D12Native.blobPointer(blob),D3D12Native.blobSize(blob),arena.guid(IID_ROOT_SIGNATURE),out),"Create DXR root signature");out.outObject("DXR root signature")}finally{D3D12Native.release(blob)}}

    private fun textureCopyLocation(resource:MemorySegment)=arena.allocate(48,8).also{it.set(ValueLayout.ADDRESS,0,resource);it.set(ValueLayout.JAVA_INT,8,D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX);it.set(ValueLayout.JAVA_INT,16,0)}
    private fun placedFootprintCopyLocation(frame:DxrFrameOutput)=arena.allocate(48,8).also{it.set(ValueLayout.ADDRESS,0,frame.buffer.resource);it.set(ValueLayout.JAVA_INT,8,D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT);it.set(ValueLayout.JAVA_LONG,16,frame.imageOffset);it.set(ValueLayout.JAVA_INT,24,DXGI_FORMAT_R8G8B8A8_UNORM);it.set(ValueLayout.JAVA_INT,28,frame.width);it.set(ValueLayout.JAVA_INT,32,frame.height);it.set(ValueLayout.JAVA_INT,36,1);it.set(ValueLayout.JAVA_INT,40,frame.rowPitch)}
}

private val IID_DEVICE="189819f1-1db6-4b57-be54-1821339b85f7".guidValue();private val IID_FACTORY6="c1b6694f-ff09-44a9-b03c-77900a0a1d17".guidValue();private val IID_ADAPTER1="29038f61-3839-4626-91fd-086879011a05".guidValue();private val IID_QUEUE="0ec870a6-5d7e-4c22-8cfc-5baae07616ed".guidValue();private val IID_ALLOCATOR="6102dee4-af59-4b09-b999-b44d73f09b24".guidValue();private val IID_DESCRIPTOR_HEAP="8efb471d-616c-4f49-90f7-127bb763fa51".guidValue();private val IID_SWAP_CHAIN3="94d99bdb-f1f8-4ab0-b236-7da0170edab1".guidValue();private val IID_RESOURCE="696442be-a72e-4059-bc79-5b5c98040fad".guidValue();private val IID_ROOT_SIGNATURE="c54a6b66-72df-4ee8-8be5-a946a1429214".guidValue();private val IID_PIPELINE_STATE="765a30f3-f624-4c6f-a828-ace948622445".guidValue();private val IID_GRAPHICS_LIST="5b160d0f-ac1b-4185-8ba8-b3ae42a5a455".guidValue();private val IID_FENCE="0a753dcf-c4d8-4b91-adf6-be5a60d95a76".guidValue()
private const val ROOT_DWORDS=44;private const val D3D12_COMMAND_LIST_TYPE_DIRECT=0;private const val D3D12_DESCRIPTOR_HEAP_TYPE_RTV=2;private const val D3D12_DESCRIPTOR_HEAP_TYPE_DSV=3;private const val D3D12_HEAP_TYPE_DEFAULT=1;private const val D3D12_HEAP_TYPE_UPLOAD=2;private const val D3D12_RESOURCE_DIMENSION_BUFFER=1;private const val D3D12_RESOURCE_DIMENSION_TEXTURE2D=3;private const val D3D12_TEXTURE_LAYOUT_ROW_MAJOR=1;private const val D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL=2;private const val D3D12_RESOURCE_STATE_RENDER_TARGET=4;private const val D3D12_RESOURCE_STATE_DEPTH_WRITE=16;private const val D3D12_RESOURCE_STATE_GENERIC_READ=2755;private const val D3D12_RESOURCE_STATE_PRESENT=0;private const val D3D12_RESOURCE_STATE_COPY_DEST=1024;private const val D3D12_CLEAR_FLAG_DEPTH=1;private const val D3D12_ROOT_PARAMETER_TYPE_32BIT_CONSTANTS=1;private const val D3D12_ROOT_PARAMETER_TYPE_SRV=3;private const val D3D12_ROOT_PARAMETER_TYPE_UAV=4;private const val D3D12_SHADER_VISIBILITY_ALL=0;private const val D3D12_ROOT_SIGNATURE_FLAG_ALLOW_INPUT_ASSEMBLER_INPUT_LAYOUT=1;private const val D3D12_DEPTH_WRITE_MASK_ALL=1;private const val D3D12_COMPARISON_FUNC_LESS=2;private const val D3D12_COMPARISON_FUNC_ALWAYS=8;private const val D3D12_STENCIL_OP_KEEP=1;private const val D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE=3;private const val D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST=4;private const val D3D12_BLEND_ZERO=1;private const val D3D12_BLEND_ONE=2;private const val D3D12_BLEND_OP_ADD=1;private const val D3D12_LOGIC_OP_NOOP=5;private const val D3D12_COLOR_WRITE_ENABLE_ALL=15;private const val D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX=0;private const val D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT=1;private const val DXGI_FORMAT_R32G32B32_FLOAT=6;private const val DXGI_FORMAT_R8G8B8A8_UNORM=28;private const val DXGI_FORMAT_R32_UINT=42;private const val DXGI_FORMAT_D24_UNORM_S8_UINT=45;private const val DXGI_USAGE_RENDER_TARGET_OUTPUT=0x20;private const val DXGI_SCALING_STRETCH=0;private const val DXGI_SWAP_EFFECT_FLIP_DISCARD=4;private const val DXGI_ALPHA_MODE_UNSPECIFIED=0;private const val DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE=2

/** Gold is deliberately metallic and low-roughness so the raster fallback keeps a clear reflective read. */
internal data class TeapotGoldMaterial(val baseRed: Float, val baseGreen: Float, val baseBlue: Float, val roughness: Float)
internal val GOLD_MATERIAL = TeapotGoldMaterial(1.0f, 0.62f, 0.08f, 0.10f)

private const val VS="""cbuffer C:register(b0){column_major float4x4 mvp;column_major float4x4 model;float4 light;float4 camera;float4 color;}struct I{float3 p:POSITION;float3 n:NORMAL;};struct O{float4 p:SV_POSITION;float3 w:TEXCOORD0;float3 n:TEXCOORD1;};O VSMain(I i){O o;float4 w=mul(model,float4(i.p,1));o.p=mul(mvp,float4(i.p,1));o.w=w.xyz;o.n=normalize(mul((float3x3)model,i.n));return o;}"""
private const val PS="""cbuffer C:register(b0){column_major float4x4 mvp;column_major float4x4 model;float4 light;float4 camera;float4 color;}struct O{float4 p:SV_POSITION;float3 w:TEXCOORD0;float3 n:TEXCOORD1;};float3 fresnelSchlick(float c,float3 f0){return f0+(1-f0)*pow(1-saturate(c),5);}float4 PSMain(O i):SV_TARGET{float3 n=normalize(i.n),l=normalize(light.xyz-i.w),v=normalize(camera.xyz-i.w);float nl=saturate(dot(n,l));float3 r=reflect(-v,n);float sky=saturate(r.y*.5+.5);float3 environment=lerp(float3(.012,.018,.045),float3(.35,.48,.72),sky);float gloss=lerp(224,48,saturate(color.a));float sparkle=pow(saturate(dot(reflect(-l,n),v)),gloss);float3 f=fresnelSchlick(saturate(dot(n,v)),color.rgb);float3 metallicBase=color.rgb*(.035+.11*nl);float3 reflective=f*(environment*.95+sparkle*3.2);float3 result=metallicBase+reflective;return float4(result/(1+result),1);}"""
