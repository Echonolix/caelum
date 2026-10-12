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
 * but each transaction must stay on its creating platform thread. Close refuses active
 * transactions, including an in-flight begin, so a map cannot be freed while in use.
 * Native low-level handles are intentionally not exposed by this ownership layer.
 */
public class MdbxEnvironment private constructor(private val handle: NPointer<MDBX_env>, private val maxSizeBytes: Long) : AutoCloseable {
    private var closed = false
    private var active = 0

    /** Starts a read snapshot, or a writer. libmdbx serializes writers. */
    public fun begin(readOnly: Boolean = false): MdbxTransaction {
        check(!Thread.currentThread().isVirtual) { "libmdbx transactions require a platform thread" }
        synchronized(this) {
            check(!closed) { "Environment is closed" }
            active++
        }
        var pending: NPointer<MDBX_txn>? = null
        try {
            return Arena.ofConfined().use { arena ->
                val output = arena.allocate(ValueLayout.ADDRESS)
                checkMdbx("mdbx_txn_begin_ex", mdbx_txn_begin_ex(
                    handle, NPointer(0), if (readOnly) MDBX_RDONLY.toUInt() else 0u,
                    NPointer(output.address()), NPointer<NChar>(0),
                ))
                val transactionHandle = NPointer<MDBX_txn>(output.get(ValueLayout.ADDRESS, 0).address())
                pending = transactionHandle
                MdbxTransaction(this, transactionHandle, readOnly)
            }.also { pending = null }
        } catch (failure: Throwable) {
            var releaseReservation = pending == null
            pending?.let { transactionHandle ->
                try {
                    val code = mdbx_txn_abort_ex(transactionHandle, NPointer(0))
                    releaseReservation = code != MDBX_THREAD_MISMATCH
                    checkMdbx("mdbx_txn_abort_ex", code)
                } catch (cleanup: Throwable) {
                    failure.addSuppressed(cleanup)
                }
            }
            if (releaseReservation) released()
            throw failure
        }
    }

    internal fun released() {
        synchronized(this) { active-- }
    }

    /**
     * Changes the current file size at a quiescent boundary, preserving upper
     * bound, page size, growth and shrink settings. libmdbx rounds/clamps to its
     * minimum and live MVCC pages; a requested size is not an exact file size.
     * No transaction or pending begin may exist; this method does not change the
     * reserved maximum mapping size.
     */
    public fun resize(sizeBytes: Long) {
        require(sizeBytes in 0..maxSizeBytes) { "sizeBytes must be between 0 and maxSizeBytes" }
        synchronized(this) {
            check(!closed) { "Environment is closed" }
            check(active == 0) { "Cannot resize environment with $active active transaction(s)" }
            checkMdbx("mdbx_env_set_geometry", mdbx_env_set_geometry(handle, -1, sizeBytes, -1, -1, -1, -1))
        }
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
         * [geometry] is applied before open; an existing database must retain its page size.
         */
        @JvmStatic
        public fun open(
            path: Path,
            maxSizeBytes: Long = 1L shl 30,
            maxTables: Int = 16,
            geometry: MdbxGeometry = MdbxGeometry(),
        ): MdbxEnvironment {
            require(maxSizeBytes >= 1L shl 20) { "maxSizeBytes must be at least 1 MiB" }
            require(maxTables in 1..32765) { "maxTables must be in 1..32765" }
            require(geometry.minSizeBytes <= maxSizeBytes && geometry.initialSizeBytes <= maxSizeBytes &&
                geometry.growthStepBytes <= maxSizeBytes && geometry.shrinkThresholdBytes <= maxSizeBytes) {
                "Geometry size exceeds maxSizeBytes"
            }
            require(geometry.minSizeBytes < 0 || geometry.initialSizeBytes <= 0 || geometry.minSizeBytes <= geometry.initialSizeBytes) {
                "minSizeBytes exceeds initialSizeBytes"
            }
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
                        handle, geometry.minSizeBytes, geometry.initialSizeBytes, maxSizeBytes,
                        geometry.growthStepBytes, geometry.shrinkThresholdBytes, geometry.pageSizeBytes,
                    ))
                    val pathBytes = arena.allocateFrom(pathname)
                    checkMdbx("mdbx_env_open", caelum_mdbx_env_open(handle, NPointer<NChar>(pathBytes.address()), 0u, 0x1a4u))
                    MdbxEnvironment(handle, maxSizeBytes)
                } catch (failure: Throwable) {
                    try { checkMdbx("mdbx_env_close_ex", mdbx_env_close_ex(handle, 0u)) }
                    catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                    throw failure
                }
            }
        }
    }
}
