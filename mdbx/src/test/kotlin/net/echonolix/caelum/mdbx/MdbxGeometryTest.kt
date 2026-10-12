package net.echonolix.caelum.mdbx

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

/** Small real-database lifecycle checks, not a stress or throughput benchmark. */
class MdbxGeometryTest {
    @TempDir lateinit var directory: Path
    private val payload = ByteArray(65537) { ((it * 31) xor (it ushr 7)).toByte() }
    private val geometry = MdbxGeometry(
        minSizeBytes = 65536, initialSizeBytes = 65536,
        growthStepBytes = 262144, shrinkThresholdBytes = 524288, pageSizeBytes = 4096,
    )
    private fun key(index: Int) = byteArrayOf(index.toByte())
    private fun size() = Files.size(directory.resolve("mdbx.dat"))

    @Test fun growthAndShrinkPreserveActiveSnapshotsAndReopen() {
        var grown: Long
        var retained: Long
        var shrunk: Long
        MdbxEnvironment.open(directory, maxSizeBytes = 16L shl 20, geometry = geometry).use { env ->
            val initial = size()
            env.begin().use { it.table().put(key(0), payload); it.commit() }
            // Hold a snapshot while another platform thread grows the actual file.
            env.begin(true).use { reader ->
                Executors.newSingleThreadExecutor().use { writer ->
                    writer.submit {
                        env.begin().use { txn ->
                            val table = txn.table()
                            for (i in 1..31) table.put(key(i), payload)
                            txn.commit()
                        }
                    }.get(5, SECONDS)
                }
                grown = size()
                assertTrue(grown > initial, "File did not grow: $initial -> $grown")
                assertTrue(grown <= 16L shl 20)
                assertContentEquals(payload, reader.table().get(key(0)))
                assertNull(reader.table().get(key(31)))
            }
            // This newer snapshot retains the full dataset while it is deleted.
            env.begin(true).use { reader ->
                Executors.newSingleThreadExecutor().use { writer ->
                    writer.submit {
                        env.begin().use { txn ->
                            val table = txn.table()
                            for (i in 0..31) assertTrue(table.delete(key(i)))
                            table.put(key(100), byteArrayOf(7))
                            txn.commit()
                        }
                    }.get(5, SECONDS)
                }
                retained = size()
                for (i in 0..31) assertContentEquals(payload, reader.table().get(key(i)))
                assertNull(reader.table().get(key(100)))
            }
            // Advance the few MVCC generations required to reclaim old pages.
            repeat(4) {
                env.begin().use { txn -> txn.table().put(key(100), byteArrayOf((8 + it).toByte())); txn.commit() }
            }
            // Delete does not guarantee immediate tail compaction. Explicit
            // quiescent resize clamps to pages still required by native MVCC/GC.
            env.resize(0)
            shrunk = size()
            println("MDBX file bytes initial=$initial grown=$grown retainedByReader=$retained shrunk=$shrunk")
            assertTrue(shrunk < retained, "Quiescent resize did not shrink the file: $retained -> $shrunk")
            assertTrue(shrunk >= geometry.minSizeBytes)
            env.begin(true).use { reader ->
                for (i in 0..31) assertNull(reader.table().get(key(i)))
                assertContentEquals(byteArrayOf(11), reader.table().get(key(100)))
            }
        }
        MdbxEnvironment.open(directory, maxSizeBytes = 16L shl 20, geometry = geometry).use { env ->
            assertTrue(size() <= shrunk)
            env.begin(true).use { reader ->
                assertContentEquals(byteArrayOf(11), reader.table().get(key(100)))
                for (i in 0..31) assertNull(reader.table().get(key(i)))
            }
        }
    }

    @Test fun automaticShrinkOfTemporaryTailPreservesConcurrentReader() {
        MdbxEnvironment.open(directory, maxSizeBytes = 8L shl 20, geometry = geometry).use { env ->
            env.begin().use { txn -> txn.table().put(key(0), payload); txn.commit() }
            env.begin(true).use { reader ->
                val expanded = Executors.newSingleThreadExecutor().use { writer ->
                    writer.submit(Callable {
                        env.begin().use { txn ->
                            val table = txn.table()
                            table.put(key(1), ByteArray(4 shl 20))
                            val expanded = size()
                            assertTrue(table.delete(key(1)))
                            table.put(key(100), byteArrayOf(7))
                            txn.commit()
                            expanded
                        }
                    }).get(5, SECONDS)
                }
                assertTrue(size() < expanded, "Temporary tail did not shrink on commit: $expanded -> ${size()}")
                assertContentEquals(payload, reader.table().get(key(0)))
                assertNull(reader.table().get(key(100)))
                println("MDBX automatic file bytes expanded=$expanded committed=${size()}")
            }
            env.begin(true).use { txn ->
                assertContentEquals(payload, txn.table().get(key(0)))
                assertContentEquals(byteArrayOf(7), txn.table().get(key(100)))
                assertNull(txn.table().get(key(1)))
            }
        }
    }

