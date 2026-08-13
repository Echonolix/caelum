package net.echonolix.caelum.directx

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

private val ADDRESS: MemoryLayout = ValueLayout.ADDRESS
private val UINT32: MemoryLayout = ValueLayout.JAVA_INT
private val SIZE_T_X64: MemoryLayout = ValueLayout.JAVA_LONG

private fun returnsAddress(vararg parameters: MemoryLayout): FunctionDescriptor =
    FunctionDescriptor.of(ADDRESS, *parameters)

private fun returnsHResult(vararg parameters: MemoryLayout): FunctionDescriptor =
    FunctionDescriptor.of(UINT32, *parameters)

/** Process entry points exported by `d3d9.dll`. */
public object D3D9EntryPoints {
    public val direct3DCreate9: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D9,
        "Direct3DCreate9",
        returnsAddress(UINT32),
    )
    public val direct3DCreate9Ex: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D9,
        "Direct3DCreate9Ex",
        returnsHResult(UINT32, ADDRESS),
    )

    public val all: List<DirectXEntryPoint> = listOf(direct3DCreate9, direct3DCreate9Ex)
}

/** Device and swap-chain creation entry points exported by `d3d10.dll`. */
public object D3D10EntryPoints {
    public val createDevice: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D10,
        "D3D10CreateDevice",
        returnsHResult(ADDRESS, UINT32, ADDRESS, UINT32, UINT32, ADDRESS),
    )
    public val createDeviceAndSwapChain: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D10,
        "D3D10CreateDeviceAndSwapChain",
        returnsHResult(ADDRESS, UINT32, ADDRESS, UINT32, UINT32, ADDRESS, ADDRESS, ADDRESS),
    )
    public val createDevice1: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D10_1,
        "D3D10CreateDevice1",
        returnsHResult(ADDRESS, UINT32, ADDRESS, UINT32, UINT32, UINT32, ADDRESS),
    )
    public val createDeviceAndSwapChain1: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D10_1,
        "D3D10CreateDeviceAndSwapChain1",
        returnsHResult(ADDRESS, UINT32, ADDRESS, UINT32, UINT32, UINT32, ADDRESS, ADDRESS, ADDRESS),
    )
    public val createBlob: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D10,
        "D3D10CreateBlob",
        returnsHResult(SIZE_T_X64, ADDRESS),
    )

    public val all: List<DirectXEntryPoint> = listOf(
        createDevice,
        createDeviceAndSwapChain,
        createDevice1,
        createDeviceAndSwapChain1,
        createBlob,
    )
}

/** Device and swap-chain creation entry points exported by `d3d11.dll`. */
public object D3D11EntryPoints {
    public val createDevice: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D11,
        "D3D11CreateDevice",
        returnsHResult(
            ADDRESS,
            UINT32,
            ADDRESS,
            UINT32,
            ADDRESS,
            UINT32,
            UINT32,
            ADDRESS,
            ADDRESS,
            ADDRESS,
        ),
    )
    public val createDeviceAndSwapChain: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D11,
        "D3D11CreateDeviceAndSwapChain",
        returnsHResult(
            ADDRESS,
            UINT32,
            ADDRESS,
            UINT32,
            ADDRESS,
            UINT32,
            UINT32,
            ADDRESS,
            ADDRESS,
            ADDRESS,
            ADDRESS,
            ADDRESS,
        ),
    )

    public val all: List<DirectXEntryPoint> = listOf(createDevice, createDeviceAndSwapChain)
}

