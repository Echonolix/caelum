package net.echonolix.caelum.mimalloc

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MimallocTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun freshProcessesExitNormallyWithStatisticsEnabledAndDisabled() {
        for (statistics in listOf("0", "1")) {
            val log = directory.resolve("exit-$statistics.log")
            val builder = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("mimalloc.test.runtimeClasspath"),
                "net.echonolix.caelum.mimalloc.MimallocExitProbe",
            ).redirectErrorStream(true).redirectOutput(log.toFile())
            builder.environment()["MIMALLOC_SHOW_STATS"] = statistics
            val process = builder.start()
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Native exit timed out")
                val output = Files.readString(log)
                assertEquals(0, process.exitValue(), output)
                assertTrue(output.contains("EXIT_PROBE_OK"), output)
                if (statistics == "1") assertTrue(output.contains("reserved:"), output)
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor()
            }
        }
    }

    @Test
    fun alignedAndZeroedAllocationsCanBeFreedByAnotherThreadAfterAllocatorThreadExits() {
        val allocator = Executors.newSingleThreadExecutor()
        val addresses = try {
            allocator.submit<List<Pair<Long, Int>>> {
                listOf(1, 21, 4096, 8193, 28672).flatMap { size ->
                    listOf(4L, 8L, 64L, 4096L).map { alignment ->
                        val address = Mimalloc.calloc(size.toLong(), alignment)
                        assertEquals(0L, address % alignment)
                        assertTrue(Mimalloc.usableSize(address) >= size)
                        val memory = MemorySegment.ofAddress(address).reinterpret(size.toLong())
                        for (i in 0 until size) assertEquals(0.toByte(), memory.get(ValueLayout.JAVA_BYTE, i.toLong()))
                        memory.fill(0x5a)
                        address to size
                    }
                }
            }.get(10, TimeUnit.SECONDS)
        } finally {
            allocator.shutdown()
            check(allocator.awaitTermination(10, TimeUnit.SECONDS))
        }
        val releaser = Executors.newFixedThreadPool(4)
        try {
            val tasks = addresses.map { (address, size) ->
                releaser.submit {
                    try {
                        val memory = MemorySegment.ofAddress(address).reinterpret(size.toLong())
                        for (i in 0 until size) assertEquals(0x5a.toByte(), memory.get(ValueLayout.JAVA_BYTE, i.toLong()))
                    } finally {
                        Mimalloc.free(address)
                    }
                }
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            releaser.shutdown()
            check(releaser.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun mallocCoversTheWholeRequestedExtentWithoutAnArena() {
        val address = Mimalloc.malloc(65539, 64)
        try {
            val memory = MemorySegment.ofAddress(address).reinterpret(65539)
            memory.fill(0x36)
            assertEquals(0x36.toByte(), memory.get(ValueLayout.JAVA_BYTE, 65538))
            assertEquals(0L, address % 64)
        } finally {
            Mimalloc.free(address)
        }
        Mimalloc.free(0)
        Mimalloc.collect(true)
    }

    @Test
    fun nativeAllocationFailureIsReportedAndDoesNotPoisonLaterAllocations() {
        assertFailsWith<OutOfMemoryError> { Mimalloc.malloc(Long.MAX_VALUE) }
        assertFailsWith<OutOfMemoryError> { Mimalloc.calloc(Long.MAX_VALUE) }
        val address = Mimalloc.malloc(21, 4)
        Mimalloc.free(address)
    }
}

object MimallocExitProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val addresses = LongArray(128)
        val worker = Thread {
            for (i in addresses.indices) {
                val size = 21L + i * 257
                addresses[i] = Mimalloc.malloc(size, 4)
                MemorySegment.ofAddress(addresses[i]).reinterpret(size).fill(i.toByte())
            }
        }
        worker.start(); worker.join(10000)
        check(!worker.isAlive)
        for (i in addresses.indices) {
            val memory = MemorySegment.ofAddress(addresses[i]).reinterpret(21L + i * 257)
            check(memory.get(ValueLayout.JAVA_BYTE, memory.byteSize() - 1) == i.toByte())
            Mimalloc.free(addresses[i])
        }
        println("EXIT_PROBE_OK")
    }
}