    @Test fun abortedGrowthPreservesDataAndCanBeShrunkAtQuiescentBoundary() {
        var expanded = 0L
        MdbxEnvironment.open(directory, maxSizeBytes = 8L shl 20, geometry = geometry).use { env ->
            env.begin().use { txn -> txn.table().put(key(100), byteArrayOf(7)); txn.commit() }
            val initial = size()
            env.begin().use { txn ->
                txn.table().put(key(1), ByteArray(4 shl 20) { it.toByte() })
                expanded = size()
                assertTrue(expanded > initial)
            }
            env.begin().use { txn ->
                assertNull(txn.table().get(key(1)))
                assertContentEquals(byteArrayOf(7), txn.table().get(key(100)))
                txn.table().put(key(100), byteArrayOf(8))
                txn.commit()
            }
            assertTrue(size() <= expanded)
            // The following commit preserves the pre-abort logical geometry; this
            // build keeps the larger physical file until reopen reapplies geometry.
            println("MDBX aborted file bytes initial=$initial expanded=$expanded afterCommit=${size()}")
        }
        val afterClose = size()
        MdbxEnvironment.open(directory, maxSizeBytes = 8L shl 20, geometry = geometry).use { env ->
            val reopened = size()
            env.resize(65536)
            assertTrue(size() < expanded, "Explicit shrink did not reclaim aborted growth")
            println("MDBX aborted file bytes afterClose=$afterClose reopened=$reopened explicitlyShrunk=${size()}")
            env.begin(true).use { txn ->
                assertNull(txn.table().get(key(1)))
                assertContentEquals(byteArrayOf(8), txn.table().get(key(100)))
            }
        }
    }

    @Test fun queuedWriterDoesNotBlockLifecycleMonitorOrLoseUpdates() {
        MdbxEnvironment.open(directory).use { env ->
            Executors.newFixedThreadPool(2).use { workers ->
                val started = CountDownLatch(1)
                val first = env.begin()
                val second = workers.submit {
                    started.countDown()
                    env.begin().use { txn -> txn.table().put(key(2), byteArrayOf(2)); txn.commit() }
                }
                try {
                    first.table().put(key(1), byteArrayOf(1))
                    assertTrue(started.await(5, SECONDS))
                    assertFalse(second.isDone, "Second writer completed before first writer released")
                    workers.submit(Callable {
                        assertFailsWith<IllegalStateException> { env.close() }
                    }).get(5, SECONDS)
                    first.commit()
                } finally {
                    first.close()
                }
                second.get(5, SECONDS)
            }
            env.begin(true).use { reader ->
                assertContentEquals(byteArrayOf(1), reader.table().get(key(1)))
                assertContentEquals(byteArrayOf(2), reader.table().get(key(2)))
            }
        }
    }

    @Test fun failedBeginReleasesReservationAndOwnerTransactionRemainsUsable() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                assertFailsWith<IllegalStateException> { env.resize(65536) }
                val error = assertFailsWith<MdbxException> { env.begin() }
                assertEquals("mdbx_txn_begin_ex", error.operation)
                txn.table().put(key(1), payload)
                txn.commit()
            }
            env.begin(true).use { reader ->
                assertFailsWith<IllegalStateException> { env.resize(65536) }
                assertContentEquals(payload, reader.table().get(key(1)))
            }
            env.resize(0) // Native clamps to live data rather than deleting it.
            env.begin(true).use { assertContentEquals(payload, it.table().get(key(1))) }
            // use/close would fail if the rejected begin kept an active reservation.
        }
    }

    @Test fun mapFullAndFailedCommitConsumeOnlyTheFailedTransaction() {
        MdbxEnvironment.open(directory, maxSizeBytes = 1L shl 20, geometry = geometry).use { env ->
            env.begin().use { txn -> txn.table().put(key(100), byteArrayOf(7)); txn.commit() }
            val txn = env.begin()
            val table = txn.table()
            try {
                val putError = assertFailsWith<MdbxException> { table.put(key(1), ByteArray(2 shl 20)) }
                assertEquals(-30792, putError.code)
                val commitError = assertFailsWith<MdbxException> { txn.commit() }
                assertEquals(-1, commitError.code) // Upstream MDBX_RESULT_TRUE: aborted broken txn.
                assertFailsWith<IllegalStateException> { table.get(key(1)) }
            } finally {
                txn.close()
            }
            env.begin(true).use { reader ->
                assertNull(reader.table().get(key(1)))
                assertContentEquals(byteArrayOf(7), reader.table().get(key(100)))
            }
        }
    }

    @Test fun virtualThreadsAreRejectedBeforeCreatingNativeTransactions() {
        MdbxEnvironment.open(directory).use { env ->
            Executors.newVirtualThreadPerTaskExecutor().use { threads ->
                threads.submit {
                    assertFailsWith<IllegalStateException> { env.begin() }
                    assertFailsWith<IllegalStateException> { env.begin(true) }
                }.get(5, SECONDS)
            }
            env.begin().use { it.table().put(key(1), payload); it.commit() }
        }
    }

    @Test fun upperBoundAboveIntRangeUses64BitGeometryWithoutAllocatingTheWholeFile() {
        MdbxEnvironment.open(directory, maxSizeBytes = 64L shl 30, geometry = geometry.copy(initialSizeBytes = 0)).use { env ->
            env.begin().use { txn -> txn.table().put(key(1), payload); txn.commit() }
            env.begin(true).use { assertContentEquals(payload, it.table().get(key(1))) }
            assertTrue(size() < 1L shl 20)
        }
    }

    @Test fun invalidGeometryIsRejectedBeforeCreatingDatabaseFiles() {
        assertFailsWith<IllegalArgumentException> { MdbxGeometry(minSizeBytes = -2) }
        assertFailsWith<IllegalArgumentException> { MdbxGeometry(pageSizeBytes = 3000) }
        assertFailsWith<IllegalArgumentException> { MdbxGeometry(growthStepBytes = 8192, shrinkThresholdBytes = 4096) }
        assertFailsWith<IllegalArgumentException> {
            MdbxEnvironment.open(directory, maxSizeBytes = 1L shl 20, geometry = MdbxGeometry(initialSizeBytes = 2L shl 20))
        }
        assertFailsWith<IllegalArgumentException> {
            MdbxEnvironment.open(directory, maxSizeBytes = 1L shl 20, geometry = MdbxGeometry(growthStepBytes = Long.MAX_VALUE))
        }
        assertFalse(Files.exists(directory.resolve("mdbx.dat")))
    }
}