/** Public runtime entry points exported by `d3d12.dll`. */
public object D3D12EntryPoints {
    public val createDevice: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12CreateDevice",
        returnsHResult(ADDRESS, UINT32, ADDRESS, ADDRESS),
    )
    public val getDebugInterface: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12GetDebugInterface",
        returnsHResult(ADDRESS, ADDRESS),
    )
    public val getInterface: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12GetInterface",
        returnsHResult(ADDRESS, ADDRESS, ADDRESS),
    )
    public val enableExperimentalFeatures: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12EnableExperimentalFeatures",
        returnsHResult(UINT32, ADDRESS, ADDRESS, ADDRESS),
    )
    public val serializeRootSignature: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12SerializeRootSignature",
        returnsHResult(ADDRESS, UINT32, ADDRESS, ADDRESS),
    )
    public val createRootSignatureDeserializer: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12CreateRootSignatureDeserializer",
        returnsHResult(ADDRESS, SIZE_T_X64, ADDRESS, ADDRESS),
    )
    public val serializeVersionedRootSignature: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12SerializeVersionedRootSignature",
        returnsHResult(ADDRESS, ADDRESS, ADDRESS),
    )
    public val createVersionedRootSignatureDeserializer: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12,
        "D3D12CreateVersionedRootSignatureDeserializer",
        returnsHResult(ADDRESS, SIZE_T_X64, ADDRESS, ADDRESS),
    )
    public val createVersionedRootSignatureDeserializerFromSubobjectInLibrary: DirectXEntryPoint =
        DirectXEntryPoint(
            DirectXLibrary.D3D12,
            "D3D12CreateVersionedRootSignatureDeserializerFromSubobjectInLibrary",
            returnsHResult(ADDRESS, SIZE_T_X64, ADDRESS, ADDRESS, ADDRESS),
        )

    public val all: List<DirectXEntryPoint> = listOf(
        createDevice,
        getDebugInterface,
        getInterface,
        enableExperimentalFeatures,
        serializeRootSignature,
        createRootSignatureDeserializer,
        serializeVersionedRootSignature,
        createVersionedRootSignatureDeserializer,
        createVersionedRootSignatureDeserializerFromSubobjectInLibrary,
    )
}

/** Optional process entry points shipped by the D3D12 Agility SDK compiler component. */
public object D3D12CompilerEntryPoints {
    public val createFactory: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12_STATE_OBJECT_COMPILER,
        "D3D12CompilerCreateFactory",
        returnsHResult(ADDRESS, ADDRESS, ADDRESS),
    )
    public val serializeVersionedRootSignature: DirectXEntryPoint = DirectXEntryPoint(
        DirectXLibrary.D3D12_STATE_OBJECT_COMPILER,
        "D3D12CompilerSerializeVersionedRootSignature",
        returnsHResult(ADDRESS, ADDRESS, ADDRESS),
    )

    public val all: List<DirectXEntryPoint> = listOf(createFactory, serializeVersionedRootSignature)
}

/** Public entry-point catalog for the redistributable HLSL compiler 47. */
public object D3DCompiler47EntryPoints {
    private fun entry(name: String, vararg parameters: MemoryLayout): DirectXEntryPoint =
        DirectXEntryPoint(DirectXLibrary.D3D_COMPILER_47, name, returnsHResult(*parameters))

