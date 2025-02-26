package org.echonolix.ktffi

import java.lang.foreign.MemoryLayout
import java.lang.foreign.StructLayout
import java.lang.foreign.UnionLayout
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