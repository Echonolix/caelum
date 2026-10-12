package net.echonolix.caelum.mdbx

import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.mdbx.functions.*
import net.echonolix.caelum.mdbx.consts.*
import net.echonolix.caelum.mdbx.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** Thread-confined transaction. Closing an unfinished writer rolls it back. */
public class MdbxTransaction internal constructor(
    private val environment: MdbxEnvironment,
    private val handle: NPointer<MDBX_txn>,
    public val readOnly: Boolean,
) : AutoCloseable {
    private val owner = Thread.currentThread()
    private var finished = false

    internal fun ensureActive(write: Boolean = false) {
        check(Thread.currentThread() === owner) { "Transaction belongs to another thread" }
        check(!finished) { "Transaction is finished" }
        check(!write || !readOnly) { "Cannot write in a read-only transaction" }
    }

    /** Returns a transaction-scoped table; null denotes the unnamed table. */
    public fun table(name: String? = null, create: Boolean = false): MdbxTable {
        ensureActive(create)
        require(name == null || '\u0000' !in name) { "Table name contains NUL" }
        return Arena.ofConfined().use { arena ->
            val output = arena.allocate(ValueLayout.JAVA_INT)
            val nativeName = NPointer<NChar>(name?.let { arena.allocateFrom(it).address() } ?: 0)
            checkMdbx("mdbx_dbi_open", mdbx_dbi_open(handle, nativeName, if (create) MDBX_CREATE.toUInt() else 0u, NPointer(output.address())))
            MdbxTable(this, output.get(ValueLayout.JAVA_INT, 0).toUInt())
        }
    }

    /** Commits and consumes this handle, including on native commit failure. */
    public fun commit() {
        ensureActive()
        finish("mdbx_txn_commit_ex", mdbx_txn_commit_ex(handle, NPointer(0)))
    }

    /** Aborts and consumes this handle; idempotent after commit/abort. */
    override fun close() {
        check(Thread.currentThread() === owner) { "Transaction belongs to another thread" }
        if (finished) return
        finish("mdbx_txn_abort_ex", mdbx_txn_abort_ex(handle, NPointer(0)))
    }

    private fun finish(operation: String, code: Int) {
        // Upstream retains the handle only for THREAD_MISMATCH; never abort an
        // already-consumed handle on another failure.
        if (code != MDBX_THREAD_MISMATCH) {
            finished = true
            environment.released()
        }
        checkMdbx(operation, code)
    }

    internal fun get(dbi: UInt, key: ByteArray): ByteArray? {
        ensureActive()
        return Arena.ofConfined().use { arena ->
            val nativeKey = value(arena, key)
            val data = arena.allocate(MDBX_val.layout)
            val result = mdbx_get(handle, dbi, nativeKey, NPointer(data.address()))
            if (result == MDBX_NOTFOUND) return null
            checkMdbx("mdbx_get", result)
            val pointer = NPointer<MDBX_val>(data.address())
            val size = pointer.iov_len
            check(size in 0..Int.MAX_VALUE.toLong()) { "Value too large for ByteArray: $size" }
            // Copy before any update or txn termination can invalidate mmap data.
            if (size == 0L) ByteArray(0) else MemorySegment.ofAddress(pointer.iov_base._address)
                .reinterpret(size).toArray(ValueLayout.JAVA_BYTE)
        }
    }

    internal fun put(dbi: UInt, key: ByteArray, data: ByteArray, noOverwrite: Boolean) {
        ensureActive(write = true)
        Arena.ofConfined().use { arena ->
            checkMdbx("mdbx_put", mdbx_put(handle, dbi, value(arena, key), value(arena, data), if (noOverwrite) MDBX_NOOVERWRITE.toUInt() else 0u))
        }
    }

    internal fun delete(dbi: UInt, key: ByteArray): Boolean {
        ensureActive(write = true)
        return Arena.ofConfined().use { arena ->
            val result = mdbx_del(handle, dbi, value(arena, key), NPointer(0))
            if (result == MDBX_NOTFOUND) false else {
                checkMdbx("mdbx_del", result)
                true
            }
        }
    }

    private fun value(arena: Arena, bytes: ByteArray): NPointer<MDBX_val> {
        val storage = arena.allocate(bytes.size.toLong().coerceAtLeast(1))
        storage.asSlice(0, bytes.size.toLong()).copyFrom(MemorySegment.ofArray(bytes))
        val result = NPointer<MDBX_val>(arena.allocate(MDBX_val.layout).address())
        result.iov_base = NPointer<NChar>(storage.address())
        result.iov_len = bytes.size.toLong()
        return result
    }
}

/** Valid only while its owning transaction is active. Returned values are owned copies. */
public class MdbxTable internal constructor(private val transaction: MdbxTransaction, private val dbi: UInt) {
    /** Missing keys return null; empty values return an empty array. */
    public fun get(key: ByteArray): ByteArray? = transaction.get(dbi, key)
    public fun put(key: ByteArray, value: ByteArray, noOverwrite: Boolean = false): Unit =
        transaction.put(dbi, key, value, noOverwrite)
    public fun delete(key: ByteArray): Boolean = transaction.delete(dbi, key)
}
