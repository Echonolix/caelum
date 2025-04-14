package net.echonolix.ktffi

import java.lang.foreign.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.jvm.javaMethod

public interface TypeDescriptor<T : NativeType> {
    public val layout: MemoryLayout
    public val arrayByteOffsetHandle: MethodHandle

    public val fromNativeDataMH: MethodHandle
    public val toNativeDataMH: MethodHandle

    public abstract class Impl<T : NativeType>(override val layout: MemoryLayout) : TypeDescriptor<T> {
        final override val arrayByteOffsetHandle: MethodHandle by lazy {
            layout.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
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
    return MemoryLayout.structLayout(*newMembers.toTypedArray())
}

public abstract class NativeStruct<T : NativeType> private constructor(override val layout: StructLayout) :
    NativeType.Impl<T>(layout), TypeDescriptor<T> {
    public constructor(vararg members: MemoryLayout) : this(
        paddedStructLayout(*members)
    )

    override val fromNativeDataMH: MethodHandle
        get() = throw UnsupportedOperationException()

    override val toNativeDataMH: MethodHandle
        get() = throw UnsupportedOperationException()
}

public abstract class NativeUnion<T : NativeType> private constructor(override val layout: UnionLayout) :
    NativeType.Impl<T>(layout), TypeDescriptor<T> {
    public constructor(vararg members: MemoryLayout) : this(
        MemoryLayout.unionLayout(*members)
    )

    override val fromNativeDataMH: MethodHandle
        get() = throw UnsupportedOperationException()

    override val toNativeDataMH: MethodHandle
        get() = throw UnsupportedOperationException()
}

public interface NativeFunction : NativeType {
    override val typeDescriptor: TypeDescriptorImpl<*>
    public val funcHandle: MethodHandle get() = typeDescriptor.filteredUpcallHandle.bindTo(this)

    public abstract class Impl(
        final override val funcHandle: MethodHandle
    ) : NativeFunction

    public abstract class TypeDescriptorImpl<T : NativeFunction>(
        public val baseUpcallHandle: MethodHandle,
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

        public val downcallArgumentFilters: Array<MethodHandle> = parameters.map {
            it.fromNativeDataMH
        }.toTypedArray()

        public val upcallArgumentFilters: Array<MethodHandle> = parameters.map {
            it.toNativeDataMH
        }.toTypedArray()

        public val filteredUpcallHandle: MethodHandle = MethodHandles.filterArguments(
            baseUpcallHandle,
            1,
            *upcallArgumentFilters
        )

        override val toNativeDataMH: MethodHandle =
            MethodHandles.lookup().unreflect(::toNativeData.javaMethod).bindTo(this)
        override val fromNativeDataMH: MethodHandle =
            MethodHandles.lookup().unreflect(::fromNativeData0.javaMethod).bindTo(this)

        public fun toNativeData(value: T): NativePointer<T> {
            return NativePointer(upcallStub(value).address())
        }

        public fun fromNativeData0(value: NativePointer<T>): T {
            return fromNativeData(value)
        }

        public abstract fun fromNativeData(value: MemorySegment): T

        public fun fromNativeData(value: NativePointer<T>): T {
            return fromNativeData(MemorySegment.ofAddress(value._address))
        }

        public fun downcallHandle(functionAddress: Long): MethodHandle {
            return downcallHandle(MemorySegment.ofAddress(functionAddress))
        }

        public fun downcallHandle(functionAddress: MemorySegment): MethodHandle {
            return MethodHandles.filterArguments(
                Linker.nativeLinker().downcallHandle(functionAddress, functionDescriptor),
                0,
                *downcallArgumentFilters
            )
        }

        public fun upcallStub(function: T): MemorySegment {
            return Linker.nativeLinker().upcallStub(
                function.funcHandle,
                functionDescriptor,
                Arena.ofAuto()
            )
        }
    }
}