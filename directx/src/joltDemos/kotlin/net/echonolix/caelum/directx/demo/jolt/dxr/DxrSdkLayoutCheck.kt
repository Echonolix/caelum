package net.echonolix.caelum.directx.demo.jolt.dxr

import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import net.echonolix.caelum.dxgi.api.NativeRecordDeclaration

/** Checks the manually encoded x64 layouts against the pinned SDK header catalog. */
internal object DxrSdkLayoutCheck {
    // The resolver is injectable so a test can corrupt a copied declaration and prove rejection.
    fun verify(resolve: ((String) -> NativeRecordDeclaration)? = null) {
        val lookup: (String) -> NativeRecordDeclaration = resolve ?: run {
            val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12)
            val catalogs = listOf(profile.primary) + profile.dependencies
            val resolver: (String) -> NativeRecordDeclaration = { name ->
                catalogs.firstNotNullOfOrNull { it.record(name) } ?: error("SDK catalog has no record $name")
            }
            resolver
        }
        fun record(name: String, size: Long, vararg fields: Pair<String, Int>) {
            val declaration = lookup(name)
            check(declaration.size == size) { "$name size: expected $size, catalog has ${declaration.size}" }
            fields.forEach { (field, byteOffset) -> checkField(declaration, field, byteOffset * 8L, null) }
        }
        record("D3D12_RAYTRACING_INSTANCE_DESC", 64, "Transform" to 0, "AccelerationStructure" to 56)
        val instance = lookup("D3D12_RAYTRACING_INSTANCE_DESC")
        checkField(instance, "InstanceID", 384, 24)
        checkField(instance, "InstanceMask", 408, 8)
        checkField(instance, "InstanceContributionToHitGroupIndex", 416, 24)
        checkField(instance, "Flags", 440, 8)
        record("D3D12_RAYTRACING_GEOMETRY_DESC", 56, "Type" to 0, "Flags" to 4, "Triangles" to 8)
        record("D3D12_RAYTRACING_GEOMETRY_TRIANGLES_DESC", 48, "Transform3x4" to 0,
            "IndexFormat" to 8, "VertexFormat" to 12, "IndexCount" to 16, "VertexCount" to 20,
            "IndexBuffer" to 24, "VertexBuffer" to 32)
        record("D3D12_GPU_VIRTUAL_ADDRESS_AND_STRIDE", 16, "StartAddress" to 0, "StrideInBytes" to 8)
        record("D3D12_BUILD_RAYTRACING_ACCELERATION_STRUCTURE_INPUTS", 24, "Type" to 0,
            "Flags" to 4, "NumDescs" to 8, "DescsLayout" to 12, "InstanceDescs" to 16, "pGeometryDescs" to 16)
        record("D3D12_BUILD_RAYTRACING_ACCELERATION_STRUCTURE_DESC", 48,
            "DestAccelerationStructureData" to 0, "Inputs" to 8, "SourceAccelerationStructureData" to 32,
            "ScratchAccelerationStructureData" to 40)
        record("D3D12_RAYTRACING_ACCELERATION_STRUCTURE_PREBUILD_INFO", 24,
            "ResultDataMaxSizeInBytes" to 0, "ScratchDataSizeInBytes" to 8, "UpdateScratchDataSizeInBytes" to 16)
        record("D3D12_DISPATCH_RAYS_DESC", 104, "RayGenerationShaderRecord" to 0,
            "MissShaderTable" to 16, "HitGroupTable" to 40, "CallableShaderTable" to 64,
            "Width" to 88, "Height" to 92, "Depth" to 96)
        record("D3D12_GPU_VIRTUAL_ADDRESS_RANGE", 16, "StartAddress" to 0, "SizeInBytes" to 8)
        record("D3D12_GPU_VIRTUAL_ADDRESS_RANGE_AND_STRIDE", 24, "StartAddress" to 0, "SizeInBytes" to 8, "StrideInBytes" to 16)
        record("D3D12_STATE_OBJECT_DESC", 16, "Type" to 0, "NumSubobjects" to 4, "pSubobjects" to 8)
        record("D3D12_STATE_SUBOBJECT", 16, "Type" to 0, "pDesc" to 8)
        record("D3D12_DXIL_LIBRARY_DESC", 32, "DXILLibrary" to 0, "NumExports" to 16, "pExports" to 24)
        record("D3D12_SHADER_BYTECODE", 16, "pShaderBytecode" to 0, "BytecodeLength" to 8)
        record("D3D12_HIT_GROUP_DESC", 40, "HitGroupExport" to 0, "Type" to 8,
            "AnyHitShaderImport" to 16, "ClosestHitShaderImport" to 24, "IntersectionShaderImport" to 32)
        record("D3D12_RAYTRACING_SHADER_CONFIG", 8, "MaxPayloadSizeInBytes" to 0, "MaxAttributeSizeInBytes" to 4)
        record("D3D12_GLOBAL_ROOT_SIGNATURE", 8, "pGlobalRootSignature" to 0)
        record("D3D12_RAYTRACING_PIPELINE_CONFIG", 4, "MaxTraceRecursionDepth" to 0)
        record("D3D12_ROOT_SIGNATURE_DESC", 40, "NumParameters" to 0, "pParameters" to 8,
            "NumStaticSamplers" to 16, "pStaticSamplers" to 24, "Flags" to 32)
        record("D3D12_ROOT_PARAMETER", 32, "ParameterType" to 0, "Constants" to 8,
            "Descriptor" to 8, "ShaderVisibility" to 24)
        record("D3D12_ROOT_DESCRIPTOR", 8, "ShaderRegister" to 0, "RegisterSpace" to 4)
        record("D3D12_ROOT_CONSTANTS", 12, "ShaderRegister" to 0, "RegisterSpace" to 4, "Num32BitValues" to 8)
        record("D3D12_TEXTURE_COPY_LOCATION", 48, "pResource" to 0, "Type" to 8, "PlacedFootprint" to 16, "SubresourceIndex" to 16)
        record("D3D12_PLACED_SUBRESOURCE_FOOTPRINT", 32, "Offset" to 0, "Footprint" to 8)
        record("D3D12_SUBRESOURCE_FOOTPRINT", 20, "Format" to 0, "Width" to 4, "Height" to 8, "Depth" to 12, "RowPitch" to 16)
        record("D3D12_FEATURE_DATA_D3D12_OPTIONS5", 12, "RaytracingTier" to 8)
        record("D3D12_COMMAND_QUEUE_DESC", 16, "Type" to 0, "Priority" to 4, "Flags" to 8, "NodeMask" to 12)
        record("D3D12_HEAP_PROPERTIES", 20, "Type" to 0, "CPUPageProperty" to 4, "MemoryPoolPreference" to 8,
            "CreationNodeMask" to 12, "VisibleNodeMask" to 16)
        record("D3D12_RESOURCE_DESC", 56, "Dimension" to 0, "Alignment" to 8, "Width" to 16, "Height" to 24,
            "DepthOrArraySize" to 28, "MipLevels" to 30, "Format" to 32, "SampleDesc" to 36, "Layout" to 44, "Flags" to 48)
        record("D3D12_RESOURCE_BARRIER", 32, "Type" to 0, "Flags" to 4, "Transition" to 8, "UAV" to 8)
        record("D3D12_RESOURCE_TRANSITION_BARRIER", 24, "pResource" to 0, "Subresource" to 8, "StateBefore" to 12, "StateAfter" to 16)
        record("D3D12_RESOURCE_UAV_BARRIER", 8, "pResource" to 0)
        record("DXGI_SWAP_CHAIN_DESC1", 48, "Width" to 0, "Height" to 4, "Format" to 8, "Stereo" to 12,
            "SampleDesc" to 16, "BufferUsage" to 24, "BufferCount" to 28, "Scaling" to 32,
            "SwapEffect" to 36, "AlphaMode" to 40, "Flags" to 44)
        record("DXGI_SAMPLE_DESC", 8, "Count" to 0, "Quality" to 4)
    }

    private fun checkField(record: NativeRecordDeclaration, name: String, expectedBits: Long, expectedWidth: Long?) {
        fun find(current: NativeRecordDeclaration, base: Long): List<Pair<Long, Long?>> = current.fields.flatMap { field ->
            val offset = field.offsetBits ?: error("${record.name}.${field.name ?: "<anonymous>"} has no SDK offset")
            if (field.name == name) listOf(base + offset to field.bitWidth)
            else field.anonymousRecord?.let { find(it, base + offset) } ?: emptyList()
        }
        val matches = find(record, 0)
        check(matches.size == 1) { "${record.name}.$name: expected one SDK field, found ${matches.size}" }
        val (actualBits, actualWidth) = matches.single()
        check(actualBits == expectedBits && actualWidth == expectedWidth) {
            "${record.name}.$name: expected offset $expectedBits bits, width $expectedWidth; catalog has offset $actualBits bits, width $actualWidth"
        }
    }
}
