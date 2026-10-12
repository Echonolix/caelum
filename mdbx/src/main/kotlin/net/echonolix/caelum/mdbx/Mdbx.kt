package net.echonolix.caelum.mdbx

import net.echonolix.caelum.NativeLibraries
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.mdbx.functions.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/** Call [load] before using the generated low-level binding functions. */
public object Mdbx {
    public const val VERSION_NUMBER: UInt = 1404u
    @Volatile private var loaded = false

    /** Loads the bundled library and verifies the pinned upstream version. Idempotent. */
    @JvmStatic
    public fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            NativeLibraries.load(Mdbx::class.java, "caelum_mdbx")
            check(caelum_mdbx_version_number() == VERSION_NUMBER) { "libmdbx version does not match 0.14.4 bindings" }
            loaded = true
        }
    }
}

/** An upstream error, preserving the operation and numeric code for callers. */
public class MdbxException(public val operation: String, public val code: Int) :
    RuntimeException("$operation failed ($code): ${errorText(code)}")

private fun errorText(code: Int): String = Arena.ofConfined().use { arena ->
    val buffer = arena.allocate(1024)
    val text = mdbx_strerror_r(code, NPointer<NChar>(buffer.address()), buffer.byteSize())
    MemorySegment.ofAddress(text._address).reinterpret(1024).getString(0)
}

internal fun checkMdbx(operation: String, code: Int) {
    if (code != 0) throw MdbxException(operation, code)
}
