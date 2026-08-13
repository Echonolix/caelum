package net.echonolix.caelum.directx.demo.model

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.sqrt

/** Loads the decimated Stanford Dragon used by the interactive DirectX demos. */
internal object StanfordDragonMesh {
    internal const val RESOURCE_PATH: String =
        "net/echonolix/caelum/directx/demo/model/stanford_dragon_res3.ply"

    /**
     * Loads strict ASCII PLY 1.0 data, centers its axis-aligned bounding box at
     * the origin and uniformly scales its largest extent to two units.
     */
    fun load(classLoader: ClassLoader = StanfordDragonMesh::class.java.classLoader): DemoMesh {
        val stream = requireNotNull(classLoader.getResourceAsStream(RESOURCE_PATH)) {
            "Stanford Dragon resource was not found: $RESOURCE_PATH"
        }
        return stream.use(::loadAsciiPly)
    }

    internal fun loadAsciiPly(input: InputStream): DemoMesh {
        val bytes = input.readAllBytes()
        require(bytes.all { (it.toInt() and 0xff) <= ASCII_MAX }) { "PLY input must be ASCII" }

        BufferedReader(InputStreamReader(bytes.inputStream(), StandardCharsets.US_ASCII)).use { reader ->
            val header = readHeader(reader)
            val positions = FloatArray(header.vertexCount * POSITION_COMPONENTS)

            repeat(header.vertexCount) { vertexIndex ->
                val values = parseTokens(requireLine(reader, "vertex", vertexIndex), "vertex", vertexIndex)
                require(values.size == header.vertexProperties.size) {
                    "Vertex $vertexIndex has ${values.size} values; expected ${header.vertexProperties.size}"
                }
                header.vertexProperties.forEachIndexed { propertyIndex, property ->
                    val value = parseFiniteFloat(values[propertyIndex], "vertex $vertexIndex property $property")
                    when (property) {
                        "x" -> positions[vertexIndex * POSITION_COMPONENTS] = value
                        "y" -> positions[vertexIndex * POSITION_COMPONENTS + 1] = value
                        "z" -> positions[vertexIndex * POSITION_COMPONENTS + 2] = value
                        // The final normals are deliberately rebuilt from the cleaned topology.
                        "nx", "ny", "nz" -> Unit
                    }
                }
            }

            val sourceIndices = IntArray(header.faceCount * DemoMesh.INDICES_PER_TRIANGLE)
            repeat(header.faceCount) { faceIndex ->
                val values = parseTokens(requireLine(reader, "face", faceIndex), "face", faceIndex)
                require(values.size == 4 && values[0] == "3") {
                    "Face $faceIndex must be a triangular '3 i j k' record"
                }
                repeat(DemoMesh.INDICES_PER_TRIANGLE) { corner ->
                    val index = parseIndex(values[corner + 1], "face $faceIndex index $corner")
                    require(index < header.vertexCount) {
                        "Face $faceIndex references vertex $index but only ${header.vertexCount} vertices exist"
                    }
                    sourceIndices[faceIndex * DemoMesh.INDICES_PER_TRIANGLE + corner] = index
                }
            }
            require(reader.readLine() == null) { "PLY contains data after its declared faces" }

            val uniqueIndices = removeDuplicateFaces(sourceIndices)
            val compacted = compactReferencedVertices(positions, uniqueIndices)
            normalizePositions(compacted.positions)
            requireNonDegenerateTriangles(compacted.positions, compacted.indices)
            orientConsistentlyForDisplay(compacted.positions, compacted.indices)
            val normals = generateSmoothNormals(compacted.positions, compacted.indices)
            normalizeNormals(normals)
            return interleave(compacted.positions, normals, compacted.indices)
        }
    }

