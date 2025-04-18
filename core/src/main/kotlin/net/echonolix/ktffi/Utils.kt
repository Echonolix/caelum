@file:Suppress("FunctionName")

package net.echonolix.ktffi

import java.lang.foreign.SegmentAllocator
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public typealias NativeChar = NativeInt8
public typealias NativeSize = NativeInt64
public typealias NativeInt = NativeInt32

public fun String.c_str(allocator: SegmentAllocator): NativePointer<NativeChar> =
    NativeArray<NativeChar>(allocator.allocateFrom(this)).ptr()

context(allocator: SegmentAllocator)
public fun String.c_str(): NativePointer<NativeChar> = c_str(allocator)

public fun Collection<String>.c_strs(allocator: SegmentAllocator): NativePointer<NativePointer<NativeChar>> {
    val arr = NativePointer.malloc<NativeChar>(allocator, this.size)
    this.forEachIndexed { index, str ->
        arr[index] = str.c_str(allocator)
    }
    return arr.ptr()
}

context(allocator: SegmentAllocator)
public fun Collection<String>.c_strs(): NativePointer<NativePointer<NativeChar>> = c_strs(allocator)

public inline fun <R> MemoryStack(block: MemoryStack.Frame.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return MemoryStack.stackPush().use {
        it.block()
    }
}

public inline fun <R> MemoryStack.Frame.MemoryStack(block: (MemoryStack.Frame).() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return this.push().use {
        it.block()
    }
}

@Suppress("UNCHECKED_CAST")
@UnsafeAPI
public fun <T : NativeType> reinterpretCast(pointer: NativePointer<*>): NativePointer<T> =
    pointer as NativePointer<T>

@Suppress("UNCHECKED_CAST")
@UnsafeAPI
public fun <T : NativeType> reinterpretCast(array: NativeArray<*>): NativeArray<T> =
    array as NativeArray<T>

@Suppress("UNCHECKED_CAST")
@UnsafeAPI
public fun <T : NativeType> reinterpretCast(value: NativeValue<*>): NativeValue<T> =
    value as NativeValue<T>