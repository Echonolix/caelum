@file:Suppress("FunctionName")

package net.echonolix.ktffi

import java.lang.foreign.SegmentAllocator
import kotlin.contracts.contract

public typealias NativeChar = NativeInt8
public typealias NativeSize = NativeInt64
public typealias NativeInt = NativeInt32

context(allocator: SegmentAllocator)
public fun String.c_str(): NativePointer<NativeChar> = NativeArray<NativeChar>(allocator.allocateFrom(this)).ptr()

context(allocator: SegmentAllocator)
public fun Collection<String>.c_strs(): NativePointer<NativePointer<NativeChar>> {
    val arr = NativePointer.mallocArr<NativeChar>(this.size)
    this.forEachIndexed { index, str ->
        arr[index] = str.c_str()
    }
    return arr.ptr()
}

public inline fun <R> MemoryStack(block: MemoryStack.Frame.() -> R): R {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return MemoryStack.stackPush().use {
        it.block()
    }
}

public inline fun <R> MemoryStack.Frame.MemoryStack(block: MemoryStack.Frame.() -> R): R {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return this.push().use {
        it.block()
    }
}