    private fun removeDuplicateFaces(indices: IntArray): IntArray {
        val seen = HashSet<FaceKey>(indices.size / DemoMesh.INDICES_PER_TRIANGLE)
        val unique = ArrayList<Int>(indices.size)
        for (offset in indices.indices step DemoMesh.INDICES_PER_TRIANGLE) {
            val first = indices[offset]
            val second = indices[offset + 1]
            val third = indices[offset + 2]
            val sorted = intArrayOf(first, second, third).also { it.sort() }
            if (seen.add(FaceKey(sorted[0], sorted[1], sorted[2]))) {
                unique += first; unique += second; unique += third
            }
        }
        return unique.toIntArray()
    }

    private fun compactReferencedVertices(positions: FloatArray, indices: IntArray): CompactedMesh {
        val remap = IntArray(positions.size / POSITION_COMPONENTS) { -1 }
        var vertexCount = 0
        for (index in indices) if (remap[index] == -1) remap[index] = vertexCount++

        val compactedPositions = FloatArray(vertexCount * POSITION_COMPONENTS)
        for (oldIndex in remap.indices) {
            val newIndex = remap[oldIndex]
            if (newIndex >= 0) positions.copyInto(
                compactedPositions,
                newIndex * POSITION_COMPONENTS,
                oldIndex * POSITION_COMPONENTS,
                oldIndex * POSITION_COMPONENTS + POSITION_COMPONENTS,
            )
        }
        return CompactedMesh(compactedPositions, IntArray(indices.size) { remap[indices[it]] })
    }

    private fun readHeader(reader: BufferedReader): PlyHeader {
        require(reader.readLine() == "ply") { "PLY must begin with exactly 'ply'" }
        require(reader.readLine() == "format ascii 1.0") { "Only ASCII PLY 1.0 is supported" }

        var state = HeaderState.BEFORE_ELEMENT
        var vertexCount: Int? = null
        var faceCount: Int? = null
        var faceIndexPropertyDeclared = false
        val vertexProperties = ArrayList<String>()
        while (true) {
            val line = requireNotNull(reader.readLine()) { "PLY header ended before end_header" }
            when {
                line == "end_header" -> break
                line.startsWith("comment ") || line.startsWith("obj_info ") -> Unit
                line.startsWith("element ") -> {
                    val tokens = headerTokens(line)
                    require(tokens.size == 3) { "Invalid element declaration: $line" }
                    when (tokens[1]) {
                        "vertex" -> {
                            require(state == HeaderState.BEFORE_ELEMENT && vertexCount == null) { "Vertex element must be first and unique" }
                            vertexCount = parsePositiveCount(tokens[2], "vertex count")
                            state = HeaderState.VERTEX
                        }
                        "face" -> {
                            require(state == HeaderState.VERTEX && faceCount == null) { "Face element must follow vertex properties" }
                            faceCount = parsePositiveCount(tokens[2], "face count")
                            state = HeaderState.FACE
                        }
                        else -> error("Unsupported PLY element '${tokens[1]}'")
                    }
                }
                line.startsWith("property ") -> when (state) {
                    HeaderState.VERTEX -> {
                        val tokens = headerTokens(line)
                        require(tokens.size == 3 && tokens[1] == "float" && tokens[2] in VERTEX_PROPERTY_NAMES) {
                            "Vertex properties must be float x/y/z with optional float nx/ny/nz: $line"
                        }
                        require(tokens[2] !in vertexProperties) { "Duplicate vertex property '${tokens[2]}'" }
                        vertexProperties += tokens[2]
                    }
                    HeaderState.FACE -> require(line == "property list uchar int vertex_indices") {
                        "Faces must declare exactly 'property list uchar int vertex_indices'"
                    }.also {
                        require(!faceIndexPropertyDeclared) { "Duplicate face index property" }
                        faceIndexPropertyDeclared = true
                    }
                    HeaderState.BEFORE_ELEMENT -> error("Property precedes an element")
                }
                else -> error("Unsupported PLY header line: $line")
            }
        }

        val actualVertexCount = requireNotNull(vertexCount) { "PLY has no vertex element" }
        val actualFaceCount = requireNotNull(faceCount) { "PLY has no face element" }
        require(state == HeaderState.FACE) { "PLY has no face element" }
        require(faceIndexPropertyDeclared) { "PLY faces must declare vertex_indices" }
        require(vertexProperties.containsAll(POSITION_PROPERTY_NAMES)) { "PLY vertices must contain x, y and z" }
        val normalPropertyCount = vertexProperties.count { it in NORMAL_PROPERTY_NAMES }
        require(normalPropertyCount == 0 || normalPropertyCount == NORMAL_PROPERTY_NAMES.size) {
            "PLY normals must provide nx, ny and nz together"
        }
        require(vertexProperties.size == POSITION_PROPERTY_NAMES.size + normalPropertyCount) {
            "PLY vertex properties are restricted to position and optional normals"
        }
        return PlyHeader(actualVertexCount, actualFaceCount, vertexProperties, normalPropertyCount != 0)
    }

