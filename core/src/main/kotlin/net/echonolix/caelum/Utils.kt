@file:Suppress("FunctionName")

package net.echonolix.caelum

import java.lang.foreign.SegmentAllocator
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public typealias NSize = NInt64
public typealias NInt = NInt32

public fun String.c_str(allocator: SegmentAllocator): NPointer<NChar> =
    NArray<NChar>(allocator.allocateFrom(this)).ptr()

context(allocator: MemoryStack)
public fun String.c_str(): NPointer<NChar> = c_str(allocator)

public fun Collection<String>.c_strs(allocator: SegmentAllocator): NArray<NPointer<NChar>> {
    val arr = NPointer.malloc<NChar>(allocator, this.size)
    this.forEachIndexed { index, str ->
        arr[index] = str.c_str(allocator)
    }
    return arr
}

context(allocator: MemoryStack)
public fun Collection<String>.c_strs(): NArray<NPointer<NChar>> = c_strs(allocator)

public var NArray<NChar>.string: String
    get() = segment.getString(0L)
    set(value) {
        segment.setString(0L, value)
    }

public var NPointer<NChar>.string: String
    get() = APIHelper.`_$OMNI_SEGMENT$_`.getString(_address)
    set(value) {
        APIHelper.`_$OMNI_SEGMENT$_`.setString(_address, value)
    }

/**
 * Creates a new [MemoryStack] and pushes it onto the stack, executing the
 * given block of code within the [MemoryStack] context.
 */
public inline fun <R> MemoryStack(block: MemoryStack.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return MemoryStack.stackPush().use {
        it.block()
    }
}

/**
 * Creates a new [MemoryStack] and pushes it onto the stack, executing
 * the given block of code within the [MemoryStack] context.
 */
public inline fun <R> MemoryStack.MemoryStack(block: (MemoryStack).() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return this.push().use {
        it.block()
    }
}

@Suppress("UNCHECKED_CAST")
@UnsafeAPI
public fun <T : NType> reinterpret_cast(pointer: NPointer<*>): NPointer<T> =
    pointer as NPointer<T>

@Suppress("UNCHECKED_CAST")
@UnsafeAPI
public fun <T : NType> reinterpret_cast(array: NArray<*>): NArray<T> =
    array as NArray<T>

@Suppress("UNCHECKED_CAST")
@UnsafeAPI
public fun <T : NType> reinterpret_cast(value: NValue<*>): NValue<T> =
    value as NValue<T>

@Suppress("UNCHECKED_CAST")
public fun <N : Any, K : Any, A : NPrimitive<N, K>, B : NPrimitive<N, K>> static_cast(value: NPointer<A>): NPointer<B> =
    value as NPointer<B>

@Suppress("UNCHECKED_CAST")
public fun <N : Any, K : Any, A : NPrimitive<N, K>, B : NPrimitive<N, K>> static_cast(value: NArray<A>): NArray<B> =
    value as NArray<B>

@Suppress("UNCHECKED_CAST")
public fun <N : Any, K : Any, A : NPrimitive<N, K>, B : NPrimitive<N, K>> static_cast(value: NValue<A>): NValue<B> =
    value as NValue<B>