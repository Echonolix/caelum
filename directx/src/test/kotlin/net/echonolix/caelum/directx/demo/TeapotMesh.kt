package net.echonolix.caelum.directx.demo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Window- and renderer-independent geometry used by the DirectX teapot demo.
 *
 * Vertices are interleaved as `position.xyz, normal.xyz`. Indices describe
 * counter-clockwise triangles when viewed from outside the surface.
 */
internal class TeapotMesh internal constructor(
    val interleavedVertices: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int
        get() = interleavedVertices.size / FLOATS_PER_VERTEX

    val indexCount: Int
        get() = indices.size

    init {
        require(interleavedVertices.size % FLOATS_PER_VERTEX == 0)
        require(indices.size % INDICES_PER_TRIANGLE == 0)
    }

    companion object {
        const val FLOATS_PER_VERTEX: Int = 6
        const val POSITION_COMPONENTS: Int = 3
        const val NORMAL_COMPONENTS: Int = 3
        const val POSITION_OFFSET_FLOATS: Int = 0
        const val NORMAL_OFFSET_FLOATS: Int = 3
        const val INDICES_PER_TRIANGLE: Int = 3

        fun create(): TeapotMesh = MeshBuilder().apply {
            addLathe(
                profile = listOf(
                    ProfilePoint(0.00f, -1.43f),
                    ProfilePoint(0.58f, -1.42f),
                    ProfilePoint(1.08f, -1.26f),
                    ProfilePoint(1.48f, -0.86f),
                    ProfilePoint(1.68f, -0.18f),
                    ProfilePoint(1.60f, 0.48f),
                    ProfilePoint(1.36f, 0.90f),
                    ProfilePoint(1.02f, 1.10f),
                    ProfilePoint(0.88f, 1.16f),
                ),
                radialSegments = 48,
            )
            addLathe(
                profile = listOf(
                    ProfilePoint(0.00f, 1.12f),
                    ProfilePoint(0.88f, 1.12f),
                    ProfilePoint(1.13f, 1.16f),
                    ProfilePoint(1.18f, 1.22f),
                    ProfilePoint(1.05f, 1.29f),
                    ProfilePoint(0.80f, 1.40f),
                    ProfilePoint(0.42f, 1.52f),
                    ProfilePoint(0.00f, 1.57f),
                ),
                radialSegments = 48,
            )
            addLathe(
                profile = listOf(
                    ProfilePoint(0.00f, 1.53f),
                    ProfilePoint(0.22f, 1.56f),
                    ProfilePoint(0.38f, 1.68f),
                    ProfilePoint(0.40f, 1.83f),
                    ProfilePoint(0.25f, 1.99f),
                    ProfilePoint(0.00f, 2.04f),
                ),
                radialSegments = 40,
            )
            addTube(
                curve = CubicCurve(
                    Vec3(1.22f, 0.22f, 0f),
                    Vec3(2.02f, 0.30f, 0f),
                    Vec3(1.98f, 1.27f, 0f),
                    Vec3(2.98f, 1.48f, 0f),
                ),
                longitudinalSegments = 30,
                radialSegments = 24,
                radius = { t ->
                    val taper = 0.46f * (1f - t) + 0.24f * t
                    if (t > 0.88f) taper + 0.055f * ((t - 0.88f) / 0.12f) else taper
                },
            )
            addTube(
                curve = CubicCurve(
                    Vec3(-1.24f, 0.70f, 0f),
                    Vec3(-3.18f, 1.62f, 0f),
                    Vec3(-3.18f, -1.52f, 0f),
                    Vec3(-1.18f, -0.78f, 0f),
                ),
                longitudinalSegments = 40,
                radialSegments = 20,
                radius = { t -> 0.23f + 0.035f * sin(PI.toFloat() * t) },
            )
        }.build()
    }
}

private data class ProfilePoint(val radius: Float, val y: Float)

private data class Vertex(val position: Vec3, val normal: Vec3)

private data class CubicCurve(
    val p0: Vec3,
    val p1: Vec3,
    val p2: Vec3,
    val p3: Vec3,
) {
    fun point(t: Float): Vec3 {
        val oneMinusT = 1f - t
        return p0 * (oneMinusT * oneMinusT * oneMinusT) +
            p1 * (3f * oneMinusT * oneMinusT * t) +
            p2 * (3f * oneMinusT * t * t) +
            p3 * (t * t * t)
    }

    fun tangent(t: Float): Vec3 {
        val oneMinusT = 1f - t
        return (p1 - p0) * (3f * oneMinusT * oneMinusT) +
            (p2 - p1) * (6f * oneMinusT * t) +
            (p3 - p2) * (3f * t * t)
    }
}

private class MeshBuilder {
    private val vertices = ArrayList<Vertex>()
    private val indices = ArrayList<Int>()

