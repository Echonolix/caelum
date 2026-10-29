package net.echonolix.caelum.directx.demo.jolt.dxr

import net.echonolix.caelum.directx.demo.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Vertices are interleaved position and normal triples; indices are mesh-local. */
internal data class DxrSceneMesh(val name: String, val vertices: FloatArray, val indices: IntArray) {
    init {
        require(vertices.isNotEmpty() && vertices.size % 6 == 0 && vertices.all(Float::isFinite)) { "Invalid position/normal vertices: $name" }
        require(indices.isNotEmpty() && indices.size % 3 == 0 && indices.all { it in 0 until vertices.size / 6 }) { "Invalid triangle indices: $name" }
    }
}

/** Transform is column-major and contains a rigid or uniformly scaled affine transform. */
internal data class DxrSceneInstance(
    val meshIndex: Int,
    val transform: FloatArray,
    val color: Vec3,
    val metallic: Float = 0f,
    val roughness: Float = .35f,
    val objectId: Int = 0,
)

/** CPU encoding for the SDK's 64-byte D3D12_RAYTRACING_INSTANCE_DESC and shader metadata. */
internal object DxrSceneData {
    const val MAX_INSTANCES = 1020
    const val IMAGE_OFFSET = 8192
    const val PRIMARY_HITS_OFFSET = 0
    const val SHADOW_RAYS_OFFSET = 4
    const val REFLECTION_RAYS_OFFSET = 8
    const val SHADOW_OCCLUSIONS_OFFSET = 12
    const val INSTANCE_HITS_OFFSET = 16
    const val REFLECTION_HITS_OFFSET = 4096
    const val INSTANCE_SIZE = 64
    const val METADATA_SIZE = 32

    fun instanceDescriptions(instances: List<DxrSceneInstance>, blasAddresses: LongArray): ByteArray {
        require(instances.size in 1..MAX_INSTANCES)
        val bytes = ByteBuffer.allocate(instances.size * INSTANCE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        instances.forEachIndexed { index, instance ->
            require(instance.meshIndex in blasAddresses.indices) { "Instance mesh index is outside the BLAS atlas" }
            val address = blasAddresses[instance.meshIndex]
            require(address != 0L && (address and 255L) == 0L) { "A BLAS address must be nonzero and 256-byte aligned" }
            val transform = instance.transform
            require(transform.size == 16 && transform.all(Float::isFinite)) { "An instance transform must contain 16 finite floats" }
            require(transform[3] == 0f && transform[7] == 0f && transform[11] == 0f && transform[15] == 1f) { "DXR instances require an affine transform" }
            // DXR cannot traverse a collapsed instance: its inverse transform must exist.
            val determinant = transform[0].toDouble() * (transform[5].toDouble() * transform[10] - transform[9].toDouble() * transform[6]) -
                transform[4].toDouble() * (transform[1].toDouble() * transform[10] - transform[9].toDouble() * transform[2]) +
                transform[8].toDouble() * (transform[1].toDouble() * transform[6] - transform[5].toDouble() * transform[2])
            require(determinant != 0.0) { "DXR instances require an invertible transform" }
            // SDK transform is row-major 3x4; scene matrices are column-major 4x4.
            for (row in 0..2) for (column in 0..3) bytes.putFloat(instance.transform[column * 4 + row])
            bytes.putInt(index or (0xff shl 24)) // InstanceID:24, InstanceMask:8
            bytes.putInt(0) // Hit-group contribution:24, flags:8
            bytes.putLong(address)
        }
        return bytes.array()
    }

    /** Atlas offsets are bytes, while each mesh's uint32 indices remain local to that mesh. */
    fun metadata(instances: List<DxrSceneInstance>, vertexOffsets: IntArray, indexOffsets: IntArray): ByteArray {
        require(instances.size in 1..MAX_INSTANCES && vertexOffsets.size == indexOffsets.size)
        require(vertexOffsets.all { it >= 0 && it % 24 == 0 } && indexOffsets.all { it >= 0 && it % 4 == 0 }) { "Atlas byte offsets must align to their vertex/index records" }
        val bytes = ByteBuffer.allocate(instances.size * METADATA_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        instances.forEach { instance ->
            require(instance.meshIndex in vertexOffsets.indices)
            require(instance.color.x.isFinite() && instance.color.y.isFinite() && instance.color.z.isFinite()) { "Instance color must be finite" }
            require(instance.metallic in 0f..1f && instance.roughness in 0f..1f) { "Instance metallic and roughness must be in 0..1" }
            bytes.putInt(indexOffsets[instance.meshIndex]); bytes.putInt(vertexOffsets[instance.meshIndex])
            bytes.putFloat(instance.color.x); bytes.putFloat(instance.color.y); bytes.putFloat(instance.color.z)
            bytes.putFloat(instance.metallic); bytes.putFloat(instance.roughness); bytes.putInt(instance.objectId)
        }
        return bytes.array()
    }
}
