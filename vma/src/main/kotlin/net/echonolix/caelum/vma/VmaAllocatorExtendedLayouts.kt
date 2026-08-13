package net.echonolix.caelum.vma

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout

/** ABI layouts required by the extended, platform-independent VMA 3.4.0 allocator API. */
public object VmaAllocatorExtendedLayouts {
    private val I32 = ValueLayout.JAVA_INT
    private val I64 = ValueLayout.JAVA_LONG
    private val F32 = ValueLayout.JAVA_FLOAT

    /** `VmaVulkanFunctions` for the pinned Vulkan-Headers/VMA build (28 function-pointer slots). */
    @JvmField public val vulkanFunctions: StructLayout = MemoryLayout.structLayout(
        *arrayOf("vkGetInstanceProcAddr", "vkGetDeviceProcAddr", "vkGetPhysicalDeviceProperties",
            "vkGetPhysicalDeviceMemoryProperties", "vkAllocateMemory", "vkFreeMemory", "vkMapMemory",
            "vkUnmapMemory", "vkFlushMappedMemoryRanges", "vkInvalidateMappedMemoryRanges",
            "vkBindBufferMemory", "vkBindImageMemory", "vkGetBufferMemoryRequirements",
            "vkGetImageMemoryRequirements", "vkCreateBuffer", "vkDestroyBuffer", "vkCreateImage",
            "vkDestroyImage", "vkCmdCopyBuffer", "vkGetBufferMemoryRequirements2KHR",
            "vkGetImageMemoryRequirements2KHR", "vkBindBufferMemory2KHR", "vkBindImageMemory2KHR",
            "vkGetPhysicalDeviceMemoryProperties2KHR", "vkGetDeviceBufferMemoryRequirements",
            "vkGetDeviceImageMemoryRequirements", "vkGetMemoryWin32HandleKHR",
            "vkGetPhysicalDeviceProperties2KHR").map { I64.withName(it) }.toTypedArray(),
    )

    @JvmField public val poolCreateInfo: StructLayout = MemoryLayout.structLayout(
        I32.withName("memoryTypeIndex"), I32.withName("flags"), I64.withName("blockSize"),
        I64.withName("minBlockCount"), I64.withName("maxBlockCount"), F32.withName("priority"),
        MemoryLayout.paddingLayout(4), I64.withName("minAllocationAlignment"),
        I64.withName("pMemoryAllocateNext"),
    )

    public const val MAX_MEMORY_TYPES: Int = 32
    public const val MAX_MEMORY_HEAPS: Int = 16
    @JvmField public val totalStatistics: StructLayout = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(MAX_MEMORY_TYPES.toLong(), VmaLayouts.detailedStatistics).withName("memoryType"),
        MemoryLayout.sequenceLayout(MAX_MEMORY_HEAPS.toLong(), VmaLayouts.detailedStatistics).withName("memoryHeap"),
        VmaLayouts.detailedStatistics.withName("total"),
    )

    @JvmField public val defragmentationInfo: StructLayout = MemoryLayout.structLayout(
        I32.withName("flags"), MemoryLayout.paddingLayout(4), I64.withName("pool"),
        I64.withName("maxBytesPerPass"), I32.withName("maxAllocationsPerPass"),
        MemoryLayout.paddingLayout(4), I64.withName("pfnBreakCallback"),
        I64.withName("pBreakCallbackUserData"),
    )
    @JvmField public val defragmentationMove: StructLayout = MemoryLayout.structLayout(
        I32.withName("operation"), MemoryLayout.paddingLayout(4),
        I64.withName("srcAllocation"), I64.withName("dstTmpAllocation"),
    )
    @JvmField public val defragmentationPassMoveInfo: StructLayout = MemoryLayout.structLayout(
        I32.withName("moveCount"), MemoryLayout.paddingLayout(4), I64.withName("pMoves"),
    )
    @JvmField public val defragmentationStats: StructLayout = MemoryLayout.structLayout(
        I64.withName("bytesMoved"), I64.withName("bytesFreed"),
        I32.withName("allocationsMoved"), I32.withName("deviceMemoryBlocksFreed"),
    )
}

/**
 * Writers for the complete function-pointer table consumed by this module's
 * dynamic Vulkan build. Function addresses may come from Caelum Vulkan's
 * loader or compatible long-lived upcall stubs.
 */
