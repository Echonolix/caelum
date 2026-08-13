package net.echonolix.caelum.directx.demo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Column-major matrices for column-vector multiplication (`matrix * vector`).
 * The perspective projection is right-handed and maps depth to Direct3D's
 * normalized `[0, 1]` range.
 */
internal object Mat4 {
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    fun rotationY(angleRadians: Float): FloatArray {
        require(angleRadians.isFinite()) { "angleRadians must be finite" }
        val cosine = cos(angleRadians)
        val sine = sin(angleRadians)
        return floatArrayOf(
            cosine, 0f, -sine, 0f,
            0f, 1f, 0f, 0f,
            sine, 0f, cosine, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun perspectiveRightHanded(
        fieldOfViewRadians: Float,
        aspect: Float,
        near: Float,
        far: Float,
    ): FloatArray {
        require(fieldOfViewRadians.isFinite() && fieldOfViewRadians > 0f && fieldOfViewRadians < PI.toFloat()) {
            "fieldOfViewRadians must be finite and between zero and PI"
        }
        require(aspect.isFinite() && aspect > 0f) { "aspect must be finite and positive" }
        require(near.isFinite() && near > 0f) { "near must be finite and positive" }
        require(far.isFinite() && far > near) { "far must be finite and greater than near" }

        val focalLength = 1f / tan(fieldOfViewRadians / 2f)
        return floatArrayOf(
            focalLength / aspect, 0f, 0f, 0f,
            0f, focalLength, 0f, 0f,
            0f, 0f, far / (near - far), -1f,
            0f, 0f, near * far / (near - far), 0f,
        )
    }

    fun lookAtRightHanded(eye: Vec3, center: Vec3, up: Vec3): FloatArray {
        val forward = (center - eye).normalized()
        val side = forward.cross(up).normalized()
        val correctedUp = side.cross(forward)
        return floatArrayOf(
            side.x, correctedUp.x, -forward.x, 0f,
            side.y, correctedUp.y, -forward.y, 0f,
            side.z, correctedUp.z, -forward.z, 0f,
            -side.dot(eye), -correctedUp.dot(eye), forward.dot(eye), 1f,
        )
    }

    fun multiply(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == MATRIX_COMPONENTS && right.size == MATRIX_COMPONENTS) {
            "Both matrices must contain exactly 16 components"
        }
        return FloatArray(MATRIX_COMPONENTS).also { result ->
            for (column in 0 until MATRIX_DIMENSION) {
                for (row in 0 until MATRIX_DIMENSION) {
                    var value = 0f
                    for (index in 0 until MATRIX_DIMENSION) {
                        value += left[index * MATRIX_DIMENSION + row] *
                            right[column * MATRIX_DIMENSION + index]
                    }
                    result[column * MATRIX_DIMENSION + row] = value
                }
            }
        }
    }

    fun transform(matrix: FloatArray, vector: Vec4): Vec4 {
        require(matrix.size == MATRIX_COMPONENTS) { "Matrix must contain exactly 16 components" }
        return Vec4(
            x = matrix[0] * vector.x + matrix[4] * vector.y + matrix[8] * vector.z + matrix[12] * vector.w,
            y = matrix[1] * vector.x + matrix[5] * vector.y + matrix[9] * vector.z + matrix[13] * vector.w,
            z = matrix[2] * vector.x + matrix[6] * vector.y + matrix[10] * vector.z + matrix[14] * vector.w,
            w = matrix[3] * vector.x + matrix[7] * vector.y + matrix[11] * vector.z + matrix[15] * vector.w,
        )
    }

    fun transpose(matrix: FloatArray): FloatArray {
        require(matrix.size == MATRIX_COMPONENTS) { "Matrix must contain exactly 16 components" }
        return FloatArray(MATRIX_COMPONENTS) { index ->
            val column = index / MATRIX_DIMENSION
            val row = index % MATRIX_DIMENSION
            matrix[row * MATRIX_DIMENSION + column]
        }
    }

    private const val MATRIX_DIMENSION: Int = 4
    private const val MATRIX_COMPONENTS: Int = MATRIX_DIMENSION * MATRIX_DIMENSION
}

internal data class Vec4(val x: Float, val y: Float, val z: Float, val w: Float)
