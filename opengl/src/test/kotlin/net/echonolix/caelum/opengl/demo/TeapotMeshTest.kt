package net.echonolix.caelum.opengl.demo

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeapotMeshTest {
    @Test
    fun `procedural teapot has finite positions and unit normals`() {
        val vertices = createTeapotVertices()

        assertEquals(0, vertices.size % 6)
        assertTrue(vertices.size / 6 > 10_000)
        assertTrue(vertices.all(Float::isFinite))

        val positions = vertices.asList().chunked(6)
        assertTrue(positions.minOf { it[0] } < -2.6f, "handle must extend left of the body")
        assertTrue(positions.maxOf { it[0] } > 2.9f, "spout must extend right of the body")
        assertTrue(positions.minOf { it[1] } < -1.4f, "body must have a closed base")
        assertTrue(positions.maxOf { it[1] } > 2.0f, "lid knob must extend above the body")

        positions.forEach { vertex ->
            val normalLength = sqrt(
                vertex[3] * vertex[3] + vertex[4] * vertex[4] + vertex[5] * vertex[5],
            )
            assertTrue(abs(normalLength - 1f) < 1e-4f, "normal length was $normalLength")
        }
    }
}
