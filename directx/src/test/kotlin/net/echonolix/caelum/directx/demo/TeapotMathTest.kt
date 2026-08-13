package net.echonolix.caelum.directx.demo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TeapotMathTest {
    @Test
    fun `right-handed projection maps near and far planes to Direct3D depth range`() {
        val near = 0.1f
        val far = 40f
        val projection = Mat4.perspectiveRightHanded(
            fieldOfViewRadians = (42.0 * PI / 180.0).toFloat(),
            aspect = 16f / 9f,
            near = near,
            far = far,
        )

        val nearClip = Mat4.transform(projection, Vec4(0f, 0f, -near, 1f))
        val farClip = Mat4.transform(projection, Vec4(0f, 0f, -far, 1f))

        assertNear(0f, nearClip.z / nearClip.w)
        assertNear(1f, farClip.z / farClip.w)
    }

    @Test
    fun `view and model matrices compose in column-major order`() {
        val eye = Vec3(0.25f, 0.45f, 7.4f)
        val view = Mat4.lookAtRightHanded(eye, Vec3(0f, 0.15f, 0f), Vec3(0f, 1f, 0f))
        val eyeInViewSpace = Mat4.transform(view, Vec4(eye.x, eye.y, eye.z, 1f))
        assertNear(0f, eyeInViewSpace.x)
        assertNear(0f, eyeInViewSpace.y)
        assertNear(0f, eyeInViewSpace.z)
        assertNear(1f, eyeInViewSpace.w)

        val rotation = Mat4.rotationY((PI / 2.0).toFloat())
        val rotated = Mat4.transform(rotation, Vec4(1f, 0f, 0f, 1f))
        assertNear(0f, rotated.x)
        assertNear(0f, rotated.y)
        assertNear(-1f, rotated.z)
        assertNear(1f, rotated.w)

        val composed = Mat4.multiply(view, rotation)
        val sequential = Mat4.transform(view, rotated)
        val directlyComposed = Mat4.transform(composed, Vec4(1f, 0f, 0f, 1f))
        assertVec4Near(sequential, directlyComposed)
    }

    @Test
    fun `transpose is its own inverse`() {
        val matrix = Mat4.multiply(
            Mat4.lookAtRightHanded(Vec3(1f, 2f, 5f), Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f)),
            Mat4.rotationY(0.37f),
        )

        assertContentEquals(matrix, Mat4.transpose(Mat4.transpose(matrix)))
    }

    private fun assertVec4Near(expected: Vec4, actual: Vec4) {
        assertNear(expected.x, actual.x)
        assertNear(expected.y, actual.y)
        assertNear(expected.z, actual.z)
        assertNear(expected.w, actual.w)
    }

    private fun assertNear(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 1e-5f, "expected $expected but was $actual")
    }
}
