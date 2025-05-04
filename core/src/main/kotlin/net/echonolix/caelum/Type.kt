package net.echonolix.caelum

import java.lang.foreign.*
import java.lang.invoke.MethodHandle

public interface TypeDescriptor<T : NativeType> {
    public val layout: MemoryLayout
    public val arrayByteOffsetHandle: MethodHandle

    public abstract class Impl<T : NativeType>(override val layout: MemoryLayout) : TypeDescriptor<T> {
        final override val arrayByteOffsetHandle: MethodHandle by lazy {
            MemoryLayout.sequenceLayout(Long.MAX_VALUE / layout.byteSize(), layout)
                .byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
        }
    }
}

public interface NativeType {
    public val typeDescriptor: TypeDescriptor<*>

    public abstract class Impl<T : NativeType>(layout: MemoryLayout) : TypeDescriptor.Impl<T>(layout), NativeType {
        override val typeDescriptor: TypeDescriptor<T> = this
    }
}

private fun paddedStructLayout(vararg members: MemoryLayout): StructLayout {
    val newMembers = mutableListOf<MemoryLayout>()
    var maxMemberSize = 1L
    var currSize = 0L
    for (member in members) {
        val memberSize = member.byteSize()
        maxMemberSize = maxOf(maxMemberSize, memberSize)
        val mod = currSize % member.byteAlignment()
        if (mod != 0L) {
            val padding = member.byteAlignment() - mod
            newMembers.add(MemoryLayout.paddingLayout(padding))
            currSize += padding
        }
        currSize += memberSize
        newMembers.add(member)
    }
    val mod = currSize % maxMemberSize
    if (mod != 0L) {
        val padding = maxMemberSize - mod
        newMembers.add(MemoryLayout.paddingLayout(padding))
    }
    return MemoryLayout.structLayout(*newMembers.toTypedArray())
}

public interface NativeEnum<T> : NativeType {
    public val value: T
    public val nativeType: NativeType
}

public abstract class NativeStruct<T : NativeType> private constructor(override val layout: StructLayout) :
    NativeType.Impl<T>(layout), TypeDescriptor<T> {
    public constructor(vararg members: MemoryLayout) : this(
        paddedStructLayout(*members)
    )
}

public abstract class NativeUnion<T : NativeType> private constructor(override val layout: UnionLayout) :
    NativeType.Impl<T>(layout), TypeDescriptor<T> {
    public constructor(vararg members: MemoryLayout) : this(
        MemoryLayout.unionLayout(*members)
    )
}

public interface NativeFunction : NativeType {
    override val typeDescriptor: TypeDescriptorImpl<*>
    public val funcHandle: MethodHandle get() = typeDescriptor.upcallHandle.bindTo(this)

    public abstract class Impl(
        final override val funcHandle: MethodHandle
    ) : NativeFunction

    public abstract class TypeDescriptorImpl<T : NativeFunction>(
        public val name: String,
        public val upcallHandle: MethodHandle,
        public val returnType: TypeDescriptor<*>?,
        vararg parameters: TypeDescriptor<*>
    ) : NativeType.Impl<T>(ValueLayout.JAVA_BYTE), TypeDescriptor<T> {
        public val parameters: List<TypeDescriptor<*>> = parameters.toList()

        public val functionDescriptor: FunctionDescriptor = if (returnType == null) {
            FunctionDescriptor.ofVoid(
                *parameters.map { it.layout }.toTypedArray()
            )
        } else {
            FunctionDescriptor.of(
                returnType.layout,
                *parameters.map { it.layout }.toTypedArray()
            )
        }

        protected abstract val manager: Manager

        public abstract fun fromNativeData(value: MemorySegment): T

        public fun toNativeData(value: T): NativePointer<T> {
            return NativePointer(upcallStub(value).address())
        }

        public fun fromNativeData(value: NativePointer<T>): T {
            return fromNativeData(MemorySegment.ofAddress(value._address))
        }

        protected fun downcallHandle(functionAddress: MemorySegment): MethodHandle {
            return Linker.nativeLinker().downcallHandle(functionAddress, functionDescriptor)
        }

        protected fun upcallStub(function: T): MemorySegment {
            return Linker.nativeLinker().upcallStub(
                function.funcHandle,
                functionDescriptor,
                manager.stubAllocator
            )
        }
    }

    public abstract class Manager {
        public var stubAllocator: Arena = Arena.ofShared(); private set

        public fun freeFunctionStubs() {
            stubAllocator.close()
            stubAllocator = Arena.ofShared()
        }
    }
}

public interface AllocateOverLoad<T : NativeType> {
    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: Long): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: Int): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: ULong): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator, count: UInt): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: Long): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: Int): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: ULong): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator, count: UInt): NativeArray<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: Long): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: Int): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: ULong): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(count: UInt): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Long): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: Int): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: ULong): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(count: UInt): NativeArray<T> = throw UnsupportedOperationException("DON'T USE THIS")


    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(allocator: SegmentAllocator): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(allocator, count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(allocator: SegmentAllocator): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun malloc(): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    @Deprecated(
        "Use allocate() instead",
        ReplaceWith("allocate(count)"),
        DeprecationLevel.ERROR
    )
    public fun calloc(): NativeValue<T> =
        throw UnsupportedOperationException("DON'T USE THIS")

    public fun allocate(allocator: SegmentAllocator, count: Long): NativeArray<T>

    public fun allocate(allocator: SegmentAllocator): NativeValue<T>
}

public fun <T : NativeType> AllocateOverLoad<T>.allocate(allocator: SegmentAllocator, count: ULong): NativeArray<T> =
    allocate(allocator, count.toLong())

public fun <T : NativeType> AllocateOverLoad<T>.allocate(allocator: SegmentAllocator, count: UInt): NativeArray<T> =
    allocate(allocator, count.toLong())

public fun <T : NativeType> AllocateOverLoad<T>.allocate(allocator: SegmentAllocator, count: Int): NativeArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NativeType> AllocateOverLoad<T>.allocate(count: Long): NativeArray<T> =
    allocate(allocator, count)

context(allocator: SegmentAllocator)
public fun <T : NativeType> AllocateOverLoad<T>.allocate(count: ULong): NativeArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NativeType> AllocateOverLoad<T>.allocate(count: UInt): NativeArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NativeType> AllocateOverLoad<T>.allocate(count: Int): NativeArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NativeType> AllocateOverLoad<T>.allocate(): NativeValue<T> =
    allocate(allocator)