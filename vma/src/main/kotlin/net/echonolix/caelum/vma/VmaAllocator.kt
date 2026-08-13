package net.echonolix.caelum.vma

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Path

/**
 * Low-level, library-scoped FFM binding for VMA's platform-independent allocator API.
 *
 * All VMA and Vulkan handles are represented as raw [Long] values, matching Caelum's existing
 * 64-bit native handle convention. Pointer parameters that refer to structs or arrays are
 * [MemorySegment]s. Vulkan `VkResult` values are returned unchanged as [Int].
 */
public class VmaAllocator private constructor(
    private val arena: Arena,
    private val lookup: SymbolLookup,
) : AutoCloseable {
    init { check(ValueLayout.ADDRESS.byteSize() == 8L) { "VmaAllocator requires a 64-bit address space" } }

    private val linker = Linker.nativeLinker()
    private val p = ValueLayout.JAVA_LONG
    private val i = ValueLayout.JAVA_INT
    private fun fn(name: String, descriptor: FunctionDescriptor): MethodHandle = linker.downcallHandle(
        lookup.find(name).orElseThrow { UnsatisfiedLinkError("VMA library does not export $name") }, descriptor,
    )

    private val createAllocatorH = fn("vmaCreateAllocator", FunctionDescriptor.of(i, p, p))
    private val destroyAllocatorH = fn("vmaDestroyAllocator", FunctionDescriptor.ofVoid(p))
    private val getAllocatorInfoH = fn("vmaGetAllocatorInfo", FunctionDescriptor.ofVoid(p, p))
    private val getPhysicalDevicePropertiesH = fn("vmaGetPhysicalDeviceProperties", FunctionDescriptor.ofVoid(p, p))
    private val getMemoryPropertiesH = fn("vmaGetMemoryProperties", FunctionDescriptor.ofVoid(p, p))
    private val getMemoryTypePropertiesH = fn("vmaGetMemoryTypeProperties", FunctionDescriptor.ofVoid(p, i, p))
    private val setCurrentFrameIndexH = fn("vmaSetCurrentFrameIndex", FunctionDescriptor.ofVoid(p, i))
    private val calculateStatisticsH = fn("vmaCalculateStatistics", FunctionDescriptor.ofVoid(p, p))
    private val getHeapBudgetsH = fn("vmaGetHeapBudgets", FunctionDescriptor.ofVoid(p, p))
    private val findMemoryTypeIndexH = fn("vmaFindMemoryTypeIndex", FunctionDescriptor.of(i, p, i, p, p))
    private val findMemoryTypeIndexForBufferInfoH = fn("vmaFindMemoryTypeIndexForBufferInfo", FunctionDescriptor.of(i, p, p, p, p))
    private val findMemoryTypeIndexForImageInfoH = fn("vmaFindMemoryTypeIndexForImageInfo", FunctionDescriptor.of(i, p, p, p, p))
    private val createPoolH = fn("vmaCreatePool", FunctionDescriptor.of(i, p, p, p))
    private val destroyPoolH = fn("vmaDestroyPool", FunctionDescriptor.ofVoid(p, p))
    private val getPoolStatisticsH = fn("vmaGetPoolStatistics", FunctionDescriptor.ofVoid(p, p, p))
    private val calculatePoolStatisticsH = fn("vmaCalculatePoolStatistics", FunctionDescriptor.ofVoid(p, p, p))
    private val checkPoolCorruptionH = fn("vmaCheckPoolCorruption", FunctionDescriptor.of(i, p, p))
    private val getPoolNameH = fn("vmaGetPoolName", FunctionDescriptor.ofVoid(p, p, p))
    private val setPoolNameH = fn("vmaSetPoolName", FunctionDescriptor.ofVoid(p, p, p))
    private val allocateMemoryH = fn("vmaAllocateMemory", FunctionDescriptor.of(i, p, p, p, p, p))
    private val allocateDedicatedMemoryH = fn("vmaAllocateDedicatedMemory", FunctionDescriptor.of(i, p, p, p, p, p, p))
    private val allocateMemoryPagesH = fn("vmaAllocateMemoryPages", FunctionDescriptor.of(i, p, p, p, p, p, p))
    private val allocateMemoryForBufferH = fn("vmaAllocateMemoryForBuffer", FunctionDescriptor.of(i, p, p, p, p, p))
    private val allocateMemoryForImageH = fn("vmaAllocateMemoryForImage", FunctionDescriptor.of(i, p, p, p, p, p))
    private val freeMemoryH = fn("vmaFreeMemory", FunctionDescriptor.ofVoid(p, p))
    private val freeMemoryPagesH = fn("vmaFreeMemoryPages", FunctionDescriptor.ofVoid(p, p, p))
    private val getAllocationInfoH = fn("vmaGetAllocationInfo", FunctionDescriptor.ofVoid(p, p, p))
    private val getAllocationInfo2H = fn("vmaGetAllocationInfo2", FunctionDescriptor.ofVoid(p, p, p))
    private val setAllocationUserDataH = fn("vmaSetAllocationUserData", FunctionDescriptor.ofVoid(p, p, p))
    private val setAllocationNameH = fn("vmaSetAllocationName", FunctionDescriptor.ofVoid(p, p, p))
    private val getAllocationMemoryPropertiesH = fn("vmaGetAllocationMemoryProperties", FunctionDescriptor.ofVoid(p, p, p))
    private val mapMemoryH = fn("vmaMapMemory", FunctionDescriptor.of(i, p, p, p))
    private val unmapMemoryH = fn("vmaUnmapMemory", FunctionDescriptor.ofVoid(p, p))
    private val flushAllocationH = fn("vmaFlushAllocation", FunctionDescriptor.of(i, p, p, p, p))
    private val invalidateAllocationH = fn("vmaInvalidateAllocation", FunctionDescriptor.of(i, p, p, p, p))
    private val flushAllocationsH = fn("vmaFlushAllocations", FunctionDescriptor.of(i, p, i, p, p, p))
    private val invalidateAllocationsH = fn("vmaInvalidateAllocations", FunctionDescriptor.of(i, p, i, p, p, p))
    private val copyMemoryToAllocationH = fn("vmaCopyMemoryToAllocation", FunctionDescriptor.of(i, p, p, p, p, p))
    private val copyAllocationToMemoryH = fn("vmaCopyAllocationToMemory", FunctionDescriptor.of(i, p, p, p, p, p))
    private val checkCorruptionH = fn("vmaCheckCorruption", FunctionDescriptor.of(i, p, i))
    private val beginDefragmentationH = fn("vmaBeginDefragmentation", FunctionDescriptor.of(i, p, p, p))
    private val endDefragmentationH = fn("vmaEndDefragmentation", FunctionDescriptor.ofVoid(p, p, p))
    private val beginDefragmentationPassH = fn("vmaBeginDefragmentationPass", FunctionDescriptor.of(i, p, p, p))
    private val endDefragmentationPassH = fn("vmaEndDefragmentationPass", FunctionDescriptor.of(i, p, p, p))
    private val bindBufferMemoryH = fn("vmaBindBufferMemory", FunctionDescriptor.of(i, p, p, p))
    private val bindBufferMemory2H = fn("vmaBindBufferMemory2", FunctionDescriptor.of(i, p, p, p, p, p))
    private val bindImageMemoryH = fn("vmaBindImageMemory", FunctionDescriptor.of(i, p, p, p))
    private val bindImageMemory2H = fn("vmaBindImageMemory2", FunctionDescriptor.of(i, p, p, p, p, p))
    private val createBufferH = fn("vmaCreateBuffer", FunctionDescriptor.of(i, p, p, p, p, p, p))
    private val createBufferWithAlignmentH = fn("vmaCreateBufferWithAlignment", FunctionDescriptor.of(i, p, p, p, p, p, p, p))
    private val createDedicatedBufferH = fn("vmaCreateDedicatedBuffer", FunctionDescriptor.of(i, p, p, p, p, p, p, p))
    private val createAliasingBufferH = fn("vmaCreateAliasingBuffer", FunctionDescriptor.of(i, p, p, p, p))
    private val createAliasingBuffer2H = fn("vmaCreateAliasingBuffer2", FunctionDescriptor.of(i, p, p, p, p, p))
    private val destroyBufferH = fn("vmaDestroyBuffer", FunctionDescriptor.ofVoid(p, p, p))
    private val createImageH = fn("vmaCreateImage", FunctionDescriptor.of(i, p, p, p, p, p, p))
    private val createDedicatedImageH = fn("vmaCreateDedicatedImage", FunctionDescriptor.of(i, p, p, p, p, p, p, p))
    private val createAliasingImageH = fn("vmaCreateAliasingImage", FunctionDescriptor.of(i, p, p, p, p))
    private val createAliasingImage2H = fn("vmaCreateAliasingImage2", FunctionDescriptor.of(i, p, p, p, p, p))
    private val destroyImageH = fn("vmaDestroyImage", FunctionDescriptor.ofVoid(p, p, p))
    private val buildStatsStringH = fn("vmaBuildStatsString", FunctionDescriptor.ofVoid(p, p, i))
    private val freeStatsStringH = fn("vmaFreeStatsString", FunctionDescriptor.ofVoid(p, p))

    public fun createAllocator(createInfo: MemorySegment, allocatorOut: MemorySegment): Int {
        require(createInfo.get(ValueLayout.JAVA_LONG, 56) != 0L) {
            "This VMA build uses dynamic Vulkan loading; attach a VmaVulkanFunctions table " +
                "with vkGetInstanceProcAddr and vkGetDeviceProcAddr before creating the allocator."
        }
        return createAllocatorH.invokeExact(createInfo.address(), allocatorOut.address()) as Int
    }
    public fun destroyAllocator(allocator: Long): Unit { destroyAllocatorH.invokeExact(allocator) }
    public fun getAllocatorInfo(allocator: Long, infoOut: MemorySegment): Unit { getAllocatorInfoH.invokeExact(allocator, infoOut.address()) }
    /** Writes a pointer to VMA-owned `VkPhysicalDeviceProperties` into [propertiesPointerOut]. */
    public fun getPhysicalDeviceProperties(allocator: Long, propertiesPointerOut: MemorySegment): Unit { getPhysicalDevicePropertiesH.invokeExact(allocator, propertiesPointerOut.address()) }
    /** Writes a pointer to VMA-owned `VkPhysicalDeviceMemoryProperties` into [propertiesPointerOut]. */
    public fun getMemoryProperties(allocator: Long, propertiesPointerOut: MemorySegment): Unit { getMemoryPropertiesH.invokeExact(allocator, propertiesPointerOut.address()) }
    public fun getMemoryTypeProperties(allocator: Long, memoryTypeIndex: Int, flagsOut: MemorySegment): Unit { getMemoryTypePropertiesH.invokeExact(allocator, memoryTypeIndex, flagsOut.address()) }
    public fun setCurrentFrameIndex(allocator: Long, frameIndex: Int): Unit { setCurrentFrameIndexH.invokeExact(allocator, frameIndex) }
    public fun calculateStatistics(allocator: Long, statisticsOut: MemorySegment): Unit { calculateStatisticsH.invokeExact(allocator, statisticsOut.address()) }
    public fun getHeapBudgets(allocator: Long, budgetsOut: MemorySegment): Unit { getHeapBudgetsH.invokeExact(allocator, budgetsOut.address()) }
    public fun findMemoryTypeIndex(allocator: Long, memoryTypeBits: Int, createInfo: MemorySegment, memoryTypeIndexOut: MemorySegment): Int =
        findMemoryTypeIndexH.invokeExact(allocator, memoryTypeBits, createInfo.address(), memoryTypeIndexOut.address()) as Int
    public fun findMemoryTypeIndexForBufferInfo(allocator: Long, bufferCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, memoryTypeIndexOut: MemorySegment): Int =
        findMemoryTypeIndexForBufferInfoH.invokeExact(allocator, bufferCreateInfo.address(), allocationCreateInfo.address(), memoryTypeIndexOut.address()) as Int
    public fun findMemoryTypeIndexForImageInfo(allocator: Long, imageCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, memoryTypeIndexOut: MemorySegment): Int =
        findMemoryTypeIndexForImageInfoH.invokeExact(allocator, imageCreateInfo.address(), allocationCreateInfo.address(), memoryTypeIndexOut.address()) as Int
    public fun createPool(allocator: Long, createInfo: MemorySegment, poolOut: MemorySegment): Int = createPoolH.invokeExact(allocator, createInfo.address(), poolOut.address()) as Int
    public fun destroyPool(allocator: Long, pool: Long): Unit { destroyPoolH.invokeExact(allocator, pool) }
    public fun getPoolStatistics(allocator: Long, pool: Long, statisticsOut: MemorySegment): Unit { getPoolStatisticsH.invokeExact(allocator, pool, statisticsOut.address()) }
    public fun calculatePoolStatistics(allocator: Long, pool: Long, statisticsOut: MemorySegment): Unit { calculatePoolStatisticsH.invokeExact(allocator, pool, statisticsOut.address()) }
    public fun checkPoolCorruption(allocator: Long, pool: Long): Int = checkPoolCorruptionH.invokeExact(allocator, pool) as Int
    public fun getPoolName(allocator: Long, pool: Long, namePointerOut: MemorySegment): Unit { getPoolNameH.invokeExact(allocator, pool, namePointerOut.address()) }
    public fun setPoolName(allocator: Long, pool: Long, utf8Name: MemorySegment): Unit { setPoolNameH.invokeExact(allocator, pool, utf8Name.address()) }

    public fun allocateMemory(allocator: Long, requirements: MemorySegment, createInfo: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        allocateMemoryH.invokeExact(allocator, requirements.address(), createInfo.address(), allocationOut.address(), infoOut.address()) as Int
    public fun allocateDedicatedMemory(allocator: Long, requirements: MemorySegment, createInfo: MemorySegment, memoryAllocateNext: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        allocateDedicatedMemoryH.invokeExact(allocator, requirements.address(), createInfo.address(), memoryAllocateNext.address(), allocationOut.address(), infoOut.address()) as Int
    public fun allocateMemoryPages(allocator: Long, requirements: MemorySegment, createInfo: MemorySegment, allocationCount: Long, allocationsOut: MemorySegment, infosOut: MemorySegment): Int =
        allocateMemoryPagesH.invokeExact(allocator, requirements.address(), createInfo.address(), allocationCount, allocationsOut.address(), infosOut.address()) as Int
    public fun allocateMemoryForBuffer(allocator: Long, buffer: Long, createInfo: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        allocateMemoryForBufferH.invokeExact(allocator, buffer, createInfo.address(), allocationOut.address(), infoOut.address()) as Int
    public fun allocateMemoryForImage(allocator: Long, image: Long, createInfo: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        allocateMemoryForImageH.invokeExact(allocator, image, createInfo.address(), allocationOut.address(), infoOut.address()) as Int
    public fun freeMemory(allocator: Long, allocation: Long): Unit { freeMemoryH.invokeExact(allocator, allocation) }
    public fun freeMemoryPages(allocator: Long, allocationCount: Long, allocations: MemorySegment): Unit { freeMemoryPagesH.invokeExact(allocator, allocationCount, allocations.address()) }
    public fun getAllocationInfo(allocator: Long, allocation: Long, infoOut: MemorySegment): Unit { getAllocationInfoH.invokeExact(allocator, allocation, infoOut.address()) }
    public fun getAllocationInfo2(allocator: Long, allocation: Long, infoOut: MemorySegment): Unit { getAllocationInfo2H.invokeExact(allocator, allocation, infoOut.address()) }
    public fun setAllocationUserData(allocator: Long, allocation: Long, userData: Long): Unit { setAllocationUserDataH.invokeExact(allocator, allocation, userData) }
    public fun setAllocationName(allocator: Long, allocation: Long, utf8Name: MemorySegment): Unit { setAllocationNameH.invokeExact(allocator, allocation, utf8Name.address()) }
    public fun getAllocationMemoryProperties(allocator: Long, allocation: Long, flagsOut: MemorySegment): Unit { getAllocationMemoryPropertiesH.invokeExact(allocator, allocation, flagsOut.address()) }
    public fun mapMemory(allocator: Long, allocation: Long, dataPointerOut: MemorySegment): Int = mapMemoryH.invokeExact(allocator, allocation, dataPointerOut.address()) as Int
    public fun unmapMemory(allocator: Long, allocation: Long): Unit { unmapMemoryH.invokeExact(allocator, allocation) }
    public fun flushAllocation(allocator: Long, allocation: Long, offset: Long, size: Long): Int = flushAllocationH.invokeExact(allocator, allocation, offset, size) as Int
    public fun invalidateAllocation(allocator: Long, allocation: Long, offset: Long, size: Long): Int = invalidateAllocationH.invokeExact(allocator, allocation, offset, size) as Int
    public fun flushAllocations(allocator: Long, allocationCount: Int, allocations: MemorySegment, offsets: MemorySegment, sizes: MemorySegment): Int =
        flushAllocationsH.invokeExact(allocator, allocationCount, allocations.address(), offsets.address(), sizes.address()) as Int
    public fun invalidateAllocations(allocator: Long, allocationCount: Int, allocations: MemorySegment, offsets: MemorySegment, sizes: MemorySegment): Int =
        invalidateAllocationsH.invokeExact(allocator, allocationCount, allocations.address(), offsets.address(), sizes.address()) as Int
    public fun copyMemoryToAllocation(allocator: Long, source: MemorySegment, allocation: Long, offset: Long, size: Long): Int =
        copyMemoryToAllocationH.invokeExact(allocator, source.address(), allocation, offset, size) as Int
    public fun copyMemoryFromAllocation(allocator: Long, allocation: Long, offset: Long, destination: MemorySegment, size: Long): Int =
        copyAllocationToMemoryH.invokeExact(allocator, allocation, offset, destination.address(), size) as Int
    public fun checkCorruption(allocator: Long, memoryTypeBits: Int): Int = checkCorruptionH.invokeExact(allocator, memoryTypeBits) as Int
    public fun beginDefragmentation(allocator: Long, info: MemorySegment, contextOut: MemorySegment): Int = beginDefragmentationH.invokeExact(allocator, info.address(), contextOut.address()) as Int
    public fun endDefragmentation(allocator: Long, context: Long, statsOut: MemorySegment): Unit { endDefragmentationH.invokeExact(allocator, context, statsOut.address()) }
    public fun beginDefragmentationPass(allocator: Long, context: Long, passInfo: MemorySegment): Int = beginDefragmentationPassH.invokeExact(allocator, context, passInfo.address()) as Int
    public fun endDefragmentationPass(allocator: Long, context: Long, passInfo: MemorySegment): Int = endDefragmentationPassH.invokeExact(allocator, context, passInfo.address()) as Int
    public fun bindBufferMemory(allocator: Long, allocation: Long, buffer: Long): Int = bindBufferMemoryH.invokeExact(allocator, allocation, buffer) as Int
    public fun bindBufferMemory2(allocator: Long, allocation: Long, allocationLocalOffset: Long, buffer: Long, next: MemorySegment): Int = bindBufferMemory2H.invokeExact(allocator, allocation, allocationLocalOffset, buffer, next.address()) as Int
    public fun bindImageMemory(allocator: Long, allocation: Long, image: Long): Int = bindImageMemoryH.invokeExact(allocator, allocation, image) as Int
    public fun bindImageMemory2(allocator: Long, allocation: Long, allocationLocalOffset: Long, image: Long, next: MemorySegment): Int = bindImageMemory2H.invokeExact(allocator, allocation, allocationLocalOffset, image, next.address()) as Int

    public fun createBuffer(allocator: Long, bufferCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, bufferOut: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        createBufferH.invokeExact(allocator, bufferCreateInfo.address(), allocationCreateInfo.address(), bufferOut.address(), allocationOut.address(), infoOut.address()) as Int
    public fun createBufferWithAlignment(allocator: Long, bufferCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, minAlignment: Long, bufferOut: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        createBufferWithAlignmentH.invokeExact(allocator, bufferCreateInfo.address(), allocationCreateInfo.address(), minAlignment, bufferOut.address(), allocationOut.address(), infoOut.address()) as Int
    public fun createDedicatedBuffer(allocator: Long, bufferCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, memoryAllocateNext: MemorySegment, bufferOut: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        createDedicatedBufferH.invokeExact(allocator, bufferCreateInfo.address(), allocationCreateInfo.address(), memoryAllocateNext.address(), bufferOut.address(), allocationOut.address(), infoOut.address()) as Int
    public fun createAliasingBuffer(allocator: Long, allocation: Long, bufferCreateInfo: MemorySegment, bufferOut: MemorySegment): Int = createAliasingBufferH.invokeExact(allocator, allocation, bufferCreateInfo.address(), bufferOut.address()) as Int
    public fun createAliasingBuffer2(allocator: Long, allocation: Long, allocationLocalOffset: Long, bufferCreateInfo: MemorySegment, bufferOut: MemorySegment): Int = createAliasingBuffer2H.invokeExact(allocator, allocation, allocationLocalOffset, bufferCreateInfo.address(), bufferOut.address()) as Int
    public fun destroyBuffer(allocator: Long, buffer: Long, allocation: Long): Unit { destroyBufferH.invokeExact(allocator, buffer, allocation) }
    public fun createImage(allocator: Long, imageCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, imageOut: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        createImageH.invokeExact(allocator, imageCreateInfo.address(), allocationCreateInfo.address(), imageOut.address(), allocationOut.address(), infoOut.address()) as Int
    public fun createDedicatedImage(allocator: Long, imageCreateInfo: MemorySegment, allocationCreateInfo: MemorySegment, memoryAllocateNext: MemorySegment, imageOut: MemorySegment, allocationOut: MemorySegment, infoOut: MemorySegment): Int =
        createDedicatedImageH.invokeExact(allocator, imageCreateInfo.address(), allocationCreateInfo.address(), memoryAllocateNext.address(), imageOut.address(), allocationOut.address(), infoOut.address()) as Int
    public fun createAliasingImage(allocator: Long, allocation: Long, imageCreateInfo: MemorySegment, imageOut: MemorySegment): Int = createAliasingImageH.invokeExact(allocator, allocation, imageCreateInfo.address(), imageOut.address()) as Int
    public fun createAliasingImage2(allocator: Long, allocation: Long, allocationLocalOffset: Long, imageCreateInfo: MemorySegment, imageOut: MemorySegment): Int = createAliasingImage2H.invokeExact(allocator, allocation, allocationLocalOffset, imageCreateInfo.address(), imageOut.address()) as Int
    public fun destroyImage(allocator: Long, image: Long, allocation: Long): Unit { destroyImageH.invokeExact(allocator, image, allocation) }
    public fun buildStatsString(allocator: Long, stringPointerOut: MemorySegment, detailedMap: Int): Unit { buildStatsStringH.invokeExact(allocator, stringPointerOut.address(), detailedMap) }
    public fun freeStatsString(allocator: Long, string: MemorySegment): Unit { freeStatsStringH.invokeExact(allocator, string.address()) }

    override fun close(): Unit = arena.close()

    public companion object {
        @JvmStatic public fun load(library: Path): VmaAllocator = open { arena -> SymbolLookup.libraryLookup(library, arena) }
        @JvmStatic public fun load(libraryName: String): VmaAllocator = open { arena -> SymbolLookup.libraryLookup(libraryName, arena) }
        /** Opens the same verified classpath-native artifact used by [VmaVirtual.loadBundled]. */
        @JvmStatic public fun loadBundled(): VmaAllocator =
            open { arena -> VmaVirtual.bundledLibraryLookup(arena) }
        private fun open(createLookup: (Arena) -> SymbolLookup): VmaAllocator {
            val arena = Arena.ofShared()
            return try { VmaAllocator(arena, createLookup(arena)) } catch (failure: Throwable) {
                arena.close()
                throw failure
            }
        }
    }
}
