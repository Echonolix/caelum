package net.echonolix.ktffi

import java.lang.foreign.SegmentAllocator

public typealias NativeChar = NativeInt8
public typealias NativeSize = NativeInt64
public typealias NativeInt = NativeInt32

public fun String.c_str(allocator: SegmentAllocator): NativeArray<NativeChar> =
    NativeArray(allocator.allocateFrom(this))

context(allocator: SegmentAllocator)
public fun String.c_str(): NativeArray<NativeChar> = NativeArray(allocator.allocateFrom(this))