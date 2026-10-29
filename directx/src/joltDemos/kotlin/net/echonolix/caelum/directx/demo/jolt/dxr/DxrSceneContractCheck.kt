package net.echonolix.caelum.directx.demo.jolt.dxr

import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fixed SDK/HLSL encoding fixtures; this check does not load a graphics runtime or native Jolt. */
public object DxrSceneContractCheck {
    @JvmStatic
    public fun main(args: Array<String>) {
        verifySdkLayouts()
        val rotated = floatArrayOf(0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 3f, 4f, 5f, 1f)
        val translated = identity().also { it[12] = -2f; it[13] = 1f; it[14] = 7f }
        // Object IDs deliberately contain gaps, as they do when a physics sensor is invisible.
        val instances = listOf(
            DxrSceneInstance(0, rotated, Vec3(.25f, .5f, .75f), .125f, .625f, 41),
            DxrSceneInstance(1, translated, Vec3(1f, .25f, 0f), .875f, .25f, 99),
        )
        val addresses = longArrayOf(0x0102030405060700L, 0x0011223344556600L)
        val descriptions = DxrSceneData.instanceDescriptions(instances, addresses)
        verifyDescriptions(descriptions)
        val badTransform = descriptions.clone().also { it[12] = 1 } // corrupt translation X
        rejectedLayout("incorrect SDK translation") { verifyDescriptions(badTransform) }
        val badMask = descriptions.clone().also { it[51] = 0 }
        rejectedLayout("missing instance mask") { verifyDescriptions(badMask) }
        val badAddress = descriptions.clone().also { it[56] = 1 }
        rejectedLayout("incorrect acceleration structure address") { verifyDescriptions(badAddress) }

        val metadata = DxrSceneData.metadata(instances, intArrayOf(96, 192), intArrayOf(12, 72))
        verifyMetadata(metadata)
        val badAtlas = metadata.clone().also { it[0] = 16 }
        rejectedLayout("incorrect atlas byte offset") { verifyMetadata(badAtlas) }

        // A GPU counter at byte 20 belongs to InstanceID 1 / mesh 1, despite objectId 99.
        val encoded = ByteBuffer.wrap(descriptions).order(ByteOrder.LITTLE_ENDIAN)
        val header = ByteBuffer.allocate(DxrSceneData.IMAGE_OFFSET).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(16, 17); header.putInt(20, 29)
        val firstId = encoded.getInt(48) and 0xffffff
        val secondId = encoded.getInt(112) and 0xffffff
        check(firstId == 0 && secondId == 1)
        check(instances[firstId].meshIndex == 0 && header.getInt(16 + firstId * 4) == 17)
        check(instances[secondId].meshIndex == 1 && header.getInt(16 + secondId * 4) == 29)
        check(header.getInt(16 + instances[1].objectId * 4) == 0) { "Object ID must not select a GPU instance hit counter" }

        verifyRejectedInputs(instances[0], addresses)
        verifyHeader()
        println("DXR_SCENE_ENCODING_CHECKS=SDK_TRANSFORM,INSTANCE_ID_MASK,BLAS_ADDRESS,METADATA_BYTE_OFFSETS,MATERIALS,OBJECT_ID_GAPS")
        println("DXR_SCENE_REJECTION_CHECKS=MESH_VERTICES,TRIANGLE_INDICES,MESH_INDEX,BLAS_ALIGNMENT,NONFINITE,TRANSFORM_LENGTH,PROJECTIVE,SINGULAR,MATERIAL,ATLAS_OFFSETS,INSTANCE_LIMIT")
        println("DXR_SCENE_MUTATION_CHECKS=TRANSLATION,MASK,BLAS_ADDRESS,ATLAS_OFFSET")
        println("CAELUM_DXR_SCENE_CONTRACTS_OK")
    }

