package net.echonolix.caelum

import java.lang.foreign.MemoryLayout
import java.lang.foreign.SegmentAllocator

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator): NValue<T> =
    NValue(allocator.allocate(layout))

context(allocator: MemoryStack)
public fun <T : NType> NType.Descriptor<T>.malloc(): NValue<T> =
    NValue(allocator.allocate(layout))

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator): NValue<T> =
    NValue(allocator.allocate(layout).apply { fill(0) })

context(allocator: MemoryStack)
public fun <T : NType> NType.Descriptor<T>.calloc(): NValue<T> =
    NValue(allocator.allocate(layout).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count))

context(allocator: MemoryStack)
public fun <T : NType> NType.Descriptor<T>.malloc(count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count))

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: MemoryStack)
public fun <T : NType> NType.Descriptor<T>.calloc(count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count).apply { fill(0) })

public interface CustomAllocateOnly<T : NType> {
    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: Long): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: Long): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: Long): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Long): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")


    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator): NValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator): NValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(): NValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(): NValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    public fun allocate(allocator: SegmentAllocator, count: Long): NArray<T>

    public fun allocate(allocator: SegmentAllocator): NValue<T>
}

context(allocator: MemoryStack)
public fun <T : NType> CustomAllocateOnly<T>.allocate(count: Long): NArray<T> =
    allocate(allocator, count)

context(allocator: MemoryStack)
public fun <T : NType> CustomAllocateOnly<T>.allocate(): NValue<T> =
    allocate(allocator)