package net.echonolix.caelum.directx.demo.model

/**
 * Renderer-independent indexed triangle geometry.
 *
 * Each vertex is interleaved as `position.xyz, normal.xyz`.  Triangles use
 * counter-clockwise winding when observed from outside the surface.
 */
internal data class DemoMesh(
    val interleavedVertices: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int
        get() = interleavedVertices.size / FLOATS_PER_VERTEX

    val indexCount: Int
        get() = indices.size

    init {
        require(interleavedVertices.size % FLOATS_PER_VERTEX == 0) {
            "Vertex data must contain $FLOATS_PER_VERTEX floats per vertex"
        }
        require(indices.size % INDICES_PER_TRIANGLE == 0) {
            "Index data must contain $INDICES_PER_TRIANGLE indices per triangle"
        }
        require(indices.all { it in 0 until vertexCount }) { "Index is outside the vertex range" }
    }

    internal companion object {
        const val FLOATS_PER_VERTEX: Int = 6
        const val POSITION_OFFSET_FLOATS: Int = 0
        const val NORMAL_OFFSET_FLOATS: Int = 3
        const val INDICES_PER_TRIANGLE: Int = 3
    }
}