    private fun normalizePositions(positions: FloatArray) {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY; var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (offset in positions.indices step POSITION_COMPONENTS) {
            minX = minOf(minX, positions[offset]); minY = minOf(minY, positions[offset + 1]); minZ = minOf(minZ, positions[offset + 2])
            maxX = maxOf(maxX, positions[offset]); maxY = maxOf(maxY, positions[offset + 1]); maxZ = maxOf(maxZ, positions[offset + 2])
        }
        val maxExtent = maxOf(maxX - minX, maxY - minY, maxZ - minZ)
        require(maxExtent > MIN_EXTENT) { "PLY has zero spatial extent" }
        val centerX = (minX + maxX) * 0.5f; val centerY = (minY + maxY) * 0.5f; val centerZ = (minZ + maxZ) * 0.5f
        val scale = TARGET_MAX_EXTENT / maxExtent
        for (offset in positions.indices step POSITION_COMPONENTS) {
            positions[offset] = (positions[offset] - centerX) * scale
            positions[offset + 1] = (positions[offset + 1] - centerY) * scale
            positions[offset + 2] = (positions[offset + 2] - centerZ) * scale
        }
    }

    private fun requireNonDegenerateTriangles(positions: FloatArray, indices: IntArray) {
        for (offset in indices.indices step DemoMesh.INDICES_PER_TRIANGLE) {
            require(areaVector(positions, indices[offset], indices[offset + 1], indices[offset + 2]).lengthSquared() > MIN_AREA_VECTOR_SQUARED) {
                "Face ${offset / DemoMesh.INDICES_PER_TRIANGLE} is degenerate"
            }
        }
    }