    private fun verifySdkLayouts() {
        DxrSdkLayoutCheck.verify()
        val catalogs=DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12).catalogs
        val instance=catalogs.firstNotNullOf { it.records["D3D12_RAYTRACING_INSTANCE_DESC"] }
        val badOffset=instance.copy(fields=instance.fields.map { if(it.name=="Transform") it.copy(offsetBits=8) else it })
        val badBitfield=instance.copy(fields=instance.fields.map { if(it.name=="InstanceMask") it.copy(bitWidth=7) else it })
        for(bad in listOf(badOffset,badBitfield)) {
            rejectedLayout("corrupted SDK ${bad.name}") {
                DxrSdkLayoutCheck.verify { name -> if(name==bad.name) bad else catalogs.firstNotNullOf { it.records[name] } }
            }
        }
        println("DXR_SDK_LAYOUTS=34_RECORDS_125_FIELDS_4_BITFIELDS; CORRUPT_OFFSET_AND_WIDTH_REJECTED")
    }

    private fun verifyDescriptions(bytes: ByteArray) {
        check(bytes.size == 128)
        // SDK row-major 3x4 for +90 degrees about Y followed by translation (3,4,5).
        // Hex is an independent fixed native little-endian fixture, including InstanceID/mask/BLAS.
        val expected = hex("""
            00000000 00000000 0000803f 00004040
            00000000 0000803f 00000000 00008040
            000080bf 00000000 00000000 0000a040
            000000ff 00000000 0007060504030201
        """)
        check(bytes.copyOfRange(0, 64).contentEquals(expected)) { "D3D12_RAYTRACING_INSTANCE_DESC differs from fixed SDK bytes" }
        val sdk = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(sdk.getFloat(64) == 1f && sdk.getFloat(76) == -2f)
        check(sdk.getFloat(84) == 1f && sdk.getFloat(92) == 1f)
        check(sdk.getFloat(104) == 1f && sdk.getFloat(108) == 7f)
        check(sdk.getInt(112) == 0xff000001.toInt())
        check(sdk.getInt(116) == 0)
        check(sdk.getLong(120) == 0x0011223344556600L)
    }

    private fun verifyMetadata(bytes: ByteArray) {
        check(bytes.size == 64)
        val shader = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(shader.getInt(0) == 12 && shader.getInt(4) == 96)
        check(shader.getFloat(8) == .25f && shader.getFloat(12) == .5f && shader.getFloat(16) == .75f)
        check(shader.getFloat(20) == .125f && shader.getFloat(24) == .625f && shader.getInt(28) == 41)
        check(shader.getInt(32) == 72 && shader.getInt(36) == 192)
        check(shader.getFloat(40) == 1f && shader.getFloat(44) == .25f && shader.getFloat(48) == 0f)
        check(shader.getFloat(52) == .875f && shader.getFloat(56) == .25f && shader.getInt(60) == 99)
    }

    private fun verifyRejectedInputs(instance: DxrSceneInstance, addresses: LongArray) {
        val triangle = floatArrayOf(0f,0f,0f, 0f,1f,0f, 1f,0f,0f, 0f,1f,0f, 0f,0f,1f, 0f,1f,0f)
        DxrSceneMesh("valid triangle", triangle, intArrayOf(0, 1, 2))
        rejected("empty vertices") { DxrSceneMesh("empty", floatArrayOf(), intArrayOf(0, 1, 2)) }
        rejected("incomplete vertex") { DxrSceneMesh("vertex", triangle.copyOf(17), intArrayOf(0, 1, 2)) }
        rejected("nonfinite vertex") { DxrSceneMesh("NaN", triangle.clone().also { it[3] = Float.NaN }, intArrayOf(0, 1, 2)) }
        rejected("negative index") { DxrSceneMesh("negative", triangle, intArrayOf(-1, 1, 2)) }
        rejected("out of range index") { DxrSceneMesh("outside", triangle, intArrayOf(0, 1, 3)) }
        rejected("incomplete triangle") { DxrSceneMesh("triangle", triangle, intArrayOf(0, 1)) }
        rejected("empty triangles") { DxrSceneMesh("indices", triangle, intArrayOf()) }
        rejected("negative mesh index") { DxrSceneData.instanceDescriptions(listOf(instance.copy(meshIndex = -1)), addresses) }
        rejected("missing mesh") { DxrSceneData.instanceDescriptions(listOf(instance.copy(meshIndex = 2)), addresses) }
        rejected("null BLAS") { DxrSceneData.instanceDescriptions(listOf(instance), longArrayOf(0)) }
        rejected("unaligned BLAS") { DxrSceneData.instanceDescriptions(listOf(instance), longArrayOf(257)) }
        rejected("short transform") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = FloatArray(15))), addresses) }
        rejected("long transform") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = FloatArray(17))), addresses) }
        for (invalid in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            rejected("nonfinite transform") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = identity().also { it[12] = invalid })), addresses) }
        }
        rejected("projective transform") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = identity().also { it[3] = .5f })), addresses) }
        rejected("nonunit affine row") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = identity().also { it[15] = 2f })), addresses) }
        rejected("collapsed scale") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = identity().also { it[0] = 0f })), addresses) }
        val dependentAxes = floatArrayOf(1f,2f,3f,0f, 2f,4f,6f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
        rejected("dependent axes") { DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = dependentAxes)), addresses) }
        // Invertibility validation must preserve ordinary negative and small nonzero scales.
        DxrSceneData.instanceDescriptions(listOf(instance.copy(transform = identity().also { it[0] = -.001f; it[5] = .001f; it[10] = .001f })), addresses)
        rejected("empty instances") { DxrSceneData.instanceDescriptions(emptyList(), addresses) }
        rejected("too many instances") { DxrSceneData.instanceDescriptions(List(1021) { instance }, addresses) }
        val maximum = DxrSceneData.instanceDescriptions(List(1020) { instance }, addresses)
        check(maximum.size == 65280)
        check(ByteBuffer.wrap(maximum).order(ByteOrder.LITTLE_ENDIAN).getInt(65264) == 0xff0003fb.toInt())
        rejected("metadata mesh index") { DxrSceneData.metadata(listOf(instance.copy(meshIndex = 2)), intArrayOf(0), intArrayOf(0)) }
        rejected("empty metadata instances") { DxrSceneData.metadata(emptyList(), intArrayOf(0), intArrayOf(0)) }
        rejected("too many metadata instances") { DxrSceneData.metadata(List(1021) { instance }, intArrayOf(0), intArrayOf(0)) }
        rejected("mismatched atlas") { DxrSceneData.metadata(listOf(instance), intArrayOf(0), intArrayOf()) }
        rejected("negative byte offset") { DxrSceneData.metadata(listOf(instance), intArrayOf(-24), intArrayOf(0)) }
        rejected("vertex element offset used as bytes") { DxrSceneData.metadata(listOf(instance), intArrayOf(3), intArrayOf(0)) }
        rejected("unaligned index bytes") { DxrSceneData.metadata(listOf(instance), intArrayOf(0), intArrayOf(3)) }
        rejected("nonfinite color") { DxrSceneData.metadata(listOf(instance.copy(color = Vec3(Float.NaN, 1f, 1f))), intArrayOf(0), intArrayOf(0)) }
        rejected("nonfinite metallic") { DxrSceneData.metadata(listOf(instance.copy(metallic = Float.NaN)), intArrayOf(0), intArrayOf(0)) }
        rejected("invalid roughness") { DxrSceneData.metadata(listOf(instance.copy(roughness = -1f)), intArrayOf(0), intArrayOf(0)) }
    }

    private fun verifyHeader() {
        check(DxrSceneData.IMAGE_OFFSET == 8192 && DxrSceneData.IMAGE_OFFSET % 512 == 0)
        check(DxrSceneData.PRIMARY_HITS_OFFSET == 0 && DxrSceneData.SHADOW_RAYS_OFFSET == 4)
        check(DxrSceneData.REFLECTION_RAYS_OFFSET == 8 && DxrSceneData.SHADOW_OCCLUSIONS_OFFSET == 12)
        check(DxrSceneData.INSTANCE_HITS_OFFSET == 16 && DxrSceneData.MAX_INSTANCES == 1020)
        check(DxrSceneData.INSTANCE_HITS_OFFSET + DxrSceneData.MAX_INSTANCES * 4 == DxrSceneData.REFLECTION_HITS_OFFSET)
        check(DxrSceneData.REFLECTION_HITS_OFFSET == 4096 && DxrSceneData.REFLECTION_HITS_OFFSET + 4 <= DxrSceneData.IMAGE_OFFSET)
        check(DxrSceneData.INSTANCE_SIZE == 64 && DxrSceneData.METADATA_SIZE == 32)
    }

    private fun identity(): FloatArray = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
    private fun hex(text: String): ByteArray = text.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun rejected(name: String, operation: () -> Unit) {
        val failure = runCatching(operation).exceptionOrNull()
        check(failure is IllegalArgumentException) { "$name must be rejected at the encoding boundary; got $failure" }
    }
    private fun rejectedLayout(name: String, operation: () -> Unit) {
        val failure = runCatching(operation).exceptionOrNull()
        check(failure is IllegalStateException) { "Independent fixture failed to reject $name" }
    }
}
