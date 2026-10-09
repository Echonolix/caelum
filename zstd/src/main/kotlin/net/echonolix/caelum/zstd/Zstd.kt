package net.echonolix.caelum.zstd

import net.echonolix.caelum.NativeLibraries
import net.echonolix.caelum.zstd.functions.ZSTD_versionNumber

/**
 * Entry point for the zstd bindings. Call [load] once before using any function in
 * [net.echonolix.caelum.zstd.functions]; the generated bindings resolve their symbols
 * when first touched.
 */
public object Zstd {
    /** Library version the bindings were generated from (major * 10000 + minor * 100 + release). */
    public const val VERSION_NUMBER: Int = 10507

    /** `ZSTD_CONTENTSIZE_UNKNOWN`, returned by `ZSTD_getFrameContentSize` as an unsigned 64-bit value. */
    public const val CONTENTSIZE_UNKNOWN: Long = -1L

    /** `ZSTD_CONTENTSIZE_ERROR`, returned by `ZSTD_getFrameContentSize` as an unsigned 64-bit value. */
    public const val CONTENTSIZE_ERROR: Long = -2L

    @Volatile
    private var loaded = false

    /** Extracts and loads the packaged native library and checks its version. Idempotent. */
    @JvmStatic
    public fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            NativeLibraries.load(Zstd::class.java, "caelum_zstd")
            val version = ZSTD_versionNumber().toInt()
            check(version == VERSION_NUMBER) {
                "caelum_zstd native version ${version} does not match bindings version ${VERSION_NUMBER}"
            }
            loaded = true
        }
    }
}
