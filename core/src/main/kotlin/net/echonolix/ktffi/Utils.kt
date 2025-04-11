package net.echonolix.ktffi

import java.lang.foreign.SegmentAllocator

typealias char = int8_t
typealias size_t = int64_t

fun String.c_str(allocator: SegmentAllocator): NativeArray<char> = NativeArray(allocator.allocateFrom(this))

context(allocator: SegmentAllocator)
fun String.c_str(): NativeArray<char> = NativeArray(allocator.allocateFrom(this))