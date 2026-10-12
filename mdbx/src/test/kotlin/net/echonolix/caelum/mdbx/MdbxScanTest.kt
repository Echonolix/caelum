package net.echonolix.caelum.mdbx

import kotlin.test.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MdbxScanTest {
    @TempDir lateinit var directory: Path
    @Test fun binaryPrefixContinuationAndOwnedCopies() {
        val env = MdbxEnvironment.open(directory)
        env.begin().use { tx ->
            val table = tx.table("scan", true)
            for (key in listOf(byteArrayOf(0, -1), byteArrayOf(1), byteArrayOf(1, 0), byteArrayOf(1, -1), byteArrayOf(2)))
                table.put(key, key)
            tx.commit()
        }
        val entries = env.begin(true).use { tx ->
            val table = tx.table("scan")
            val first = table.scanPrefix(byteArrayOf(1), 2)
            assertEquals(2, first.size)
            assertContentEquals(byteArrayOf(1), first[0].key)
            assertContentEquals(byteArrayOf(1, 0), first[1].key)
            val last = table.scanPrefix(byteArrayOf(1), 2, first.last().key)
            assertEquals(1, last.size)
            assertContentEquals(byteArrayOf(1, -1), last[0].key)
            assertTrue(table.scanPrefix(byteArrayOf(1), 1, byteArrayOf(1, -1)).isEmpty())
            assertTrue(table.scanPrefix(byteArrayOf(9)).isEmpty())
            assertEquals(5, table.scanPrefix(byteArrayOf()).size)
            assertFailsWith<IllegalArgumentException> { table.scanPrefix(byteArrayOf(1), 0) }
            assertFailsWith<IllegalArgumentException> { table.scanPrefix(byteArrayOf(1), 2, byteArrayOf(2)) }
            first + last
        }
        env.close()
        entries.forEach { assertContentEquals(it.key, it.value) }
    }
    @Test fun copyLimitsRejectBeforeCopiesAndCursorClosesOnError() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { tx ->
                val table = tx.table("scan", true)
                table.put(byteArrayOf(1), ByteArray(65536))
                table.put(byteArrayOf(2, 0), byteArrayOf(3))
                tx.commit()
            }
            env.begin(true).use { tx ->
                val table = tx.table("scan")
                assertFailsWith<IllegalStateException> { table.get(byteArrayOf(1), 4096) }
                assertFailsWith<IllegalArgumentException> { table.get(byteArrayOf(1), -1) }
                assertEquals(65536, table.get(byteArrayOf(1))!!.size)
                assertFailsWith<IllegalStateException> { table.scanPrefix(byteArrayOf(1), 1, maxKeyBytes = 4, maxValueBytes = 4096, maxTotalBytes = 8192) }
                assertFailsWith<IllegalStateException> { table.scanPrefix(byteArrayOf(2), 1, maxKeyBytes = 1, maxValueBytes = 4, maxTotalBytes = 16) }
                assertFailsWith<IllegalStateException> { table.scanPrefix(byteArrayOf(2), 1, maxKeyBytes = 2, maxValueBytes = 4, maxTotalBytes = 2) }
                // All failures leave the txn and a subsequent cursor usable.
                assertEquals(1, table.scanPrefix(byteArrayOf(2), 1, maxKeyBytes = 2, maxValueBytes = 1, maxTotalBytes = 3).size)
                assertContentEquals(byteArrayOf(2, 0), table.scanPrefix(byteArrayOf(2), 1, byteArrayOf(2)).single().key)
            }
        }
    }
    @Test fun staleAndCrossThreadScansAreRejected() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { tx -> tx.table("scan", true); tx.commit() }
            val tx = env.begin(true); val table = tx.table("scan")
            var rejected = false
            val thread = Thread.ofPlatform().start {
                try { table.scanPrefix(byteArrayOf()) } catch (_: IllegalStateException) { rejected = true }
            }
            thread.join(); assertTrue(rejected)
            tx.close()
            assertFailsWith<IllegalStateException> { table.scanPrefix(byteArrayOf()) }
        }
    }
}