public object VmaVulkanFunctions {
    public const val SLOT_COUNT: Int = 28
    public fun clear(segment: MemorySegment): Unit { segment.fill(0) }
    public fun set(segment: MemorySegment, slot: Int, address: Long): Unit {
        require(slot in 0 until SLOT_COUNT) { "slot $slot outside 0 until $SLOT_COUNT" }
        segment.set(ValueLayout.JAVA_LONG, slot * 8L, address)
    }
    public fun getInstanceProcAddr(segment: MemorySegment, address: Long): Unit = set(segment, 0, address)
    public fun getDeviceProcAddr(segment: MemorySegment, address: Long): Unit = set(segment, 1, address)

    /** Assigns this table to `VmaAllocatorCreateInfo::pVulkanFunctions` at its pinned ABI offset. */
    public fun attachToAllocatorCreateInfo(createInfo: MemorySegment, table: MemorySegment): Unit {
        require(table.address() != 0L) { "dynamic VMA requires a non-null VmaVulkanFunctions table" }
        require(table.get(ValueLayout.JAVA_LONG, 0) != 0L) { "vkGetInstanceProcAddr is required" }
        require(table.get(ValueLayout.JAVA_LONG, 8) != 0L) { "vkGetDeviceProcAddr is required" }
        createInfo.set(ValueLayout.JAVA_LONG, 56, table.address())
    }
}

public object VmaPoolCreateInfo {
    public fun set(segment: MemorySegment, memoryTypeIndex: Int, flags: Int = 0, blockSize: Long = 0,
        minBlockCount: Long = 0, maxBlockCount: Long = 0, priority: Float = 0f,
        minAllocationAlignment: Long = 0, memoryAllocateNext: Long = 0): Unit {
        segment.fill(0)
        segment.set(ValueLayout.JAVA_INT, 0, memoryTypeIndex); segment.set(ValueLayout.JAVA_INT, 4, flags)
        segment.set(ValueLayout.JAVA_LONG, 8, blockSize); segment.set(ValueLayout.JAVA_LONG, 16, minBlockCount)
        segment.set(ValueLayout.JAVA_LONG, 24, maxBlockCount); segment.set(ValueLayout.JAVA_FLOAT, 32, priority)
        segment.set(ValueLayout.JAVA_LONG, 40, minAllocationAlignment); segment.set(ValueLayout.JAVA_LONG, 48, memoryAllocateNext)
    }
}

public object VmaTotalStatistics {
    public fun memoryType(segment: MemorySegment, index: Int): MemorySegment = element(segment, index, 0, VmaAllocatorExtendedLayouts.MAX_MEMORY_TYPES)
    public fun memoryHeap(segment: MemorySegment, index: Int): MemorySegment = element(segment, index, 2048, VmaAllocatorExtendedLayouts.MAX_MEMORY_HEAPS)
    public fun total(segment: MemorySegment): MemorySegment = segment.asSlice(3072, VmaLayouts.detailedStatistics.byteSize())
    private fun element(segment: MemorySegment, index: Int, base: Long, count: Int): MemorySegment {
        require(index in 0 until count) { "index $index outside 0 until $count" }
        return segment.asSlice(base + index * VmaLayouts.detailedStatistics.byteSize(), VmaLayouts.detailedStatistics.byteSize())
    }
}

public object VmaDefragmentationInfo {
    public fun set(segment: MemorySegment, flags: Int = 0, pool: Long = 0, maxBytesPerPass: Long = 0,
        maxAllocationsPerPass: Int = 0, breakCallback: Long = 0, breakCallbackUserData: Long = 0): Unit {
        segment.fill(0); segment.set(ValueLayout.JAVA_INT, 0, flags); segment.set(ValueLayout.JAVA_LONG, 8, pool)
        segment.set(ValueLayout.JAVA_LONG, 16, maxBytesPerPass); segment.set(ValueLayout.JAVA_INT, 24, maxAllocationsPerPass)
        segment.set(ValueLayout.JAVA_LONG, 32, breakCallback); segment.set(ValueLayout.JAVA_LONG, 40, breakCallbackUserData)
    }
}

public object VmaDefragmentationPassMoveInfo {
    public fun moveCount(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 0)
    public fun moves(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 8)
}

public object VmaDefragmentationMove {
    public fun operation(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 0)
    public fun operation(segment: MemorySegment, value: Int): Unit = segment.set(ValueLayout.JAVA_INT, 0, value)
    public fun sourceAllocation(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 8)
    public fun destinationTemporaryAllocation(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 16)
}

public object VmaDefragmentationStats {
    public fun bytesMoved(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 0)
    public fun bytesFreed(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 8)
    public fun allocationsMoved(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 16)
    public fun deviceMemoryBlocksFreed(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 20)
}
