package net.echonolix.caelum.mdbx

import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.mdbx.functions.*
import net.echonolix.caelum.mdbx.consts.*
import net.echonolix.caelum.mdbx.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import java.nio.file.Path

/**
 * Owns a real libmdbx environment. Transactions may be created on different threads,
 * but each transaction must stay on its creating thread. Close refuses active
 * transactions, including an in-flight begin, so a map cannot be freed while in use.
 * Native low-level handles are intentionally not exposed by this ownership layer.
 */
public class MdbxEnvironment private constructor(private val handle: NPointer<MDBX_env>) : AutoCloseable {
    private var closed = false
    private var active = 0

    /** Starts a read snapshot, or a writer. libmdbx serializes writers. */
    public fun begin(readOnly: Boolean = false): MdbxTransaction {
        synchronized(this) {
            check(!closed) { "Environment is closed" }
            active++
        }
        try {
            return Arena.ofConfined().use { arena ->
                val output = arena.allocate(ValueLayout.ADDRESS)
                checkMdbx("mdbx_txn_begin_ex", mdbx_txn_begin_ex(
                    handle, NPointer(0), if (readOnly) MDBX_RDONLY.toUInt() else 0u,
                    NPointer(output.address()), NPointer<NChar>(0),
                ))
                MdbxTransaction(this, NPointer(output.get(ValueLayout.ADDRESS, 0).address()), readOnly)
            }
        } catch (failure: Throwable) {
            released()
            throw failure
        }
    }

    internal fun released() {
        synchronized(this) { active-- }
    }

    /** Syncs and frees the environment; idempotent after successful close. */
    override fun close() {
        synchronized(this) {
            if (closed) return
            check(active == 0) { "Cannot close environment with $active active transaction(s)" }
            val code = mdbx_env_close_ex(handle, 0u)
            // Only MDBX_BUSY leaves the handle alive, according to upstream.
            if (code != MDBX_BUSY) closed = true
            checkMdbx("mdbx_env_close_ex", code)
        }
    }

    public companion object {
        /**
         * Opens an existing directory (no implicit directory creation). Uses durable
         * defaults and bounds file growth by [maxSizeBytes]. This is a geometry limit,
         * not an application quota or eviction policy. [maxTables] counts named tables.
         */
        @JvmStatic
        public fun open(path: Path, maxSizeBytes: Long = 1L shl 30, maxTables: Int = 16): MdbxEnvironment {
            require(maxSizeBytes >= 1L shl 20) { "maxSizeBytes must be at least 1 MiB" }
            require(maxTables in 1..32765) { "maxTables must be in 1..32765" }
            val pathname = path.toAbsolutePath().toString()
            require('\u0000' !in pathname) { "Path contains NUL" }
            Mdbx.load()
            return Arena.ofConfined().use { arena ->
                val output = arena.allocate(ValueLayout.ADDRESS)
                checkMdbx("mdbx_env_create", mdbx_env_create(NPointer(output.address())))
                val handle = NPointer<MDBX_env>(output.get(ValueLayout.ADDRESS, 0).address())
                try {
                    checkMdbx("mdbx_env_set_option", mdbx_env_set_option(handle, 0, maxTables.toULong()))
                    checkMdbx("mdbx_env_set_geometry", mdbx_env_set_geometry(
                        handle, -1, -1, maxSizeBytes, -1, -1, -1,
                    ))
                    val pathBytes = arena.allocateFrom(pathname)
                    checkMdbx("mdbx_env_open", caelum_mdbx_env_open(handle, NPointer<NChar>(pathBytes.address()), 0u, 0x1a4u))
                    MdbxEnvironment(handle)
                } catch (failure: Throwable) {
                    try { checkMdbx("mdbx_env_close_ex", mdbx_env_close_ex(handle, 0u)) }
                    catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                    throw failure
                }
            }
        }
    }
}
