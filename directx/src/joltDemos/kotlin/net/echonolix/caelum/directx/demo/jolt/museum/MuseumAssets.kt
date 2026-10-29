package net.echonolix.caelum.directx.demo.jolt.museum

import net.echonolix.caelum.directx.demo.TeapotMesh
import net.echonolix.caelum.directx.demo.jolt.Geometry
import net.echonolix.caelum.directx.demo.jolt.mesh
import net.echonolix.caelum.directx.demo.jolt.dxr.DxrSceneMesh
import net.echonolix.caelum.directx.demo.model.DemoMesh
import net.echonolix.caelum.directx.demo.model.StanfordDragonMesh

/** Immutable display meshes and matching convex-hull point clouds, in the same local coordinates. */
internal class MuseumAssets {
    val meshes = ArrayList<DxrSceneMesh>()
    private val primitives = HashMap<Geometry, Int>()
    private val shapes = HashMap<Int, Geometry>()
    val teapot: Int
    val dragon: Int

    init {
        val pot = TeapotMesh.create()
        teapot = add("teapot", normalize(DemoMesh(pot.interleavedVertices, pot.indices), 2.6f))
        dragon = add("stanford_dragon", normalize(StanfordDragonMesh.load(), 2.6f))
    }

    fun primitive(geometry: Geometry): Int = primitives.getOrPut(geometry) {
        add("primitive_${meshes.size}", geometry.mesh()).also { shapes[it] = geometry }
    }
    fun primitiveShape(index: Int): Geometry? = shapes[index]
    fun points(index: Int): FloatArray {
        val vertices = meshes[index].vertices
        return FloatArray(vertices.size / 2) { vertices[(it / 3) * 6 + it % 3] }
    }
    fun bottom(index: Int): Float {
        val vertices = meshes[index].vertices
        var minimum = Float.POSITIVE_INFINITY
        for (i in 1 until vertices.size step 6) minimum = minOf(minimum, vertices[i])
        return minimum
    }
    private fun add(name: String, mesh: DemoMesh): Int = meshes.size.also {
        meshes.add(DxrSceneMesh(name, mesh.interleavedVertices, mesh.indices))
    }
    private fun normalize(mesh: DemoMesh, extent: Float): DemoMesh {
        val min = FloatArray(3) { Float.POSITIVE_INFINITY }
        val max = FloatArray(3) { Float.NEGATIVE_INFINITY }
        for (i in mesh.interleavedVertices.indices step 6) for (axis in 0..2) {
            min[axis] = minOf(min[axis], mesh.interleavedVertices[i + axis])
            max[axis] = maxOf(max[axis], mesh.interleavedVertices[i + axis])
        }
        val scale = extent / (0..2).maxOf { max[it] - min[it] }
        val vertices = mesh.interleavedVertices.copyOf()
        for (i in vertices.indices step 6) for (axis in 0..2)
            vertices[i + axis] = (vertices[i + axis] - (min[axis] + max[axis]) * .5f) * scale
        return DemoMesh(vertices, mesh.indices)
    }
}
