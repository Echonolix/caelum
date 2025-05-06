package net.echonolix.caelum

import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

public sealed interface NType {
    public val typeDescriptor: Descriptor<*>

    public sealed interface Descriptor<T : NType> {
        public val layout: MemoryLayout
        public val arrayByteOffsetHandle: MethodHandle

        public sealed class Impl<T : NType>(override val layout: MemoryLayout) : Descriptor<T> {
            final override val arrayByteOffsetHandle: MethodHandle by lazy {
                MemoryLayout.sequenceLayout(Long.MAX_VALUE / layout.byteSize(), layout)
                    .byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
            }
        }
    }
}

public interface NPrimitive<N : Any, K : Any> : NType {
    public override val typeDescriptor: Descriptor<*, N, K>

    public interface NativeData<T : NPrimitive<N, *>, N : Any> : NType.Descriptor<T> {
        public val valueVarHandle: VarHandle
        public val arrayVarHandle: VarHandle

        public fun arrayGetElement(array: NArray<out T>, index: Long): N
        public fun arraySetElement(array: NArray<out T>, index: Long, value: N)
        public fun pointerGetElement(pointer: NPointer<out T>, index: Long): N
        public fun pointerSetElement(pointer: NPointer<out T>, index: Long, value: N)

        public fun valueGetValue(value: NValue<out T>): N
        public fun valueSetValue(value: NValue<out T>, newValue: N)
        public fun pointerGetValue(pointer: NPointer<out T>): N
        public fun pointerSetValue(pointer: NPointer<out T>, newValue: N)

        public sealed class Impl<T : NPrimitive<N, *>, N : Any>(override val layout: ValueLayout) :
            NType.Descriptor.Impl<T>(layout), NativeData<T, N> {
            public final override val valueVarHandle: VarHandle = layout.varHandle()
            public final override val arrayVarHandle: VarHandle = layout.arrayElementVarHandle()
        }
    }

    public interface KotlinApi<N : Any, K : Any> {
        public fun fromNativeData(value: N): K
        public fun toNativeData(value: K): N
    }

    public interface Descriptor<T : NPrimitive<N, K>, N : Any, K : Any> : NativeData<T, N>, KotlinApi<N, K>

    public interface TypeObject<T : NPrimitive<N, K>, N : Any, K : Any> : NPrimitive<N, K>, Descriptor<T, N, K> {
        override val typeDescriptor: TypeObject<T, N, K> get() = this
    }
}

public interface NComposite : NType {
    public override val typeDescriptor: Descriptor<*>

    public abstract class Impl<T : NComposite>(layout: MemoryLayout) : NComposite, Descriptor.Impl<T>(layout) {
        override val typeDescriptor: Impl<T> = this
    }

    public interface Descriptor<T : NComposite> : NType.Descriptor<T> {
        public sealed class Impl<T : NComposite>(layout: MemoryLayout) : NType.Descriptor.Impl<T>(layout), Descriptor<T>
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

public interface NEnum<T : NEnum<T, N>, N : Any> : NPrimitive<N, T> {
    public val value: N
    public val nType: NPrimitive<N, N>
    public override val typeDescriptor: Descriptor<T, N>

    public interface Descriptor<T : NEnum<T, N>, N : Any> : NPrimitive.Descriptor<T, N, T>

    public interface TypeObject<T : NEnum<T, N>, N : Any> : NPrimitive.TypeObject<T, N, T>, Descriptor<T, N>
}

public abstract class NStruct<T : NComposite> private constructor(override val layout: StructLayout) :
    NComposite.Impl<T>(layout) {
    public constructor(vararg members: MemoryLayout) : this(
        paddedStructLayout(*members)
    )
}

public abstract class NUnion<T : NComposite> private constructor(override val layout: UnionLayout) :
    NComposite.Impl<T>(layout) {
    public constructor(vararg members: MemoryLayout) : this(
        MemoryLayout.unionLayout(*members)
    )
}

public interface NFunction : NComposite {
    override val typeDescriptor: Descriptor<*>
    public val funcHandle: MethodHandle get() = typeDescriptor.upcallHandle.bindTo(this)

    public abstract class Impl(
        final override val funcHandle: MethodHandle
    ) : NFunction

    public abstract class Descriptor<T : NFunction>(
        public val name: String,
        public val invokeNativeFunc: KFunction<*>,
        public val returnType: NType.Descriptor<*>?,
        vararg parameters: NType.Descriptor<*>
    ) : NComposite.Impl<T>(ValueLayout.JAVA_BYTE), NType.Descriptor<T> {
        public val upcallHandle: MethodHandle = MethodHandles.lookup().unreflect(invokeNativeFunc.javaMethod)
        public val parameters: List<NType.Descriptor<*>> = parameters.toList()

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

        protected open val manager: Manager
            get() = Manager.Global

        public abstract fun fromNativeData(value: MemorySegment): T

        public fun toNativeData(value: T): NPointer<T> {
            return NPointer(upcallStub(value).address())
        }

        public fun fromNativeData(value: NPointer<T>): T {
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

    public interface Manager {
        public val stubAllocator: Arena

        public abstract class Impl : Manager {
            public final override var stubAllocator: Arena = Arena.ofShared(); private set

            public fun freeFunctionStubs() {
                stubAllocator.close()
                stubAllocator = Arena.ofShared()
            }
        }

        public object Global : Manager {
            public override val stubAllocator: Arena = Arena.ofShared()
        }
    }
}

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


public interface AllocateOverLoad<T : NType> {
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

public fun <T : NType> AllocateOverLoad<T>.allocate(
    allocator: SegmentAllocator,
    count: ULong
): NArray<T> =
    allocate(allocator, count.toLong())

public fun <T : NType> AllocateOverLoad<T>.allocate(allocator: SegmentAllocator, count: UInt): NArray<T> =
    allocate(allocator, count.toLong())

public fun <T : NType> AllocateOverLoad<T>.allocate(allocator: SegmentAllocator, count: Int): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NType> AllocateOverLoad<T>.allocate(count: Long): NArray<T> =
    allocate(allocator, count)

context(allocator: SegmentAllocator)
public fun <T : NType> AllocateOverLoad<T>.allocate(count: ULong): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NType> AllocateOverLoad<T>.allocate(count: UInt): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NType> AllocateOverLoad<T>.allocate(count: Int): NArray<T> =
    allocate(allocator, count.toLong())

context(allocator: SegmentAllocator)
public fun <T : NType> AllocateOverLoad<T>.allocate(): NValue<T> =
    allocate(allocator)