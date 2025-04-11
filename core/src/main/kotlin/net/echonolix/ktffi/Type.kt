package net.echonolix.ktffi

import java.lang.foreign.*
import java.lang.foreign.StructLayout
import java.lang.foreign.UnionLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

interface NativeType {
    val layout: MemoryLayout
    val arrayByteOffsetHandle: MethodHandle
}

sealed class NativeTypeImpl(override val layout: MemoryLayout) : NativeType {
    final override val arrayByteOffsetHandle: MethodHandle =
        layout.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
}

abstract class NativeStruct(override val layout: StructLayout) : NativeTypeImpl(layout)
abstract class NativeUnion(override val layout: UnionLayout) : NativeTypeImpl(layout)
abstract class NativeFunction(val returnType: NativeType?, vararg parameters: NativeType) :
    NativeTypeImpl(ValueLayout.JAVA_BYTE) {

    val functionDescriptor = if (returnType == null) {
        FunctionDescriptor.ofVoid(
            *parameters.map { it.layout }.toTypedArray()
        )
    } else {
        FunctionDescriptor.of(
            returnType.layout,
            *parameters.map { it.layout }.toTypedArray()
        )
    }

    fun downcallHandle(functionAddress: MemorySegment): MethodHandle {
        return Linker.nativeLinker().downcallHandle(
            functionAddress,
            functionDescriptor
        )
    }
}