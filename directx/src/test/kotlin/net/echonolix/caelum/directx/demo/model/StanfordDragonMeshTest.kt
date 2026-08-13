package net.echonolix.caelum.directx.demo.model

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StanfordDragonMeshTest {
    @Test
    fun `packaged model is the reviewed official Stanford res3 asset`() {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(StanfordDragonMesh.RESOURCE_PATH)) {
            "Stanford Dragon resource was not found: ${StanfordDragonMesh.RESOURCE_PATH}"
        }.use { it.readAllBytes() }
        val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }

        assertEquals(OFFICIAL_RES3_SHA256, actualHash)
    }

    @Test
    fun `official res3 dragon is normalized indexed geometry with consistent display winding`() {
        val mesh = StanfordDragonMesh.load()

        assertEquals(22_982, mesh.vertexCount)
        assertEquals(46_540 * DemoMesh.INDICES_PER_TRIANGLE, mesh.indexCount)
        assertTrue(mesh.interleavedVertices.all(Float::isFinite))
        assertTrue(mesh.indices.all { it in 0 until mesh.vertexCount })

        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (vertexIndex in 0 until mesh.vertexCount) {
            val offset = vertexIndex * DemoMesh.FLOATS_PER_VERTEX
            minX = minOf(minX, mesh.interleavedVertices[offset]); minY = minOf(minY, mesh.interleavedVertices[offset + 1]); minZ = minOf(minZ, mesh.interleavedVertices[offset + 2])
            maxX = maxOf(maxX, mesh.interleavedVertices[offset]); maxY = maxOf(maxY, mesh.interleavedVertices[offset + 1]); maxZ = maxOf(maxZ, mesh.interleavedVertices[offset + 2])
            val normalLength = sqrt(mesh.interleavedVertices[offset + 3] * mesh.interleavedVertices[offset + 3] + mesh.interleavedVertices[offset + 4] * mesh.interleavedVertices[offset + 4] + mesh.interleavedVertices[offset + 5] * mesh.interleavedVertices[offset + 5])
            assertTrue(abs(normalLength - 1f) < 1e-4f, "normal $vertexIndex has length $normalLength")
        }
        assertTrue(abs(minX + maxX) < 1e-5f && abs(minY + maxY) < 1e-5f && abs(minZ + maxZ) < 1e-5f, "bounding box must be centered")
        assertTrue(maxOf(maxX - minX, maxY - minY, maxZ - minZ) in 1.9999f..2.0001f, "largest bounding extent must be two")

        val referenced = BooleanArray(mesh.vertexCount)
        val faces = HashSet<List<Int>>()
        var windingAgreement = 0.0
        for (offset in mesh.indices.indices step DemoMesh.INDICES_PER_TRIANGLE) {
            repeat(3) { referenced[mesh.indices[offset + it]] = true }
            assertTrue(faces.add(mesh.indices.slice(offset until offset + 3).sorted()), "duplicate triangle at ${offset / 3}")
            val first = mesh.position(mesh.indices[offset])
            val second = mesh.position(mesh.indices[offset + 1])
            val third = mesh.position(mesh.indices[offset + 2])
            val area = (second - first).cross(third - first)
            assertTrue(area.lengthSquared() > 1e-14f, "triangle ${offset / 3} is degenerate")
            val averageNormal = mesh.normal(mesh.indices[offset]) + mesh.normal(mesh.indices[offset + 1]) + mesh.normal(mesh.indices[offset + 2])
            windingAgreement += area.dot(averageNormal).toDouble()
        }
        assertTrue(referenced.all { it }, "cleaned mesh must not retain unused vertices")
        assertTrue(windingAgreement > 0.0, "display normals must have positive aggregate agreement with face winding")

        val edgeUses = HashMap<Long, MutableList<Boolean>>()
        for (offset in mesh.indices.indices step 3) repeat(3) { corner ->
            val from = mesh.indices[offset + corner]
            val to = mesh.indices[offset + (corner + 1) % 3]
            val low = minOf(from, to)
            val high = maxOf(from, to)
            val key = (low.toLong() shl 32) or (high.toLong() and 0xffffffffL)
            edgeUses.getOrPut(key, ::ArrayList).add(from < to)
        }
        edgeUses.values.filter { it.size == 2 }.forEach { uses ->
            assertTrue(uses[0] != uses[1], "each manifold edge must be oppositely directed by its adjacent faces")
        }
    }

    private fun DemoMesh.position(index: Int): Vec3 {
        val offset = index * DemoMesh.FLOATS_PER_VERTEX
        return Vec3(interleavedVertices[offset], interleavedVertices[offset + 1], interleavedVertices[offset + 2])
    }

    private fun DemoMesh.normal(index: Int): Vec3 {
        val offset = index * DemoMesh.FLOATS_PER_VERTEX + DemoMesh.NORMAL_OFFSET_FLOATS
        return Vec3(interleavedVertices[offset], interleavedVertices[offset + 1], interleavedVertices[offset + 2])
    }

    private data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
        fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z
        fun cross(other: Vec3): Vec3 = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
        fun lengthSquared(): Float = dot(this)
    }

    private companion object {
        private const val OFFICIAL_RES3_SHA256: String =
            "F32B87762894BDE78CD45DC05AA9FDE0F5AD390C944168A96E97191FF1FC6D45"
    }
}
