package net.echonolix.caelum.bc7e

import net.echonolix.caelum.NativeLibraries
import net.echonolix.caelum.bc7e.functions.bc7e_compress_block_init

/**
 * Entry point for the bc7e.ispc BC7 encoder bindings. Call [load] once before using
 * any function in [net.echonolix.caelum.bc7e.functions]; it loads the native library
 * and builds the encoder's shared tables.
 *
 * The native library contains SSE2, SSE4, AVX2 and AVX-512 (SKX) variants and picks
 * one at runtime. `bc7e_compress_blocks` is thread-safe after [load].
 */
public object Bc7e {
    /** Bytes per encoded 4x4 block. */
    public const val BLOCK_BYTES: Int = 16

    /** Bytes of RGBA8 input per 4x4 block. */
    public const val BLOCK_PIXEL_BYTES: Int = 64

    @Volatile
    private var loaded = false

    /** Loads the packaged native library and initializes the encoder. Idempotent. */
    @JvmStatic
    public fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            NativeLibraries.load(Bc7e::class.java, "caelum_bc7e")
            bc7e_compress_block_init()
            loaded = true
        }
    }
}
