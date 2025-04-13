package net.echonolix.ktffi

import java.lang.foreign.*
import java.lang.foreign.StructLayout
import java.lang.foreign.UnionLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

interface TypeDescriptor<T : NativeType> {
    val layout: MemoryLayout
    val arrayByteOffsetHandle: MethodHandle

    abstract class Impl<T : NativeType>(override val layout: MemoryLayout) : TypeDescriptor<T> {
        final override val arrayByteOffsetHandle: MethodHandle by lazy {
            layout.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
        }
    }
}

interface NativeType {
    val typeDescriptor: TypeDescriptor<*>

    abstract class Impl<T : NativeType>(layout: MemoryLayout) : TypeDescriptor.Impl<T>(layout), NativeType {
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

abstract class NativeStruct<T : NativeType> private constructor(override val layout: StructLayout) :
    NativeType.Impl<T>(layout), TypeDescriptor<T> {
    constructor(vararg members: MemoryLayout) : this(
        paddedStructLayout(*members)
    )
}

abstract class NativeUnion<T : NativeType> private constructor(override val layout: UnionLayout) :
    NativeType.Impl<T>(layout), TypeDescriptor<T> {
    constructor(vararg members: MemoryLayout) : this(
        MemoryLayout.unionLayout(*members)
    )
}

abstract class NativeFunction<T : NativeType>(val returnType: NativeType?, vararg parameters: NativeType) :
    NativeType.Impl<T>(ValueLayout.JAVA_BYTE), TypeDescriptor<T> {

    val functionDescriptor = if (returnType == null) {
        FunctionDescriptor.ofVoid(
            *parameters.map { it.typeDescriptor.layout }.toTypedArray()
        )
    } else {
        FunctionDescriptor.of(
            returnType.typeDescriptor.layout,
            *parameters.map { it.typeDescriptor.layout }.toTypedArray()
        )
    }

    fun downcallHandle(functionAddress: MemorySegment): MethodHandle {
        return Linker.nativeLinker().downcallHandle(
            functionAddress,
            functionDescriptor
        )
    }
}