    fun addLathe(profile: List<ProfilePoint>, radialSegments: Int) {
        require(profile.size >= 2)
        require(radialSegments >= 3)

        val rings = profile.mapIndexed { profileIndex, point ->
            val previous = profile[(profileIndex - 1).coerceAtLeast(0)]
            val next = profile[(profileIndex + 1).coerceAtMost(profile.lastIndex)]
            val deltaRadius = next.radius - previous.radius
            val deltaY = next.y - previous.y
            IntArray(radialSegments + 1) { radialIndex ->
                val angle = TWO_PI * radialIndex / radialSegments
                val cosine = cos(angle)
                val sine = sin(angle)
                addVertex(
                    Vertex(
                        position = Vec3(point.radius * cosine, point.y, point.radius * sine),
                        normal = Vec3(deltaY * cosine, -deltaRadius, deltaY * sine).normalized(),
                    ),
                )
            }
        }

        for (profileIndex in 0 until profile.lastIndex) {
            for (radialIndex in 0 until radialSegments) {
                val lowerLeft = rings[profileIndex][radialIndex]
                val upperLeft = rings[profileIndex + 1][radialIndex]
                val upperRight = rings[profileIndex + 1][radialIndex + 1]
                val lowerRight = rings[profileIndex][radialIndex + 1]
                addTriangle(lowerLeft, upperLeft, upperRight)
                addTriangle(lowerLeft, upperRight, lowerRight)
            }
        }
    }

    fun addTube(
        curve: CubicCurve,
        longitudinalSegments: Int,
        radialSegments: Int,
        radius: (Float) -> Float,
    ) {
        require(longitudinalSegments >= 1)
        require(radialSegments >= 3)

        val rings = List(longitudinalSegments + 1) { longitudinalIndex ->
            val t = longitudinalIndex.toFloat() / longitudinalSegments
            val center = curve.point(t)
            val tangent = curve.tangent(t).normalized()
            val side = Vec3(-tangent.y, tangent.x, 0f).normalized()
            val binormal = Vec3(0f, 0f, 1f)
            IntArray(radialSegments + 1) { radialIndex ->
                val angle = TWO_PI * radialIndex / radialSegments
                val normal = (side * cos(angle) + binormal * sin(angle)).normalized()
                addVertex(Vertex(center + normal * radius(t), normal))
            }
        }

        for (longitudinalIndex in 0 until longitudinalSegments) {
            for (radialIndex in 0 until radialSegments) {
                val lowerLeft = rings[longitudinalIndex][radialIndex]
                val upperLeft = rings[longitudinalIndex + 1][radialIndex]
                val upperRight = rings[longitudinalIndex + 1][radialIndex + 1]
                val lowerRight = rings[longitudinalIndex][radialIndex + 1]
                addTriangle(lowerLeft, upperRight, upperLeft)
                addTriangle(lowerLeft, lowerRight, upperRight)
            }
        }
    }

    fun build(): TeapotMesh {
        val interleavedVertices = FloatArray(vertices.size * TeapotMesh.FLOATS_PER_VERTEX)
        vertices.forEachIndexed { vertexIndex, vertex ->
            val offset = vertexIndex * TeapotMesh.FLOATS_PER_VERTEX
            interleavedVertices[offset] = vertex.position.x
            interleavedVertices[offset + 1] = vertex.position.y
            interleavedVertices[offset + 2] = vertex.position.z
            interleavedVertices[offset + 3] = vertex.normal.x
            interleavedVertices[offset + 4] = vertex.normal.y
            interleavedVertices[offset + 5] = vertex.normal.z
        }
        return TeapotMesh(interleavedVertices, indices.toIntArray())
    }

    private fun addVertex(vertex: Vertex): Int {
        vertices += vertex
        return vertices.lastIndex
    }

    private fun addTriangle(first: Int, second: Int, third: Int) {
        val firstPosition = vertices[first].position
        val secondPosition = vertices[second].position
        val thirdPosition = vertices[third].position
        val areaVector = (secondPosition - firstPosition).cross(thirdPosition - firstPosition)
        if (areaVector.lengthSquared() <= MIN_TRIANGLE_AREA_VECTOR_SQUARED) return

        indices += first
        indices += second
        indices += third
    }

    private companion object {
        const val TWO_PI: Float = (2.0 * PI).toFloat()
        const val MIN_TRIANGLE_AREA_VECTOR_SQUARED: Float = 1e-10f
    }
}

internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun lengthSquared(): Float = dot(this)

    fun normalized(): Vec3 {
        val length = sqrt(lengthSquared())
        check(length > MIN_NORMALIZABLE_LENGTH) { "Cannot normalize a zero-length vector" }
        return this * (1f / length)
    }

    private companion object {
        const val MIN_NORMALIZABLE_LENGTH: Float = 1e-7f
    }
}
