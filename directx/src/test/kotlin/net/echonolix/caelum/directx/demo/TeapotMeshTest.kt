package net.echonolix.caelum.directx.demo

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeapotMeshTest {
    @Test
    fun `indexed teapot has finite bounded geometry and unit normals`() {
        val mesh = TeapotMesh.create()
        val vertices = mesh.interleavedVertices

        assertEquals(0, vertices.size % TeapotMesh.FLOATS_PER_VERTEX)
        assertTrue(mesh.vertexCount > 2_500, "teapot should have enough vertices for a smooth silhouette")
        assertTrue(vertices.all(Float::isFinite))

        val xCoordinates = ArrayList<Float>(mesh.vertexCount)
        val yCoordinates = ArrayList<Float>(mesh.vertexCount)
        for (vertexIndex in 0 until mesh.vertexCount) {
            val offset = vertexIndex * TeapotMesh.FLOATS_PER_VERTEX
            xCoordinates += vertices[offset]
            yCoordinates += vertices[offset + 1]

            val normalLength = sqrt(
                vertices[offset + 3] * vertices[offset + 3] +
                    vertices[offset + 4] * vertices[offset + 4] +
                    vertices[offset + 5] * vertices[offset + 5],
            )
            assertTrue(abs(normalLength - 1f) < 1e-4f, "normal length was $normalLength")
        }

        assertTrue(xCoordinates.min() < -2.6f, "handle must extend left of the body")
        assertTrue(xCoordinates.max() > 2.9f, "spout must extend right of the body")
        assertTrue(yCoordinates.min() < -1.4f, "body must have a closed base")
        assertTrue(yCoordinates.max() > 2.0f, "lid knob must extend above the body")
    }

    @Test
    fun `indices form in-range non-degenerate outward triangles`() {
        val mesh = TeapotMesh.create()

        assertEquals(0, mesh.indexCount % TeapotMesh.INDICES_PER_TRIANGLE)
        assertTrue(mesh.indexCount > 13_000, "teapot should have enough triangles for a smooth silhouette")
        assertTrue(mesh.indices.all { it in 0 until mesh.vertexCount })

        mesh.indices.asList().chunked(TeapotMesh.INDICES_PER_TRIANGLE).forEach { triangle ->
            val first = mesh.position(triangle[0])
            val second = mesh.position(triangle[1])
            val third = mesh.position(triangle[2])
            val areaVector = (second - first).cross(third - first)
            assertTrue(areaVector.lengthSquared() > 1e-10f, "triangle must not be degenerate")

            val averageNormal = mesh.normal(triangle[0]) + mesh.normal(triangle[1]) + mesh.normal(triangle[2])
            assertTrue(areaVector.dot(averageNormal) > 0f, "triangle winding must agree with its vertex normals")
        }
    }

    private fun TeapotMesh.position(vertexIndex: Int): Vec3 {
        val offset = vertexIndex * TeapotMesh.FLOATS_PER_VERTEX
        return Vec3(
            interleavedVertices[offset],
            interleavedVertices[offset + 1],
            interleavedVertices[offset + 2],
        )
    }

    private fun TeapotMesh.normal(vertexIndex: Int): Vec3 {
        val offset = vertexIndex * TeapotMesh.FLOATS_PER_VERTEX + TeapotMesh.NORMAL_OFFSET_FLOATS
        return Vec3(
            interleavedVertices[offset],
            interleavedVertices[offset + 1],
            interleavedVertices[offset + 2],
        )
    }
}
