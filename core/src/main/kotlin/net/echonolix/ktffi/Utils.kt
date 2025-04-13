package net.echonolix.ktffi

import java.lang.foreign.SegmentAllocator

typealias NativeChar = NativeInt8
typealias NativeSize = NativeInt64
typealias NativeInt = NativeInt32

fun String.c_str(allocator: SegmentAllocator): NativeArray<NativeChar> = NativeArray(allocator.allocateFrom(this))

context(allocator: SegmentAllocator)
fun String.c_str(): NativeArray<NativeChar> = NativeArray(allocator.allocateFrom(this))