    public val readFileToBlob: DirectXEntryPoint = entry("D3DReadFileToBlob", ADDRESS, ADDRESS)
    public val writeBlobToFile: DirectXEntryPoint = entry("D3DWriteBlobToFile", ADDRESS, ADDRESS, UINT32)
    public val compile: DirectXEntryPoint = entry(
        "D3DCompile",
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        UINT32,
        UINT32,
        ADDRESS,
        ADDRESS,
    )
    public val compile2: DirectXEntryPoint = entry(
        "D3DCompile2",
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        UINT32,
        UINT32,
        UINT32,
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
        ADDRESS,
    )
    public val compileFromFile: DirectXEntryPoint = entry(
        "D3DCompileFromFile",
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        UINT32,
        UINT32,
        ADDRESS,
        ADDRESS,
    )
    public val preprocess: DirectXEntryPoint = entry(
        "D3DPreprocess",
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
    )
    public val getDebugInfo: DirectXEntryPoint = entry("D3DGetDebugInfo", ADDRESS, SIZE_T_X64, ADDRESS)
    public val reflect: DirectXEntryPoint = entry("D3DReflect", ADDRESS, SIZE_T_X64, ADDRESS, ADDRESS)
    public val reflectLibrary: DirectXEntryPoint = entry("D3DReflectLibrary", ADDRESS, SIZE_T_X64, ADDRESS, ADDRESS)
    public val disassemble: DirectXEntryPoint = entry(
        "D3DDisassemble",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        ADDRESS,
        ADDRESS,
    )
    public val disassembleRegion: DirectXEntryPoint = entry(
        "D3DDisassembleRegion",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        ADDRESS,
        SIZE_T_X64,
        SIZE_T_X64,
        ADDRESS,
        ADDRESS,
    )
    public val createLinker: DirectXEntryPoint = entry("D3DCreateLinker", ADDRESS)
    public val loadModule: DirectXEntryPoint = entry("D3DLoadModule", ADDRESS, SIZE_T_X64, ADDRESS)
    public val createFunctionLinkingGraph: DirectXEntryPoint = entry(
        "D3DCreateFunctionLinkingGraph",
        UINT32,
        ADDRESS,
    )
    public val getTraceInstructionOffsets: DirectXEntryPoint = entry(
        "D3DGetTraceInstructionOffsets",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        SIZE_T_X64,
        SIZE_T_X64,
        ADDRESS,
        ADDRESS,
    )
    public val getInputSignatureBlob: DirectXEntryPoint = entry(
        "D3DGetInputSignatureBlob",
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
    )
    public val getOutputSignatureBlob: DirectXEntryPoint = entry(
        "D3DGetOutputSignatureBlob",
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
    )
    public val getInputAndOutputSignatureBlob: DirectXEntryPoint = entry(
        "D3DGetInputAndOutputSignatureBlob",
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
    )
    public val stripShader: DirectXEntryPoint = entry(
        "D3DStripShader",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        ADDRESS,
    )
    public val getBlobPart: DirectXEntryPoint = entry(
        "D3DGetBlobPart",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        UINT32,
        ADDRESS,
    )
    public val setBlobPart: DirectXEntryPoint = entry(
        "D3DSetBlobPart",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        UINT32,
        ADDRESS,
        SIZE_T_X64,
        ADDRESS,
    )
    public val createBlob: DirectXEntryPoint = entry("D3DCreateBlob", SIZE_T_X64, ADDRESS)
    public val compressShaders: DirectXEntryPoint = entry("D3DCompressShaders", UINT32, ADDRESS, UINT32, ADDRESS)
    public val decompressShaders: DirectXEntryPoint = entry(
        "D3DDecompressShaders",
        ADDRESS,
        SIZE_T_X64,
        UINT32,
        UINT32,
        ADDRESS,
        UINT32,
        ADDRESS,
        ADDRESS,
    )
    public val disassemble10Effect: DirectXEntryPoint = entry("D3DDisassemble10Effect", ADDRESS, UINT32, ADDRESS)

    public val all: List<DirectXEntryPoint> = listOf(
        readFileToBlob,
        writeBlobToFile,
        compile,
        compile2,
        compileFromFile,
        preprocess,
        getDebugInfo,
        reflect,
        reflectLibrary,
        disassemble,
        disassembleRegion,
        createLinker,
        loadModule,
        createFunctionLinkingGraph,
        getTraceInstructionOffsets,
        getInputSignatureBlob,
        getOutputSignatureBlob,
        getInputAndOutputSignatureBlob,
        stripShader,
        getBlobPart,
        setBlobPart,
        createBlob,
        compressShaders,
        decompressShaders,
        disassemble10Effect,
    )
}

/**
 * Hand-written convenience entry points used by the runtime facade.
 *
 * The complete SDK symbol surface (including D3D9 performance helpers,
 * D3D10 shader/effect helpers, and D3D11 trace helpers) lives in the generated
 * raw API catalog alongside all COM methods. This list intentionally contains
 * stable creation, root-signature, and shader-compiler entry points only.
 */
public object DirectXEntryPoints {
    public val all: List<DirectXEntryPoint> =
        D3D9EntryPoints.all +
            D3D10EntryPoints.all +
            D3D11EntryPoints.all +
            D3D12EntryPoints.all +
            D3D12CompilerEntryPoints.all +
            D3DCompiler47EntryPoints.all

    public fun forLibrary(library: DirectXLibrary): List<DirectXEntryPoint> =
        all.filter { it.library == library }
}
