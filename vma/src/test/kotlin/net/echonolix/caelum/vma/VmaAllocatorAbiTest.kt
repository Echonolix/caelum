package net.echonolix.caelum.vma

import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VmaAllocatorAbiTest {
    @Test
    fun coreAllocatorSymbolsResolveFromCompiledLibrary() {
        val configured = requireNotNull(System.getProperty("caelum.vma.test.library"))
        val library = Path.of(configured)
        VmaAllocator.load(library).use { /* Eager handles resolve every supported symbol. */ }
        VmaAllocator.loadBundled().use { /* Packaged artifact resolves the same core surface. */ }
    }

    @Test
    fun coreStructLayoutsMatch64BitVmaAbi() {
        // The native build declares VMA_EXTERNAL_MEMORY=1. This is an ABI
        // choice, not merely a feature choice: it controls the trailing field
        // of VmaAllocatorCreateInfo represented by this Kotlin layout.
        assertEquals("1", System.getProperty("caelum.vma.test.externalMemory"))
        assertEquals(88, VmaAllocatorLayouts.allocatorCreateInfo.byteSize())
        assertEquals(24, VmaAllocatorLayouts.allocatorInfo.byteSize())
        assertEquals(24, VmaAllocatorLayouts.memoryRequirements.byteSize())
        assertEquals(56, VmaAllocatorLayouts.allocationCreateInfo.byteSize())
        assertEquals(56, VmaAllocatorLayouts.allocationInfo.byteSize())
        assertEquals(72, VmaAllocatorLayouts.allocationInfo2.byteSize())
        assertEquals(40, VmaAllocatorLayouts.budget.byteSize())
        assertEquals(640, VmaAllocatorLayouts.heapBudgets.byteSize())
        assertEquals(56, VmaAllocatorExtendedLayouts.poolCreateInfo.byteSize())
        assertEquals(3136, VmaAllocatorExtendedLayouts.totalStatistics.byteSize())
        assertEquals(48, VmaAllocatorExtendedLayouts.defragmentationInfo.byteSize())
        assertEquals(24, VmaAllocatorExtendedLayouts.defragmentationMove.byteSize())
        assertEquals(16, VmaAllocatorExtendedLayouts.defragmentationPassMoveInfo.byteSize())
        assertEquals(24, VmaAllocatorExtendedLayouts.defragmentationStats.byteSize())
        assertEquals(224, VmaAllocatorExtendedLayouts.vulkanFunctions.byteSize())
    }

    @Test
    fun allocatorCreationRejectsMissingDynamicVulkanLoaderTable() {
        val configured = requireNotNull(System.getProperty("caelum.vma.test.library"))
        VmaAllocator.load(Path.of(configured)).use { vma ->
            Arena.ofConfined().use { arena ->
                val createInfo = arena.allocate(VmaAllocatorLayouts.allocatorCreateInfo).also { it.fill(0) }
                val allocatorOut = arena.allocate(ValueLayout.JAVA_LONG)
                val failure = assertFailsWith<IllegalArgumentException> {
                    vma.createAllocator(createInfo, allocatorOut)
                }
                kotlin.test.assertContains(failure.message.orEmpty(), "VmaVulkanFunctions")
            }
        }
    }

    @Test
    fun requiredDynamicVulkanFunctionsAttachToAllocatorCreateInfo() {
        Arena.ofConfined().use { arena ->
            val createInfo = arena.allocate(VmaAllocatorLayouts.allocatorCreateInfo).also { it.fill(0) }
            val functions = arena.allocate(VmaAllocatorExtendedLayouts.vulkanFunctions)
            VmaVulkanFunctions.clear(functions)
            VmaVulkanFunctions.getInstanceProcAddr(functions, 0x1111)
            VmaVulkanFunctions.getDeviceProcAddr(functions, 0x2222)

            VmaVulkanFunctions.attachToAllocatorCreateInfo(createInfo, functions)

            assertEquals(0x1111L, functions.get(ValueLayout.JAVA_LONG, 0))
            assertEquals(0x2222L, functions.get(ValueLayout.JAVA_LONG, 8))
            assertEquals(functions.address(), createInfo.get(ValueLayout.JAVA_LONG, 56))
        }
    }
}
