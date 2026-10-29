package net.echonolix.caelum.directx.demo.jolt

import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.directx.demo.model.DemoMesh
import net.echonolix.caelum.jolt.JoltPosition
import net.echonolix.caelum.jolt.JoltRotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dimensions are baked into vertices, so the model matrix is a rigid Jolt transform. */
internal sealed interface Geometry {
    data class Box(val x: Float, val y: Float, val z: Float) : Geometry
    data class Sphere(val radius: Float) : Geometry
    data class Capsule(val halfHeight: Float, val radius: Float) : Geometry
}

internal fun Geometry.mesh(): DemoMesh = when (this) {
    is Geometry.Box -> boxMesh(x, y, z)
    is Geometry.Sphere -> capsuleMesh(0f, radius)
    is Geometry.Capsule -> capsuleMesh(halfHeight, radius)
}

private fun boxMesh(x: Float, y: Float, z: Float): DemoMesh {
    val vertices = ArrayList<Float>()
    val indices = ArrayList<Int>()
    fun face(n: Vec3, u: Vec3, v: Vec3) {
        val start = vertices.size / 6
        for ((a, b) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
            val p = n + u * a + v * b
            vertices.addAll(listOf(p.x * x, p.y * y, p.z * z, n.x, n.y, n.z))
        }
        indices.addAll(listOf(0, 1, 2, 0, 2, 3).map { start + it })
    }
    face(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f))
    face(Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f))
    face(Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f))
    face(Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f))
    face(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f))
    face(Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f))
    return DemoMesh(vertices.toFloatArray(), indices.toIntArray())
}

private fun capsuleMesh(halfHeight: Float, radius: Float): DemoMesh {
    val sides = 24
    val hemiRings = 8
    val profile = buildList {
        for (i in 0..hemiRings) add((-PI / 2 + i * PI / (2 * hemiRings)) to -halfHeight)
        for (i in 0..hemiRings) add((i * PI / (2 * hemiRings)) to halfHeight)
    }
    val vertices = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for ((angle, offset) in profile) for (side in 0..sides) {
        val longitude = side * 2 * PI / sides
        val nx = (cos(angle) * cos(longitude)).toFloat()
        val ny = sin(angle).toFloat()
        val nz = (cos(angle) * sin(longitude)).toFloat()
        vertices.addAll(listOf(radius * nx, offset + radius * ny, radius * nz, nx, ny, nz))
    }
    for (ring in 0 until profile.lastIndex) for (side in 0 until sides) {
        val a = ring * (sides + 1) + side
        val b = a + sides + 1
        indices.addAll(listOf(a, b, b + 1, a, b + 1, a + 1))
    }
    return DemoMesh(vertices.toFloatArray(), indices.toIntArray())
}

internal fun modelMatrix(p: JoltPosition, q: JoltRotation): FloatArray {
    val (x, y, z, w) = q
    return floatArrayOf(
        1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
        2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
        2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
        p.x.toFloat(), p.y.toFloat(), p.z.toFloat(), 1f,
    )
}
