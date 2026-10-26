package net.echonolix.caelum.mdbx

import net.echonolix.caelum.mdbx.functions.*
import net.echonolix.caelum.mdbx.structs.MDBX_val
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.ValueLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class MdbxTest {
    @TempDir lateinit var directory: Path
    private val key = byteArrayOf(0, 1, -1, 42)
    private val payload = ByteArray(65537) { ((it * 31) xor (it ushr 7)).toByte() }

    @Test fun generatedAbiMatchesUpstream() {
        Mdbx.load()
        Mdbx.load()
        assertEquals(1404u, caelum_mdbx_version_number())
        val layout = MDBX_val.layout
        assertEquals(caelum_mdbx_val_size().toLong(), layout.byteSize())
        assertEquals(caelum_mdbx_val_alignment().toLong(), layout.byteAlignment())
        assertEquals(caelum_mdbx_val_base_offset().toLong(), layout.byteOffset(groupElement("iov_base")))
        assertEquals(caelum_mdbx_val_length_offset().toLong(), layout.byteOffset(groupElement("iov_len")))
        assertEquals(listOf("iov_base", "iov_len"), (layout as java.lang.foreign.StructLayout).memberLayouts().map { it.name().orElseThrow() })
    }

    @Test fun generatedFunctionCarriersPreservePointerWidthAndBool() {
        assertEquals(listOf(8L, 8L, 8L, 8L, 8L, 8L, 8L), _mdbx_env_set_geometry_fd.argumentLayouts().map { it.byteSize() })
        assertEquals(listOf(8L, 1L), _mdbx_env_close_ex_fd.argumentLayouts().map { it.byteSize() })
        assertEquals(listOf(8L, 8L, 4L, 4L), _caelum_mdbx_env_open_fd.argumentLayouts().map { it.byteSize() })
        assertEquals(listOf(8L, 4L, 8L, 8L), _mdbx_get_fd.argumentLayouts().map { it.byteSize() })
        assertEquals(ValueLayout.JAVA_INT, _mdbx_get_fd.returnLayout().orElseThrow())
    }

    @Test fun binaryOverflowValueSurvivesCommitCloseAndReopen() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                txn.table("binary", create = true).put(key, payload)
                txn.commit()
            }
        }
        MdbxEnvironment.open(directory).use { env ->
            env.begin(readOnly = true).use { txn ->
                assertContentEquals(payload, txn.table("binary").get(key))
            }
        }
    }

    @Test fun closingWriterRollsBackOverwriteAndNewKey() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn -> txn.table().put(key, payload); txn.commit() }
            env.begin().use { txn ->
                txn.table().put(key, byteArrayOf(99))
                txn.table().put(byteArrayOf(50), payload)
            }
            env.begin(true).use { txn ->
                assertContentEquals(payload, txn.table().get(key))
                assertNull(txn.table().get(byteArrayOf(50)))
            }
        }
    }

    @Test fun largeValueIsFilledBeforeLaterUpdatesAndFailedInsertion() {
        val large = ByteArray(9_773_372) { ((it * 17) xor (it ushr 11)).toByte() }
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                val table = txn.table()
                table.put(key, large)
                assertFailsWith<MdbxException> { table.put(key, byteArrayOf(9), noOverwrite = true) }
                repeat(100) { table.put(byteArrayOf(77, it.toByte()), payload) }
                assertContentEquals(large, table.get(key))
                txn.commit()
            }
            env.begin().use { txn ->
                txn.table().put(key, byteArrayOf())
                txn.table().put(key, payload)
            }
            env.begin(true).use { assertContentEquals(large, it.table().get(key)) }
        }
        MdbxEnvironment.open(directory).use { env ->
            env.begin(true).use { assertContentEquals(large, it.table().get(key)) }
            env.begin().use { txn -> txn.table().put(key, payload); txn.commit() }
            env.begin(true).use { assertContentEquals(payload, it.table().get(key)) }
        }
    }

    @Test fun missingEmptyValuesAndDeleteAreDistinct() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                val table = txn.table()
                assertNull(table.get(key))
                assertFalse(table.delete(key))
                table.put(key, byteArrayOf())
                assertContentEquals(byteArrayOf(), table.get(key))
                table.put(byteArrayOf(), payload)
                assertContentEquals(payload, table.get(byteArrayOf()))
                assertTrue(table.delete(key))
                assertNull(table.get(key))
                txn.commit()
            }
        }
    }

    @Test fun nativeErrorPreservesCodeAndTransactionCanContinue() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                val table = txn.table()
                table.put(key, payload)
                val failure = assertFailsWith<MdbxException> { table.put(key, byteArrayOf(1), noOverwrite = true) }
                assertEquals(-30799, failure.code)
                assertEquals("mdbx_put", failure.operation)
                assertTrue(failure.message!!.contains("-30799"))
                assertContentEquals(payload, table.get(key))
                txn.commit()
            }
            env.begin(true).use { txn ->
                val failure = assertFailsWith<MdbxException> { txn.table("missing") }
                assertEquals(-30798, failure.code)
            }
        }
    }

    @Test fun finishedTransactionsAndTablesCannotAccessNativeHandles() {
        val env = MdbxEnvironment.open(directory)
        val txn = env.begin()
        val table = txn.table()
        table.put(key, payload)
        txn.commit()
        txn.close()
        assertFailsWith<IllegalStateException> { txn.commit() }
        assertFailsWith<IllegalStateException> { txn.table() }
        assertFailsWith<IllegalStateException> { table.get(key) }
        assertFailsWith<IllegalStateException> { table.put(key, payload) }
        assertFailsWith<IllegalStateException> { table.delete(key) }
        env.close()
        env.close()
        assertFailsWith<IllegalStateException> { env.begin() }
    }

    @Test fun activeTransactionBlocksEnvironmentCloseAndAbortIsIdempotent() {
        val env = MdbxEnvironment.open(directory)
        val txn = env.begin()
        assertFailsWith<IllegalStateException> { env.close() }
        txn.table().put(key, payload)
        txn.close()
        txn.close()
        assertFailsWith<IllegalStateException> { txn.table() }
        env.close()
        MdbxEnvironment.open(directory).use { reopened ->
            reopened.begin(true).use { assertNull(it.table().get(key)) }
        }
    }

    @Test fun readOnlyWriteAndCrossThreadAccessAreRejected() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { it.table().put(key, payload); it.commit() }
            env.begin(true).use { txn ->
                val table = txn.table()
                assertFailsWith<IllegalStateException> { table.put(key, payload) }
                assertFailsWith<IllegalStateException> { table.delete(key) }
                assertFailsWith<IllegalStateException> { txn.table("new", true) }
                Executors.newSingleThreadExecutor().use { worker ->
                    worker.submit {
                        assertFailsWith<IllegalStateException> { table.get(key) }
                        assertFailsWith<IllegalStateException> { txn.commit() }
                        assertFailsWith<IllegalStateException> { txn.close() }
                    }.get()
                }
                assertContentEquals(payload, table.get(key))
            }
        }
    }

    @Test fun readerSnapshotAndCopiedValueSurviveConcurrentCommit() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { it.table().put(key, payload); it.commit() }
            val copied: ByteArray
            env.begin(true).use { reader ->
                copied = reader.table().get(key)!!
                Executors.newSingleThreadExecutor().use { worker ->
                    worker.submit {
                        env.begin().use { txn -> txn.table().put(key, byteArrayOf(9)); txn.commit() }
                    }.get()
                }
                assertContentEquals(payload, reader.table().get(key))
            }
            env.begin(true).use { assertContentEquals(byteArrayOf(9), it.table().get(key)) }
            assertContentEquals(payload, copied)
        }
    }

    @Test fun namedTablesHaveIndependentKeySpacesAndAbortedCreationIsAbsent() {
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                txn.table("first", true).put(key, byteArrayOf(1))
                txn.table("second", true).put(key, byteArrayOf(2))
                txn.commit()
            }
            env.begin().use { txn -> txn.table("aborted", true).put(key, payload) }
            env.begin(true).use { txn ->
                assertContentEquals(byteArrayOf(1), txn.table("first").get(key))
                assertContentEquals(byteArrayOf(2), txn.table("second").get(key))
                assertEquals(-30798, assertFailsWith<MdbxException> { txn.table("aborted") }.code)
            }
        }
    }

    @Test fun failedOpenCleansUpAndValidEnvironmentStillWorks() {
        repeat(3) {
            val failure = assertFailsWith<MdbxException> { MdbxEnvironment.open(directory.resolve("absent/database")) }
            assertEquals("mdbx_env_open", failure.operation)
            assertTrue(failure.code != 0)
            assertTrue(failure.suppressed.isEmpty())
        }
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { it.table().put(key, payload); it.commit() }
        }
        assertTrue(Files.exists(directory.resolve("mdbx.dat")))
    }

    @Test fun validatesGeometryAndNamesBeforeNativeSideEffects() {
        assertFailsWith<IllegalArgumentException> { MdbxEnvironment.open(directory, maxSizeBytes = 1) }
        assertFailsWith<IllegalArgumentException> { MdbxEnvironment.open(directory, maxTables = 0) }
        MdbxEnvironment.open(directory).use { env ->
            env.begin().use { txn ->
                assertFailsWith<IllegalArgumentException> { txn.table("bad\u0000name", true) }
                txn.table().put(key, payload)
                txn.commit()
            }
        }
    }
}
