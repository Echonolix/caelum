package net.echonolix.caelum.mdbx

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

class MdbxUsageTest {
    @TempDir lateinit var directory: Path
    @Test fun nativeUsageSeparatesPreallocatedFileAndUsedPagesAndTracksSnapshot() {
        MdbxEnvironment.open(directory, 32L shl 20, geometry = MdbxGeometry(initialSizeBytes = 16L shl 20, growthStepBytes = 1L shl 20, shrinkThresholdBytes = 0)).use { env ->
            val initial = env.begin(true).use { it.usage() }
            assertTrue(initial.usedPages > 0)
            assertEquals(initial.usedBytes, initial.usedPages * initial.pageSize)
            assertTrue(initial.fileBytes > initial.usedBytes)
            env.begin().use { tx -> tx.table("data", true).put(byteArrayOf(1), ByteArray(1 shl 20) { it.toByte() }); tx.commit() }
            val filled = env.begin(true).use { it.usage() }
            assertTrue(filled.usedBytes > initial.usedBytes)
            env.begin(true).use { reader ->
                val before = reader.usage()
                val writer = Thread.ofPlatform().start { env.begin().use { tx -> tx.table("data").delete(byteArrayOf(1)); tx.commit() } }
                writer.join()
                assertEquals(before.usedBytes, reader.usage().usedBytes)
                assertEquals(1 shl 20, reader.table("data").get(byteArrayOf(1))!!.size)
            }
            env.begin(true).use { assertNull(it.table("data").get(byteArrayOf(1))); assertTrue(it.usage().usedBytes <= it.usage().fileBytes) }
            val finished = env.begin(true); finished.close()
            assertThrows(IllegalStateException::class.java) { finished.usage() }
        }
        MdbxEnvironment.open(directory, 32L shl 20).use { env -> env.begin(true).use { assertTrue(it.usage().usedPages > 0) } }
    }
}
