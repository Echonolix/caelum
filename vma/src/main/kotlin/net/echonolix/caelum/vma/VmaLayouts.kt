package net.echonolix.caelum.vma

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout

/** ABI layouts for the structures used by the VMA 3.4.0 virtual allocator. */
public object VmaLayouts {
    private val U32 = ValueLayout.JAVA_INT
    private val U64 = ValueLayout.JAVA_LONG
    private val PTR = ValueLayout.JAVA_LONG

    @JvmField public val virtualBlockCreateInfo: StructLayout = MemoryLayout.structLayout(
        U64.withName("size"), U32.withName("flags"), MemoryLayout.paddingLayout(4),
        PTR.withName("pAllocationCallbacks")
    )
    @JvmField public val virtualAllocationCreateInfo: StructLayout = MemoryLayout.structLayout(
        U64.withName("size"), U64.withName("alignment"), U32.withName("flags"),
        MemoryLayout.paddingLayout(4), PTR.withName("pUserData")
    )
    @JvmField public val virtualAllocationInfo: StructLayout = MemoryLayout.structLayout(
        U64.withName("offset"), U64.withName("size"), PTR.withName("pUserData")
    )
    @JvmField public val statistics: StructLayout = MemoryLayout.structLayout(
        U32.withName("blockCount"), U32.withName("allocationCount"),
        U64.withName("blockBytes"), U64.withName("allocationBytes")
    )
    @JvmField public val detailedStatistics: StructLayout = MemoryLayout.structLayout(
        statistics.withName("statistics"), U32.withName("unusedRangeCount"),
        MemoryLayout.paddingLayout(4), U64.withName("allocationSizeMin"),
        U64.withName("allocationSizeMax"), U64.withName("unusedRangeSizeMin"),
        U64.withName("unusedRangeSizeMax")
    )
}

/** Field accessors for [VmaLayouts.virtualBlockCreateInfo]. */
public object VmaVirtualBlockCreateInfo {
    public fun set(segment: MemorySegment, size: Long, flags: Int = 0, allocationCallbacks: Long = 0) {
        segment.set(ValueLayout.JAVA_LONG, 0, size)
        segment.set(ValueLayout.JAVA_INT, 8, flags)
        segment.set(ValueLayout.JAVA_LONG, 16, allocationCallbacks)
    }
}

/** Field accessors for [VmaLayouts.virtualAllocationCreateInfo]. */
public object VmaVirtualAllocationCreateInfo {
    public fun set(segment: MemorySegment, size: Long, alignment: Long = 0, flags: Int = 0, userData: Long = 0) {
        segment.set(ValueLayout.JAVA_LONG, 0, size)
        segment.set(ValueLayout.JAVA_LONG, 8, alignment)
        segment.set(ValueLayout.JAVA_INT, 16, flags)
        segment.set(ValueLayout.JAVA_LONG, 24, userData)
    }
}

/** Field accessors for [VmaLayouts.virtualAllocationInfo]. */
public object VmaVirtualAllocationInfo {
    public fun offset(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 0)
    public fun size(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 8)
    public fun userData(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 16)
}

/** Field accessors for [VmaLayouts.statistics]. */
public object VmaStatistics {
    public fun blockCount(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 0)
    public fun allocationCount(segment: MemorySegment): Int = segment.get(ValueLayout.JAVA_INT, 4)
    public fun blockBytes(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 8)
    public fun allocationBytes(segment: MemorySegment): Long = segment.get(ValueLayout.JAVA_LONG, 16)
}

/** Constants defined by the VMA 3.4.0 virtual allocation API. */
public object VmaVirtualFlags {
    public const val BLOCK_LINEAR_ALGORITHM: Int = 0x00000001
    public const val ALLOCATION_UPPER_ADDRESS: Int = 0x00000040
    public const val ALLOCATION_STRATEGY_MIN_MEMORY: Int = 0x00010000
    public const val ALLOCATION_STRATEGY_MIN_TIME: Int = 0x00020000
    public const val ALLOCATION_STRATEGY_MIN_OFFSET: Int = 0x00040000
}
