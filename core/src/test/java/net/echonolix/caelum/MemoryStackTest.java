package net.echonolix.caelum;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryStackTest {
    private static final byte SENTINEL = (byte) 0xA5;

    @Test
    void emptyNestedFrameLeavesUnalignedParentUntouched() {
        MemoryStack stack = new MemoryStack(64);
        stack.push();
        MemorySegment parent = stack.allocate(5, 1);
        parent.fill(SENTINEL);
        stack.push();
        stack.pop();
        assertBytes(parent, 0, parent.byteSize(), SENTINEL);
        stack.pop();
        assertBytes(parent, 0, parent.byteSize(), (byte) 0);
    }

    @Test
    void popZerosExactReleasedIntervalIncludingAlignmentPadding() {
        int[] sizes = {0, 1, 7, 8, 9, 15, 16, 17, 255, 256, 257};
        int[] alignments = {1, 8, 16, 32, 64};
        for (int alignment : alignments) {
            for (int size : sizes) {
                MemoryStack stack = new MemoryStack(512);
                MemorySegment memory = seed(stack, 512);
                stack.push();
                MemorySegment parent = stack.allocate(5, 1);
                stack.push();
                MemorySegment child = stack.allocate(size, alignment);
                assertEquals(0L, child.address() & (Math.max(alignment, 8) - 1));
                long end = child.address() - memory.address() + size;
                stack.pop();
                assertBytes(parent, 0, 5, SENTINEL);
                assertBytes(memory, 5, end, (byte) 0);
                assertBytes(memory, end, memory.byteSize(), SENTINEL);
                // Reallocation must reuse the same address and see the cleared bytes.
                stack.push();
                MemorySegment reused = stack.allocate(size, alignment);
                assertEquals(child.address(), reused.address());
                assertBytes(reused, 0, size, (byte) 0);
                stack.pop();
                stack.pop();
                assertBytes(memory, 0, end, (byte) 0);
            }
        }
    }

    @Test
    void nestedPopsPreserveBothParentFrames() {
        MemoryStack stack = new MemoryStack(512);
        MemorySegment memory = seed(stack, 512);
        stack.push();
        MemorySegment parent = stack.allocate(5, 1);
        stack.push();
        MemorySegment child = stack.allocate(13, 16);
        long childEnd = child.address() - memory.address() + child.byteSize();
        stack.push();
        MemorySegment grandchild = stack.allocate(257, 32);
        long grandchildEnd = grandchild.address() - memory.address() + grandchild.byteSize();
        stack.pop();
        assertBytes(memory, 0, childEnd, SENTINEL);
        assertBytes(memory, childEnd, grandchildEnd, (byte) 0);
        assertBytes(memory, grandchildEnd, memory.byteSize(), SENTINEL);
        stack.pop();
        assertBytes(parent, 0, 5, SENTINEL);
        assertBytes(memory, 5, grandchildEnd, (byte) 0);
        stack.pop();
        assertBytes(memory, 0, grandchildEnd, (byte) 0);
    }

    @Test
    void popAtStackBoundaryDoesNotRoundPastCapacity() {
        MemoryStack stack = new MemoryStack(33);
        MemorySegment memory = seed(stack, 33);
        stack.push();
        MemorySegment parent = stack.allocate(31, 1);
        stack.push();
        MemorySegment child = stack.allocate(1, 1);
        assertEquals(memory.address() + 32, child.address());
        stack.pop();
        assertBytes(parent, 0, 31, SENTINEL);
        assertBytes(memory, 31, 33, (byte) 0);
        stack.pop();
        assertBytes(memory, 0, 33, (byte) 0);
    }

    private static MemorySegment seed(MemoryStack stack, long capacity) {
        stack.push();
        MemorySegment memory = stack.allocate(capacity, 1);
        stack.pop();
        memory.fill(SENTINEL);
        return memory;
    }

    private static void assertBytes(MemorySegment memory, long start, long end, byte expected) {
        for (long i = start; i < end; i++) {
            assertEquals(expected, memory.get(ValueLayout.JAVA_BYTE, i), "byte=" + i);
        }
    }
}
