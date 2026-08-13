package net.echonolix.caelum.vma

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout

/** 64-bit native ABI layouts used by the core VMA 3.4.0 allocator API. */
public object VmaAllocatorLayouts {
    private val I32 = ValueLayout.JAVA_INT
    private val I64 = ValueLayout.JAVA_LONG
    private val F32 = ValueLayout.JAVA_FLOAT

    /** `VmaAllocatorCreateInfo`, with `VMA_EXTERNAL_MEMORY` enabled (the VMA default). */
    @JvmField public val allocatorCreateInfo: StructLayout = MemoryLayout.structLayout(
        I32.withName("flags"), MemoryLayout.paddingLayout(4),
        I64.withName("physicalDevice"), I64.withName("device"),
        I64.withName("preferredLargeHeapBlockSize"),
        I64.withName("pAllocationCallbacks"), I64.withName("pDeviceMemoryCallbacks"),
        I64.withName("pHeapSizeLimit"), I64.withName("pVulkanFunctions"),
        I64.withName("instance"), I32.withName("vulkanApiVersion"), MemoryLayout.paddingLayout(4),
        I64.withName("pTypeExternalMemoryHandleTypes"),
    )

    @JvmField public val allocatorInfo: StructLayout = MemoryLayout.structLayout(
        I64.withName("instance"), I64.withName("physicalDevice"), I64.withName("device"),
    )

    @JvmField public val memoryRequirements: StructLayout = MemoryLayout.structLayout(
        I64.withName("size"), I64.withName("alignment"), I32.withName("memoryTypeBits"),
        MemoryLayout.paddingLayout(4),
    )

    @JvmField public val allocationCreateInfo: StructLayout = MemoryLayout.structLayout(
        I32.withName("flags"), I32.withName("usage"), I32.withName("requiredFlags"),
        I32.withName("preferredFlags"), I32.withName("memoryTypeBits"), MemoryLayout.paddingLayout(4),
        I64.withName("pool"), I64.withName("pUserData"), I32.withName("priority"),
        MemoryLayout.paddingLayout(4), I64.withName("minAlignment"),
    )

    @JvmField public val allocationInfo: StructLayout = MemoryLayout.structLayout(
        I32.withName("memoryType"), MemoryLayout.paddingLayout(4), I64.withName("deviceMemory"),
        I64.withName("offset"), I64.withName("size"), I64.withName("pMappedData"),
        I64.withName("pUserData"), I64.withName("pName"),
    )

    @JvmField public val allocationInfo2: StructLayout = MemoryLayout.structLayout(
        allocationInfo.withName("allocationInfo"), I64.withName("blockSize"),
        I32.withName("dedicatedMemory"), MemoryLayout.paddingLayout(4),
    )

    @JvmField public val budget: StructLayout = MemoryLayout.structLayout(
        VmaLayouts.statistics.withName("statistics"), I64.withName("usage"), I64.withName("budget"),
    )

    public const val MAX_MEMORY_HEAPS: Int = 16
    @JvmField public val heapBudgets: MemoryLayout = MemoryLayout.sequenceLayout(MAX_MEMORY_HEAPS.toLong(), budget)
}

/** Field writers for [VmaAllocatorLayouts.allocatorCreateInfo]. Vulkan handles are raw 64-bit values. */
public object VmaAllocatorCreateInfo {
    /**
     * Initializes `VmaAllocatorCreateInfo` for Caelum's dynamic-Vulkan VMA binary.
     * [vulkanFunctions] must point to a live table initialized through
     * [VmaVulkanFunctions]; the table and its upcall stubs must outlive the allocator.
     */
    public fun set(
        segment: MemorySegment,
        physicalDevice: Long,
        device: Long,
        instance: Long,
        flags: Int = 0,
        preferredLargeHeapBlockSize: Long = 0,
        allocationCallbacks: Long = 0,
        deviceMemoryCallbacks: Long = 0,
        heapSizeLimit: Long = 0,
        vulkanFunctions: Long,
        vulkanApiVersion: Int = 0,
        typeExternalMemoryHandleTypes: Long = 0,
    ): Unit {
        segment.fill(0)
        segment.set(ValueLayout.JAVA_INT, 0, flags)
        segment.set(ValueLayout.JAVA_LONG, 8, physicalDevice)
        segment.set(ValueLayout.JAVA_LONG, 16, device)
        segment.set(ValueLayout.JAVA_LONG, 24, preferredLargeHeapBlockSize)
        segment.set(ValueLayout.JAVA_LONG, 32, allocationCallbacks)
        segment.set(ValueLayout.JAVA_LONG, 40, deviceMemoryCallbacks)
        segment.set(ValueLayout.JAVA_LONG, 48, heapSizeLimit)
        segment.set(ValueLayout.JAVA_LONG, 56, vulkanFunctions)
        segment.set(ValueLayout.JAVA_LONG, 64, instance)
        segment.set(ValueLayout.JAVA_INT, 72, vulkanApiVersion)
        segment.set(ValueLayout.JAVA_LONG, 80, typeExternalMemoryHandleTypes)
    }
}

/** Field writers for [VmaAllocatorLayouts.allocationCreateInfo]. */
public object VmaAllocationCreateInfo {
    public fun set(
        segment: MemorySegment,
        flags: Int = 0,
        usage: Int = 0,
        requiredFlags: Int = 0,
        preferredFlags: Int = 0,
        memoryTypeBits: Int = 0,
        pool: Long = 0,
        userData: Long = 0,
        priority: Float = 0f,
        minAlignment: Long = 0,
    ): Unit {
        segment.fill(0)
        segment.set(ValueLayout.JAVA_INT, 0, flags)
        segment.set(ValueLayout.JAVA_INT, 4, usage)
        segment.set(ValueLayout.JAVA_INT, 8, requiredFlags)
        segment.set(ValueLayout.JAVA_INT, 12, preferredFlags)
        segment.set(ValueLayout.JAVA_INT, 16, memoryTypeBits)
        segment.set(ValueLayout.JAVA_LONG, 24, pool)
        segment.set(ValueLayout.JAVA_LONG, 32, userData)
        segment.set(ValueLayout.JAVA_FLOAT, 40, priority)
        segment.set(ValueLayout.JAVA_LONG, 48, minAlignment)
    }
}

/** Read-only accessors for [VmaAllocatorLayouts.allocationInfo]. */
public object VmaAllocationInfo {
    public fun memoryType(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 0)
    public fun deviceMemory(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 8)
    public fun offset(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 16)
    public fun size(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 24)
    public fun mappedData(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 32)
    public fun userData(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 40)
    public fun name(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 48)
}
