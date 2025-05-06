package net.echonolix.caelum

import java.lang.foreign.MemoryLayout
import java.lang.foreign.SegmentAllocator

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator): NValue<T> =
    NValue(allocator.allocate(layout))

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.malloc(): NValue<T> =
    NValue(allocator.allocate(layout))

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator): NValue<T> =
    NValue(allocator.allocate(layout).apply { fill(0) })

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.calloc(): NValue<T> =
    NValue(allocator.allocate(layout).apply { fill(0) })


public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count))

public fun <T : NType> NType.Descriptor<T>.malloc(allocator: SegmentAllocator, count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.malloc(count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.malloc(count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.malloc(count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count))

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.malloc(count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()))

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count).apply { fill(0) })

public fun <T : NType> NType.Descriptor<T>.calloc(allocator: SegmentAllocator, count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.calloc(count: ULong): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.calloc(count: UInt): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.calloc(count: Long): NArray<T> =
    NArray(allocator.allocate(layout, count).apply { fill(0) })

context(allocator: MemoryStack.Frame)
public fun <T : NType> NType.Descriptor<T>.calloc(count: Int): NArray<T> =
    NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })


public interface AllocOverload<T : NType> {
    public val layoutDelegate: MemoryLayout

    public fun malloc(allocator: SegmentAllocator): NValue<T> =
        NValue(allocator.allocate(layoutDelegate))

    context(allocator: MemoryStack.Frame)
    public fun malloc(): NValue<T> =
        NValue(allocator.allocate(layoutDelegate))

    public fun calloc(allocator: SegmentAllocator): NValue<T> =
        NValue(allocator.allocate(layoutDelegate).apply { fill(0) })

    context(allocator: MemoryStack.Frame)
    public fun calloc(): NValue<T> =
        NValue(allocator.allocate(layoutDelegate).apply { fill(0) })

    public fun malloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()))

    public fun malloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()))

    public fun malloc(allocator: SegmentAllocator, count: Long): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count))

    public fun malloc(allocator: SegmentAllocator, count: Int): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()))

    context(allocator: MemoryStack.Frame)
    public fun malloc(count: ULong): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()))

    context(allocator: MemoryStack.Frame)
    public fun malloc(count: UInt): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()))

    context(allocator: MemoryStack.Frame)
    public fun malloc(count: Long): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count))

    context(allocator: MemoryStack.Frame)
    public fun malloc(count: Int): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()))

    public fun calloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()).apply { fill(0) })

    public fun calloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()).apply { fill(0) })

    public fun calloc(allocator: SegmentAllocator, count: Long): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count).apply { fill(0) })

    public fun calloc(allocator: SegmentAllocator, count: Int): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()).apply { fill(0) })

    context(allocator: MemoryStack.Frame)
    public fun calloc(count: ULong): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()).apply { fill(0) })

    context(allocator: MemoryStack.Frame)
    public fun calloc(count: UInt): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()).apply { fill(0) })

    context(allocator: MemoryStack.Frame)
    public fun calloc(count: Long): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count).apply { fill(0) })

    context(allocator: MemoryStack.Frame)
    public fun calloc(count: Int): NArray<T> =
        NArray(allocator.allocate(layoutDelegate, count.toLong()).apply { fill(0) })
}


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
    public fun malloc(allocator: SegmentAllocator, count: Int): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
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
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: Int): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: ULong): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: UInt): NArray<T> =
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
    public fun malloc(count: Int): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: ULong): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: UInt): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Long): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Int): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: ULong): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: UInt): NArray<T> = throw UnsupportedOperationException("DON'T USE THIS")


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

public fun <T : NType> CustomAllocateOnly<T>.allocate(
    allocator: SegmentAllocator,
    count: ULong
): NArray<T> =
    allocate(allocator, count.toLong())

public fun <T : NType> CustomAllocateOnly<T>.allocate(allocator: SegmentAllocator, count: UInt): NArray<T> =
    allocate(allocator, count.toLong())

public fun <T : NType> CustomAllocateOnly<T>.allocate(allocator: SegmentAllocator, count: Int): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: MemoryStack.Frame)
public fun <T : NType> CustomAllocateOnly<T>.allocate(count: Long): NArray<T> =
    allocate(allocator, count)

context(allocator: MemoryStack.Frame)
public fun <T : NType> CustomAllocateOnly<T>.allocate(count: ULong): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: MemoryStack.Frame)
public fun <T : NType> CustomAllocateOnly<T>.allocate(count: UInt): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: MemoryStack.Frame)
public fun <T : NType> CustomAllocateOnly<T>.allocate(count: Int): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: MemoryStack.Frame)
public fun <T : NType> CustomAllocateOnly<T>.allocate(): NValue<T> =
    allocate(allocator)