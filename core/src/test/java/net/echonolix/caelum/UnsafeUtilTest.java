package net.echonolix.caelum;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsafeUtilTest {
    private static final byte SENTINEL = (byte) 0xA5;

    @Test
    void assertionsAreEnabled() {
        assertTrue(UnsafeUtil.class.desiredAssertionStatus(), "UnsafeUtil assertions must be enabled");
        assertTrue(MemoryStack.class.desiredAssertionStatus(), "MemoryStack assertions must be enabled");
    }

    @Test
    void zerosOnlyRequestedIntervalForEveryAlignmentAndShortLength() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment memory = arena.allocate(352, 8);
            for (int alignmentOffset = 0; alignmentOffset < 8; alignmentOffset++) {
                // Includes both sides of the 256-byte short-write threshold.
                for (int size = 0; size <= 320; size++) {
                    memory.fill(SENTINEL);
                    long start = 8 + alignmentOffset;
                    UnsafeUtil.fillZeros(memory.address() + start, size);
                    for (long i = 0; i < memory.byteSize(); i++) {
                        byte expected = i >= start && i < start + size ? 0 : SENTINEL;
                        assertEquals(expected, memory.get(ValueLayout.JAVA_BYTE, i),
                            "offset=" + alignmentOffset + ", size=" + size + ", byte=" + i);
                    }
                }
            }
        }
    }

    @Test
    void zerosLargeUnalignedIntervalWithoutTouchingGuards() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment memory = arena.allocate(4112, 8);
            memory.fill(SENTINEL);
            UnsafeUtil.fillZeros(memory.address() + 3, 4097);
            for (long i = 0; i < memory.byteSize(); i++) {
                assertEquals(i >= 3 && i < 4100 ? (byte) 0 : SENTINEL,
                    memory.get(ValueLayout.JAVA_BYTE, i), "byte=" + i);
            }
        }
    }
}