    private fun orientConsistentlyForDisplay(positions: FloatArray, indices: IntArray) {
        val faceCount = indices.size / DemoMesh.INDICES_PER_TRIANGLE
        val edges = HashMap<Long, MutableList<EdgeUse>>()
        repeat(faceCount) { face ->
            val offset = face * DemoMesh.INDICES_PER_TRIANGLE
            repeat(DemoMesh.INDICES_PER_TRIANGLE) { corner ->
                val from = indices[offset + corner]
                val to = indices[offset + (corner + 1) % DemoMesh.INDICES_PER_TRIANGLE]
                edges.getOrPut(edgeKey(from, to), ::ArrayList).add(EdgeUse(face, from < to))
            }
        }

        val adjacency = Array(faceCount) { ArrayList<OrientationConstraint>() }
        for (uses in edges.values) {
            // Non-manifold edges cannot make every incident pair oppositely directed. They are
            // intentionally excluded from winding constraints; their manifold neighbours still
            // orient each sheet deterministically.
            if (uses.size != 2) continue
            val first = uses[0]
            val second = uses[1]
            val sameFlip = first.canonicalDirection != second.canonicalDirection
            adjacency[first.face] += OrientationConstraint(second.face, sameFlip)
            adjacency[second.face] += OrientationConstraint(first.face, sameFlip)
        }

        val flip = arrayOfNulls<Boolean>(faceCount)
        repeat(faceCount) { seed ->
            if (flip[seed] != null) return@repeat
            val component = ArrayList<Int>()
            val queue = ArrayDeque<Int>()
            flip[seed] = false
            queue.add(seed)
            while (queue.isNotEmpty()) {
                val face = queue.removeFirst()
                component += face
                for (constraint in adjacency[face]) {
                    val expected = if (constraint.sameFlip) flip[face]!! else !flip[face]!!
                    val existing = flip[constraint.face]
                    require(existing == null || existing == expected) {
                        "PLY manifold winding constraints conflict at face ${constraint.face}"
                    }
                    if (existing == null) {
                        flip[constraint.face] = expected
                        queue.add(constraint.face)
                    }
                }
            }

            for (face in component) if (flip[face] == true) flipFace(indices, face)

            // Choose a deterministic display-facing direction from area-weighted agreement
            // relative to the component centroid. For a closed component this has the same sign
            // as signed volume and is translation invariant. For an open or non-manifold
            // component it is only a visual-orientation heuristic, not an outwardness guarantee.
            val componentVertices = HashSet<Int>()
            for (face in component) {
                val offset = face * DemoMesh.INDICES_PER_TRIANGLE
                repeat(DemoMesh.INDICES_PER_TRIANGLE) { componentVertices += indices[offset + it] }
            }
            var centerX = 0.0; var centerY = 0.0; var centerZ = 0.0
            for (vertex in componentVertices) {
                val point = position(positions, vertex)
                centerX += point.x; centerY += point.y; centerZ += point.z
            }
            centerX /= componentVertices.size; centerY /= componentVertices.size; centerZ /= componentVertices.size
            var outwardAgreement = 0.0
            for (face in component) {
                val offset = face * DemoMesh.INDICES_PER_TRIANGLE
                val first = indices[offset]
                val second = indices[offset + 1]
                val third = indices[offset + 2]
                val centroidTimesThree = position(positions, first) + position(positions, second) + position(positions, third)
                val relativeCentroidTimesThree = Vec3(
                    (centroidTimesThree.x - centerX * 3.0).toFloat(),
                    (centroidTimesThree.y - centerY * 3.0).toFloat(),
                    (centroidTimesThree.z - centerZ * 3.0).toFloat(),
                )
                outwardAgreement += relativeCentroidTimesThree.dot(areaVector(positions, first, second, third)).toDouble()
            }
            if (outwardAgreement < 0.0) for (face in component) flipFace(indices, face)
        }
    }

    private fun flipFace(indices: IntArray, face: Int) {
        val offset = face * DemoMesh.INDICES_PER_TRIANGLE
        val second = indices[offset + 1]
        indices[offset + 1] = indices[offset + 2]
        indices[offset + 2] = second
    }

    private fun edgeKey(first: Int, second: Int): Long {
        val low = minOf(first, second)
        val high = maxOf(first, second)
        return (low.toLong() shl 32) or (high.toLong() and 0xffffffffL)
    }

    private fun generateSmoothNormals(positions: FloatArray, indices: IntArray): FloatArray {
        val normals = FloatArray(positions.size)
        for (offset in indices.indices step DemoMesh.INDICES_PER_TRIANGLE) {
            val area = areaVector(positions, indices[offset], indices[offset + 1], indices[offset + 2])
            repeat(DemoMesh.INDICES_PER_TRIANGLE) { corner ->
                val normalOffset = indices[offset + corner] * POSITION_COMPONENTS
                normals[normalOffset] += area.x; normals[normalOffset + 1] += area.y; normals[normalOffset + 2] += area.z
            }
        }
        return normals
    }

    private fun normalizeNormals(normals: FloatArray) {
        for (offset in normals.indices step POSITION_COMPONENTS) {
            val normal = Vec3(normals[offset], normals[offset + 1], normals[offset + 2])
            val length = sqrt(normal.lengthSquared())
            require(length > MIN_NORMAL_LENGTH) { "Vertex ${offset / POSITION_COMPONENTS} has a zero-length normal" }
            normals[offset] /= length; normals[offset + 1] /= length; normals[offset + 2] /= length
        }
    }

