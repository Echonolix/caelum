package net.echonolix.caelum

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator

@Suppress("FunctionName")
public interface AllocateScope {
    public fun _malloc(layout: MemoryLayout): Long
    public fun _calloc(layout: MemoryLayout): Long
    public fun _malloc(layout: MemoryLayout, count: Long): Long
    public fun _calloc(layout: MemoryLayout, count: Long): Long
    public fun _allocateCString(value: String): MemorySegment
}

public fun <T : NType> AllocateScope.malloc(descriptor: NType.Descriptor<T>): NValue<T> =
    NValue(_malloc(descriptor.layout))

public fun <T : NType> AllocateScope.calloc(descriptor: NType.Descriptor<T>): NValue<T> =
    NValue(_calloc(descriptor.layout))

public fun <T : NType> AllocateScope.malloc(descriptor: NType.Descriptor<T>, count: Long): NArray<T> =
    NArray(_malloc(descriptor.layout, count), count)

public fun <T : NType> AllocateScope.calloc(descriptor: NType.Descriptor<T>, count: Long): NArray<T> =
    NArray(_calloc(descriptor.layout, count), count)

public abstract class WrappedAllocateScope : AllocateScope {
    protected abstract val segmentAllocator: SegmentAllocator

    override fun _malloc(layout: MemoryLayout): Long =
        segmentAllocator.allocate(layout).address()

    override fun _calloc(layout: MemoryLayout): Long =
        segmentAllocator.allocate(layout).fill(0).address()

    override fun _malloc(layout: MemoryLayout, count: Long): Long =
        segmentAllocator.allocate(layout, count).address()

    override fun _calloc(layout: MemoryLayout, count: Long): Long =
        segmentAllocator.allocate(layout, count).fill(0).address()

    override fun _allocateCString(value: String): MemorySegment =
        segmentAllocator.allocateFrom(value)

    public class Impl(
        override val segmentAllocator: SegmentAllocator
    ) : WrappedAllocateScope()
}

public fun SegmentAllocator.asAllocateScope(): AllocateScope =
    WrappedAllocateScope.Impl(this)

@JvmName("malloc114")
public fun <T : NType> NType.Descriptor<T>.malloc(allocator: AllocateScope): NValue<T> =
    allocator.malloc(this)

@JvmName("malloc514")
context(allocator: AllocateScope)
public fun <T : NType> NType.Descriptor<T>.malloc(): NValue<T> =
    allocator.malloc(this)

@JvmName("malloc1919")
public fun <T : NType> NType.Descriptor<T>.malloc(allocator: AllocateScope, count: Long): NArray<T> =
    allocator.malloc(this, count)

@JvmName("malloc810")
context(allocator: AllocateScope)
public fun <T : NType> NType.Descriptor<T>.malloc(count: Long): NArray<T> =
    allocator.malloc(this, count)

@JvmName("calloc114")
public fun <T : NType> NType.Descriptor<T>.calloc(allocator: AllocateScope): NValue<T> =
    allocator.calloc(this)

@JvmName("calloc514")
context(allocator: AllocateScope)
public fun <T : NType> NType.Descriptor<T>.calloc(): NValue<T> =
    allocator.calloc(this)

@JvmName("calloc1919")
public fun <T : NType> NType.Descriptor<T>.calloc(allocator: AllocateScope, count: Long): NArray<T> =
    allocator.calloc(this, count)

@JvmName("calloc810")
context(allocator: AllocateScope)
public fun <T : NType> NType.Descriptor<T>.calloc(count: Long): NArray<T> =
    allocator.calloc(this, count)

public interface CustomAllocateOnly<T : NType> {
    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: AllocateScope, count: Long): NArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: AllocateScope, count: Long): NArray<T> =
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
    public fun malloc(allocator: AllocateScope): NValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: AllocateScope): NValue<T> =
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

    public fun allocate(allocator: AllocateScope): NValue<T>

    public fun allocate(allocator: AllocateScope, count: Long): NArray<T>
}

context(allocator: AllocateScope)
public fun <T : NType> CustomAllocateOnly<T>.allocate(): NValue<T> =
    allocate(allocator)

context(allocator: AllocateScope)
public fun <T : NType> CustomAllocateOnly<T>.allocate(count: Long): NArray<T> =
    allocate(allocator, count)