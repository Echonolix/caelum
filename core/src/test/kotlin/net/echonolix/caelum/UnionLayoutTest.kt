package net.echonolix.caelum

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class UnionLayoutTest {
    private object TailPadded : NUnion.Impl<TailPadded>(
        MemoryLayout.sequenceLayout(68, ValueLayout.JAVA_BYTE).withName("bytes"),
        ValueLayout.JAVA_LONG.withName("aligned")
    )

    private object AlreadyAligned : NUnion.Impl<AlreadyAligned>(
        ValueLayout.JAVA_LONG.withName("wide"),
        ValueLayout.JAVA_BYTE.withName("small")
    )

    private object NestedUnion : NUnion.Impl<NestedUnion>(
        TailPadded.layout.withName("inner"),
        MemoryLayout.sequenceLayout(77, ValueLayout.JAVA_BYTE).withName("bytes")
    )

    private object NestedStruct : NStruct.Impl<NestedStruct>(
        ValueLayout.JAVA_BYTE.withName("prefix"),
        NestedUnion.layout.withName("payload"),
        ValueLayout.JAVA_LONG.withName("suffix")
    )

    @Test
    fun `union rounds its largest member size up to maximum alignment`() {
        assertEquals(72L, TailPadded.layout.byteSize())
        assertEquals(8L, TailPadded.layout.byteAlignment())
        assertEquals(0L, TailPadded.layout.byteOffset(groupElement("bytes")))
        assertEquals(0L, TailPadded.layout.byteOffset(groupElement("aligned")))
        assertEquals(68L, TailPadded.layout.select(groupElement("bytes")).byteSize())
        // Array stride must include tail padding, including the final element.
        assertEquals(144L, MemoryLayout.sequenceLayout(2, TailPadded.layout).byteSize())
    }

    @Test
    fun `calloc reserves full padded union array storage with native alignment`() {
        Arena.ofConfined().use { arena ->
            val allocations = mutableListOf<MemorySegment>()
            val allocator = SegmentAllocator { size, alignment ->
                assertEquals(8L, alignment)
                arena.allocate(size, alignment).also(allocations::add)
            }.asAllocateScope()
            val values = TailPadded.calloc(allocator, 2)
            val allocation = allocations.single()
            assertEquals(144L, allocation.byteSize())
            assertEquals(0L, allocation.address() % 8)
            assertEquals(allocation.address(), values._address)
            assertEquals(2L, values.count)
            assertEquals(0.toByte(), allocation.get(ValueLayout.JAVA_BYTE, 143))
            allocation.set(ValueLayout.JAVA_BYTE, 143, 7.toByte())
            assertEquals(7.toByte(), allocation.get(ValueLayout.JAVA_BYTE, 143))
        }
    }

    @Test
    fun `aligned unions preserve their original member layouts`() {
        assertEquals(8L, AlreadyAligned.layout.byteSize())
        assertEquals(8L, AlreadyAligned.layout.byteAlignment())
        assertEquals(2, AlreadyAligned.layout.memberLayouts().size)
        assertEquals(ValueLayout.JAVA_LONG.withName("wide"), AlreadyAligned.layout.select(groupElement("wide")))
    }

    @Test
    fun `nested unions and structs include union padding in offsets and array stride`() {
        assertEquals(80L, NestedUnion.layout.byteSize())
        assertEquals(8L, NestedUnion.layout.byteAlignment())
        assertEquals(72L, NestedUnion.layout.select(groupElement("inner")).byteSize())
        assertEquals(96L, NestedStruct.layout.byteSize())
        assertEquals(8L, NestedStruct.layout.byteAlignment())
        assertEquals(8L, NestedStruct.layout.byteOffset(groupElement("payload")))
        assertEquals(88L, NestedStruct.layout.byteOffset(groupElement("suffix")))
        assertEquals(8L, NestedStruct.layout.byteOffset(groupElement("payload"), groupElement("inner")))
        assertEquals(192L, MemoryLayout.sequenceLayout(2, NestedStruct.layout).byteSize())
    }
}
