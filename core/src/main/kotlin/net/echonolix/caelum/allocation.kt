package net.echonolix.caelum

import net.echonolix.caelum.APIHelper.`_$OMNI_SEGMENT$_`
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import kotlin.contracts.contract

public fun <T : NType> AllocateScope.malloc(descriptor: NType.Descriptor<T>): NValue<T> =
    NValue(_malloc(descriptor.layout.byteSize(), descriptor.layout.byteAlignment()))

public fun <T : NType> AllocateScope.calloc(descriptor: NType.Descriptor<T>): NValue<T> =
    NValue(_calloc(descriptor.layout.byteSize(), descriptor.layout.byteAlignment()))

public fun <T : NType> AllocateScope.malloc(descriptor: NType.Descriptor<T>, count: Long): NArray<T> =
    NArray(_malloc(descriptor.layout.byteSize() * count, descriptor.layout.byteAlignment()), count)

public fun <T : NType> AllocateScope.calloc(descriptor: NType.Descriptor<T>, count: Long): NArray<T> =
    NArray(_calloc(descriptor.layout.byteSize() * count, descriptor.layout.byteAlignment()), count)

public fun SegmentAllocator.asAllocateScope(): AllocateScope =
    WrappedAllocateScope.Impl(this)

public inline fun <R> Arena.useAllocateScope(block: AllocateScope.() -> R): R {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }

    return use {
        WrappedAllocateScope.Impl(this).block()
    }
}

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

@OptIn(UnsafeAPI::class)
public fun <T : NGroup> T.copyOf(allocator: AllocateScope, value: NValue<T>): NValue<T> {
    val newValue = reinterpret_cast<T>(allocator.malloc(this.typeDescriptor))
    MemorySegment.copy(
        `_$OMNI_SEGMENT$_`,
        value._address,
        `_$OMNI_SEGMENT$_`,
        newValue._address,
        typeDescriptor.layout.byteSize()
    )
    return newValue
}

@OptIn(UnsafeAPI::class)
public fun <T : NGroup> T.copyOf(allocator: AllocateScope, array: NArray<T>): NValue<T> {
    val newValue = reinterpret_cast<T>(allocator.malloc(this.typeDescriptor))
    MemorySegment.copy(
        `_$OMNI_SEGMENT$_`,
        array._address,
        `_$OMNI_SEGMENT$_`,
        newValue._address,
        array.count * typeDescriptor.layout.byteSize()
    )
    return newValue
}

@OptIn(UnsafeAPI::class)
public fun <T : NGroup> T.copyOf(allocator: AllocateScope, ptr: NPointer<T>): NValue<T> {
    val newValue = reinterpret_cast<T>(allocator.malloc(this.typeDescriptor))
    MemorySegment.copy(
        `_$OMNI_SEGMENT$_`,
        ptr._address,
        `_$OMNI_SEGMENT$_`,
        newValue._address,
        typeDescriptor.layout.byteSize()
    )
    return newValue
}

@OptIn(UnsafeAPI::class)
public fun <T : NGroup> T.copyOf(allocator: AllocateScope, ptr: NPointer<T>, count: Long): NValue<T> {
    val newValue = reinterpret_cast<T>(allocator.malloc(this.typeDescriptor))
    MemorySegment.copy(
        `_$OMNI_SEGMENT$_`,
        ptr._address,
        `_$OMNI_SEGMENT$_`,
        newValue._address,
        count * typeDescriptor.layout.byteSize()
    )
    return newValue
}