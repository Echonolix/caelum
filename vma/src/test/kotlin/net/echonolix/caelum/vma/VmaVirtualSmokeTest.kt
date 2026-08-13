package net.echonolix.caelum.vma

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VmaVirtualSmokeTest {
    @Test
    fun explicitLibraryAllocatesTracksAndFreesVirtualMemoryWithoutGpu() {
        val library = Path.of(requireNotNull(System.getProperty("caelum.vma.test.library")))
        verifyVirtualAllocator(VmaVirtual.load(library))
    }

    @Test
    fun bundledLibraryExtractsAndAllocatesVirtualMemoryWithoutGpu() {
        verifyVirtualAllocator(VmaVirtual.loadBundled())
    }

    @Test
    fun privateCachePublishPreservesAnOccupiedCandidate() {
        val root = Files.createTempDirectory("caelum-vma-publish-test-")
        try {
            val staged = root.resolve("staged.dll")
            val content = root.resolve("content")
            Files.createDirectory(content)
            Files.writeString(staged, "expected VMA bytes")
            val occupied = content.resolve("caelum_vma.dll")
            Files.writeString(occupied, "do not replace")

            val expectedDigest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(staged))
                .joinToString("") { "%02x".format(it) }
            val published = VmaVirtual.publishStagedLibrary(staged, content, "caelum_vma.dll", expectedDigest)

            assertEquals("do not replace", Files.readString(occupied))
            assertTrue(Files.isRegularFile(published))
            assertEquals("expected VMA bytes", Files.readString(published))
            assertFalse(published == occupied)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cachePublishCreatesStablePrimaryThenReusesIt() {
        val root = Files.createTempDirectory("caelum-vma-primary-test-")
        try {
            val staged = root.resolve("staged.dll")
            val content = root.resolve("content")
            Files.createDirectory(content)
            Files.writeString(staged, "expected VMA bytes")
            val expectedDigest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(staged))
                .joinToString("") { "%02x".format(it) }

            val first = VmaVirtual.publishStagedLibrary(staged, content, "caelum_vma.dll", expectedDigest)
            val second = VmaVirtual.publishStagedLibrary(staged, content, "caelum_vma.dll", expectedDigest)

            assertEquals(content.resolve("caelum_vma.dll"), first)
            assertEquals(first, second)
            assertEquals("expected VMA bytes", Files.readString(second))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun verifyVirtualAllocator(vma: VmaVirtual) {
        vma.use {
            Arena.ofConfined().use { arena ->
                val blockInfo = arena.allocate(VmaLayouts.virtualBlockCreateInfo)
                val blockOut = arena.allocate(ValueLayout.JAVA_LONG)
                VmaVirtualBlockCreateInfo.set(blockInfo, size = 4096)
                assertEquals(0, vma.createVirtualBlock(blockInfo, blockOut))
                val block = blockOut.get(ValueLayout.JAVA_LONG, 0)
                assertTrue(block != 0L)
                assertTrue(vma.isVirtualBlockEmpty(block))

                try {
                    val allocationInfo = arena.allocate(VmaLayouts.virtualAllocationCreateInfo)
                    val allocationOut = arena.allocate(ValueLayout.JAVA_LONG)
                    val offsetOut = arena.allocate(ValueLayout.JAVA_LONG)
                    VmaVirtualAllocationCreateInfo.set(
                        allocationInfo, size = 1000, alignment = 256, userData = 0x1234
                    )
                    assertEquals(0, vma.virtualAllocate(block, allocationInfo, allocationOut, offsetOut))
                    val allocation = allocationOut.get(ValueLayout.JAVA_LONG, 0)
                    val offset = offsetOut.get(ValueLayout.JAVA_LONG, 0)
                    assertTrue(allocation != 0L)
                    assertEquals(0L, offset % 256)
                    assertFalse(vma.isVirtualBlockEmpty(block))

                    val info = arena.allocate(VmaLayouts.virtualAllocationInfo)
                    vma.getVirtualAllocationInfo(block, allocation, info)
                    assertEquals(offset, VmaVirtualAllocationInfo.offset(info))
                    assertEquals(1000, VmaVirtualAllocationInfo.size(info))
                    assertEquals(0x1234, VmaVirtualAllocationInfo.userData(info))
                    vma.setVirtualAllocationUserData(block, allocation, 0x5678)
                    vma.getVirtualAllocationInfo(block, allocation, info)
                    assertEquals(0x5678, VmaVirtualAllocationInfo.userData(info))

                    val stats = arena.allocate(VmaLayouts.statistics)
                    vma.getVirtualBlockStatistics(block, stats)
                    assertEquals(1, VmaStatistics.blockCount(stats))
                    assertEquals(1, VmaStatistics.allocationCount(stats))
                    assertEquals(4096, VmaStatistics.blockBytes(stats))
                    assertEquals(1000, VmaStatistics.allocationBytes(stats))

                    val detailed = arena.allocate(VmaLayouts.detailedStatistics)
                    vma.calculateVirtualBlockStatistics(block, detailed)
                    assertEquals(1, VmaStatistics.allocationCount(detailed))

                    val stringOut = arena.allocate(ValueLayout.JAVA_LONG)
                    vma.buildVirtualBlockStatsString(block, stringOut, detailedMap = true)
                    val stringAddress = stringOut.get(ValueLayout.JAVA_LONG, 0)
                    assertTrue(stringAddress != 0L)
                    try {
                        val json = MemorySegment.ofAddress(stringAddress).reinterpret(8192).getString(0)
                        assertTrue(json.contains("AllocationBytes"))
                    } finally {
                        vma.freeVirtualBlockStatsString(block, stringAddress)
                    }

                    vma.virtualFree(block, allocation)
                    assertTrue(vma.isVirtualBlockEmpty(block))
                } finally {
                    vma.clearVirtualBlock(block)
                    vma.destroyVirtualBlock(block)
                }
            }
        }
    }
}
