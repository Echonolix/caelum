@file:Suppress("FunctionName")

package net.echonolix.caelum.sdl3

import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NUInt64
import net.echonolix.caelum.value

public data class SDLInlineFunction(public val name: String)

public object SDLInlineFunctions {
    @JvmField
    public val names: List<String> = listOf(
        "SDL_HasExactlyOneBitSet32",
        "SDL_MostSignificantBitIndex32",
        "SDL_PointInRect",
        "SDL_PointInRectFloat",
        "SDL_RectEmpty",
        "SDL_RectEmptyFloat",
        "SDL_RectToFRect",
        "SDL_RectsEqual",
        "SDL_RectsEqualEpsilon",
        "SDL_RectsEqualFloat",
        "SDL_SwapFloat",
        "SDL_size_add_check_overflow",
        "SDL_size_add_check_overflow_builtin",
        "SDL_size_mul_check_overflow",
        "SDL_size_mul_check_overflow_builtin",
    )

    @JvmField
    public val unsupported: List<SDLInlineFunction> = emptyList()
}

public fun SDL_size_mul_check_overflow(a: ULong, b: ULong, ret: NPointer<NUInt64>): Boolean {
    require(ret._address != 0L) { "ret must not be NULL" }
    if (a != 0uL && b > ULong.MAX_VALUE / a) return false
    ret.value = a * b
    return true
}

public fun SDL_size_mul_check_overflow_builtin(a: ULong, b: ULong, ret: NPointer<NUInt64>): Boolean =
    SDL_size_mul_check_overflow(a, b, ret)

public fun SDL_size_add_check_overflow(a: ULong, b: ULong, ret: NPointer<NUInt64>): Boolean {
    require(ret._address != 0L) { "ret must not be NULL" }
    if (b > ULong.MAX_VALUE - a) return false
    ret.value = a + b
    return true
}

public fun SDL_size_add_check_overflow_builtin(a: ULong, b: ULong, ret: NPointer<NUInt64>): Boolean =
    SDL_size_add_check_overflow(a, b, ret)

public fun SDL_SwapFloat(x: Float): Float = Float.fromBits(Integer.reverseBytes(x.toRawBits()))

public fun SDL_MostSignificantBitIndex32(x: UInt): Int = if (x == 0u) -1 else 31 - x.countLeadingZeroBits()

public fun SDL_HasExactlyOneBitSet32(x: UInt): Boolean = x != 0u && (x and (x - 1u)) == 0u

public fun SDL_RectToFRect(rect: NPointer<SDL_Rect>, frect: NPointer<SDL_FRect>): Unit {
    require(rect._address != 0L) { "rect must not be NULL" }
    require(frect._address != 0L) { "frect must not be NULL" }
    frect.x = rect.x.toFloat()
    frect.y = rect.y.toFloat()
    frect.w = rect.w.toFloat()
    frect.h = rect.h.toFloat()
}

public fun SDL_PointInRect(p: NPointer<SDL_Point>, r: NPointer<SDL_Rect>): Boolean =
    p._address != 0L && r._address != 0L &&
        p.x >= r.x && p.x < r.x + r.w &&
        p.y >= r.y && p.y < r.y + r.h

public fun SDL_RectEmpty(r: NPointer<SDL_Rect>): Boolean =
    r._address == 0L || r.w <= 0 || r.h <= 0

public fun SDL_RectsEqual(a: NPointer<SDL_Rect>, b: NPointer<SDL_Rect>): Boolean =
    a._address != 0L && b._address != 0L &&
        a.x == b.x && a.y == b.y && a.w == b.w && a.h == b.h

public fun SDL_PointInRectFloat(p: NPointer<SDL_FPoint>, r: NPointer<SDL_FRect>): Boolean =
    p._address != 0L && r._address != 0L &&
        p.x >= r.x && p.x <= r.x + r.w &&
        p.y >= r.y && p.y <= r.y + r.h

public fun SDL_RectEmptyFloat(r: NPointer<SDL_FRect>): Boolean =
    r._address == 0L || r.w < 0.0f || r.h < 0.0f

public fun SDL_RectsEqualEpsilon(a: NPointer<SDL_FRect>, b: NPointer<SDL_FRect>, epsilon: Float): Boolean =
    a._address != 0L && b._address != 0L &&
        (a._address == b._address ||
            (kotlin.math.abs(a.x - b.x) <= epsilon &&
                kotlin.math.abs(a.y - b.y) <= epsilon &&
                kotlin.math.abs(a.w - b.w) <= epsilon &&
                kotlin.math.abs(a.h - b.h) <= epsilon))

public fun SDL_RectsEqualFloat(a: NPointer<SDL_FRect>, b: NPointer<SDL_FRect>): Boolean =
    SDL_RectsEqualEpsilon(a, b, SDL_FLT_EPSILON)