    private fun interleave(positions: FloatArray, normals: FloatArray, indices: IntArray): DemoMesh {
        val vertices = FloatArray(positions.size * DemoMesh.FLOATS_PER_VERTEX / POSITION_COMPONENTS)
        for (vertexIndex in 0 until positions.size / POSITION_COMPONENTS) {
            val positionOffset = vertexIndex * POSITION_COMPONENTS
            val vertexOffset = vertexIndex * DemoMesh.FLOATS_PER_VERTEX
            vertices[vertexOffset] = positions[positionOffset]; vertices[vertexOffset + 1] = positions[positionOffset + 1]; vertices[vertexOffset + 2] = positions[positionOffset + 2]
            vertices[vertexOffset + DemoMesh.NORMAL_OFFSET_FLOATS] = normals[positionOffset]
            vertices[vertexOffset + DemoMesh.NORMAL_OFFSET_FLOATS + 1] = normals[positionOffset + 1]
            vertices[vertexOffset + DemoMesh.NORMAL_OFFSET_FLOATS + 2] = normals[positionOffset + 2]
        }
        return DemoMesh(vertices, indices)
    }

    private fun areaVector(positions: FloatArray, first: Int, second: Int, third: Int): Vec3 =
        (position(positions, second) - position(positions, first)).cross(position(positions, third) - position(positions, first))

    private fun position(values: FloatArray, index: Int): Vec3 = Vec3(values[index * POSITION_COMPONENTS], values[index * POSITION_COMPONENTS + 1], values[index * POSITION_COMPONENTS + 2])

    private fun requireLine(reader: BufferedReader, record: String, index: Int): String =
        requireNotNull(reader.readLine()) { "PLY ended before $record $index" }

    private fun parseTokens(line: String, record: String, index: Int): List<String> {
        val tokens = line.trim().split(WHITESPACE).filter(String::isNotEmpty)
        require(tokens.isNotEmpty()) { "$record $index is empty" }
        return tokens
    }

    private fun headerTokens(line: String): List<String> = line.split(' ')

    private fun parsePositiveCount(token: String, label: String): Int = token.toIntOrNull()
        ?.takeIf { it in 1..MAX_ELEMENT_COUNT }
        ?: error("$label must be a positive Int no greater than $MAX_ELEMENT_COUNT: $token")

    private fun parseIndex(token: String, label: String): Int = token.toIntOrNull()
        ?.takeIf { it >= 0 }
        ?: error("$label must be a non-negative Int: $token")

    private fun parseFiniteFloat(token: String, label: String): Float = token.toFloatOrNull()
        ?.takeIf(Float::isFinite)
        ?: error("$label must be a finite float: $token")

    private data class PlyHeader(val vertexCount: Int, val faceCount: Int, val vertexProperties: List<String>, val hasNormals: Boolean)
    private data class FaceKey(val first: Int, val second: Int, val third: Int)
    private data class CompactedMesh(val positions: FloatArray, val indices: IntArray)
    private data class EdgeUse(val face: Int, val canonicalDirection: Boolean)
    private data class OrientationConstraint(val face: Int, val sameFlip: Boolean)

    private enum class HeaderState { BEFORE_ELEMENT, VERTEX, FACE }

    private data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
        fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z
        fun cross(other: Vec3): Vec3 = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
        fun lengthSquared(): Float = dot(this)
    }

    private const val ASCII_MAX: Int = 0x7f
    private const val POSITION_COMPONENTS: Int = 3
    private const val TARGET_MAX_EXTENT: Float = 2f
    private const val MIN_EXTENT: Float = 1e-7f
    private const val MIN_AREA_VECTOR_SQUARED: Float = 1e-14f
    private const val MIN_NORMAL_LENGTH: Float = 1e-7f
    private const val MAX_ELEMENT_COUNT: Int = 2_000_000
    private val WHITESPACE = Regex("\\s+")
    private val POSITION_PROPERTY_NAMES = setOf("x", "y", "z")
    private val NORMAL_PROPERTY_NAMES = setOf("nx", "ny", "nz")
    private val VERTEX_PROPERTY_NAMES = POSITION_PROPERTY_NAMES + NORMAL_PROPERTY_NAMES
}
