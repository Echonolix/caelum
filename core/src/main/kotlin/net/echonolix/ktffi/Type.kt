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
        final override val arrayByteOffsetHandle: MethodHandle =
            layout.byteOffsetHandle(MemoryLayout.PathElement.sequenceElement())
    }
}

interface NativeType {
    val descriptor: TypeDescriptor<*>
    abstract class Impl<T : NativeType>(layout: MemoryLayout) : NativeType {
        override val descriptor: TypeDescriptor<T> = object : TypeDescriptor.Impl<T>(layout) {}
    }
}

abstract class NativeStruct<T : NativeType>(override val layout: StructLayout) : NativeType.Impl<T>(layout), TypeDescriptor<T>
abstract class NativeUnion<T : NativeType>(override val layout: UnionLayout) : NativeType.Impl<T>(layout), TypeDescriptor<T>
abstract class NativeFunction<T : NativeType>(val returnType: NativeType?, vararg parameters: NativeType) :
    NativeType.Impl<T>(ValueLayout.JAVA_BYTE), TypeDescriptor<T> {

    val functionDescriptor = if (returnType == null) {
        FunctionDescriptor.ofVoid(
            *parameters.map { it.descriptor.layout }.toTypedArray()
        )
    } else {
        FunctionDescriptor.of(
            returnType.descriptor.layout,
            *parameters.map { it.descriptor.layout }.toTypedArray()
        )
    }

    fun downcallHandle(functionAddress: MemorySegment): MethodHandle {
        return Linker.nativeLinker().downcallHandle(
            functionAddress,
            functionDescriptor
        )
    }
}