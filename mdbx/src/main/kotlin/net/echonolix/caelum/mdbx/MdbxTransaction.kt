package net.echonolix.caelum.mdbx

import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.mdbx.functions.*
import net.echonolix.caelum.mdbx.consts.*
import net.echonolix.caelum.mdbx.structs.*
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** Platform-thread-confined transaction. Closing an unfinished writer rolls it back. */
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

    /** Native transaction snapshot high-water used pages, including MVCC/GC retained space.
     * Does not scan the reader table or estimate bytes from values/file length. */
    public fun usage(): MdbxUsage {
        ensureActive()
        return Arena.ofConfined().use { arena ->
            val used = arena.allocate(ValueLayout.JAVA_LONG)
            val file = arena.allocate(ValueLayout.JAVA_LONG)
            val page = arena.allocate(ValueLayout.JAVA_INT)
            checkMdbx("caelum_mdbx_usage", caelum_mdbx_usage(handle,
                NPointer(used.address()), NPointer(file.address()), NPointer(page.address())))
            MdbxUsage(used.get(ValueLayout.JAVA_LONG, 0), file.get(ValueLayout.JAVA_LONG, 0), page.get(ValueLayout.JAVA_INT, 0))
        }
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

    internal fun get(dbi: UInt, key: ByteArray, maxValueBytes: Int): ByteArray? {
        ensureActive()
        require(maxValueBytes >= 0)
        return Arena.ofConfined().use { arena ->
            val nativeKey = value(arena, key)
            val data = arena.allocate(MDBX_val.layout)
            val result = mdbx_get(handle, dbi, nativeKey, NPointer(data.address()))
            if (result == MDBX_NOTFOUND) return null
            checkMdbx("mdbx_get", result)
            val pointer = NPointer<MDBX_val>(data.address())
            val size = pointer.iov_len
            check(size in 0..maxValueBytes.toLong()) { "Value exceeds copy limit $maxValueBytes: $size" }
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

    internal fun scanPrefix(dbi: UInt, prefix: ByteArray, limit: Int, after: ByteArray?,
                            maxKeyBytes: Int, maxValueBytes: Int, maxTotalBytes: Long): List<MdbxEntry> {
        ensureActive()
        require(maxKeyBytes >= 0 && maxValueBytes >= 0 && maxTotalBytes >= 0)
        require(prefix.size <= maxKeyBytes && (after == null || after.size <= maxKeyBytes))
        require(limit in 1..4096) { "Scan limit must be in 1..4096" }
        require(after == null || after.size >= prefix.size && prefix.indices.all { after[it] == prefix[it] }) {
            "Continuation must belong to the requested prefix"
        }
        return Arena.ofConfined().use { arena ->
            val output = arena.allocate(ValueLayout.ADDRESS)
            checkMdbx("mdbx_cursor_open", mdbx_cursor_open(handle, dbi, NPointer(output.address())))
            val cursor = NPointer<MDBX_cursor>(output.get(ValueLayout.ADDRESS, 0).address())
            try {
                val key = value(arena, after ?: prefix)
                val data = NPointer<MDBX_val>(arena.allocate(MDBX_val.layout).address())
                var operation = if (prefix.isEmpty() && after == null) MDBX_FIRST else MDBX_SET_RANGE
                val entries = ArrayList<MdbxEntry>()
                var copiedBytes = 0L
                while (entries.size < limit) {
                    val code = mdbx_cursor_get(cursor, key, data, operation)
                    if (code == MDBX_NOTFOUND) break
                    checkMdbx("mdbx_cursor_get", code)
                    operation = MDBX_NEXT
                    check(key.iov_len in 0..maxKeyBytes.toLong()) { "Key exceeds copy limit $maxKeyBytes: ${key.iov_len}" }
                    check(key.iov_len <= maxTotalBytes - copiedBytes) { "Scan exceeds total copy limit $maxTotalBytes" }
                    val copiedKey = copyValue(key)
                    if (copiedKey.size < prefix.size || prefix.indices.any { copiedKey[it] != prefix[it] }) break
                    if (after != null && copiedKey.contentEquals(after)) continue
                    check(data.iov_len in 0..maxValueBytes.toLong()) { "Value exceeds copy limit $maxValueBytes: ${data.iov_len}" }
                    val entryBytes = key.iov_len + data.iov_len
                    check(entryBytes <= maxTotalBytes - copiedBytes) { "Scan exceeds total copy limit $maxTotalBytes" }
                    entries.add(MdbxEntry(copiedKey, copyValue(data)))
                    copiedBytes += entryBytes
                }
                entries
            } finally { mdbx_cursor_close(cursor) }
        }
    }

    private fun copyValue(value: NPointer<MDBX_val>): ByteArray {
        val size = value.iov_len
        check(size in 0..Int.MAX_VALUE.toLong()) { "Value too large for ByteArray: $size" }
        return if (size == 0L) ByteArray(0) else MemorySegment.ofAddress(value.iov_base._address)
            .reinterpret(size).toArray(ValueLayout.JAVA_BYTE)
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
    public fun get(key: ByteArray): ByteArray? = transaction.get(dbi, key, Int.MAX_VALUE)
    /** Rejects a larger stored value before allocating/copying any value bytes. */
    public fun get(key: ByteArray, maxValueBytes: Int): ByteArray? = transaction.get(dbi, key, maxValueBytes)
    public fun put(key: ByteArray, value: ByteArray, noOverwrite: Boolean = false): Unit =
        transaction.put(dbi, key, value, noOverwrite)
    public fun delete(key: ByteArray): Boolean = transaction.delete(dbi, key)
    /** Binary lexicographic prefix scan; exclusive continuation, bounded count, no borrowed mmap views.
     * Intended for ordinary non-DUPSORT/non-integer-key tables, as created by [MdbxTransaction.table].
     */
    public fun scanPrefix(prefix: ByteArray, limit: Int = 256, after: ByteArray? = null): List<MdbxEntry> =
        transaction.scanPrefix(dbi, prefix, limit, after, Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE)
    /** Copy limits are checked on native lengths before key/value allocation. On limit failure the
     * entire scan throws and closes its cursor; use a smaller count limit for a smaller batch.
     */
    public fun scanPrefix(prefix: ByteArray, limit: Int = 256, after: ByteArray? = null,
                          maxKeyBytes: Int, maxValueBytes: Int, maxTotalBytes: Long): List<MdbxEntry> =
        transaction.scanPrefix(dbi, prefix, limit, after, maxKeyBytes, maxValueBytes, maxTotalBytes)
}

/** Owned copies; valid after cursor/transaction/environment close. */
public data class MdbxEntry(public val key: ByteArray, public val value: ByteArray)

/** Upstream txn_space_used is the extent through the last used database page.
 * Deleted pages can remain here until native GC/tail reclamation; this is not live payload size. */
public data class MdbxUsage(public val usedBytes: Long, public val fileBytes: Long, public val pageSize: Int) {
    init { require(pageSize > 0 && usedBytes >= 0 && usedBytes % pageSize == 0L && fileBytes >= usedBytes) }
    public val usedPages: Long get() = usedBytes / pageSize
}
