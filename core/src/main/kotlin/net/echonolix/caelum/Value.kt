@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import java.lang.foreign.MemorySegment

@JvmInline
public value class NValue<T : NType>(
    public val segment: MemorySegment,
) {
    public inline fun ptr(): NPointer<T> = NPointer(segment.address())
}