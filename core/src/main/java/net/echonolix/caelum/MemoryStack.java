package net.echonolix.caelum;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.Arrays;

public final class MemoryStack implements SegmentAllocator, AutoCloseable {
    private static final ThreadLocal<MemoryStack> INSTANCES = ThreadLocal.withInitial(() -> new MemoryStack(8 * 1024 * 1024));

    private final MemorySegment baseSegment;
    private long offset = 0L;
    private long[] frame = new long[16];
    private int frameTop = -1;

    MemoryStack(long size) {
        this.baseSegment = Arena.ofAuto().allocate(size);
    }

    private void pushFrame(long value) {
        if (frameTop == frame.length) {
            frame = Arrays.copyOf(frame, frame.length * 2);
        }
        frame[++frameTop] = value;
    }

    private long popFrame() {
        if (frameTop == frame.length - 1) {
            throw new IllegalStateException("No frame");
        }
        return frame[frameTop--];
    }

    private static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        long min = baseSegment.address();
        long start = alignUp(min + offset, byteAlignment) - min;
        MemorySegment slice = baseSegment.asSlice(start, byteSize, byteAlignment);
        offset = start + byteSize;
        return slice;
    }

    @NotNull
    public MemoryStack push() {
        pushFrame(offset);
        return this;
    }

    public void pop() {
        offset = popFrame();
    }

    @NotNull
    public static MemoryStack stackPush() {
        return getInstance().push();
    }

    public static void checkEmpty() {
        if (getInstance().frameTop != -1) {
            throw new IllegalStateException("Frame leak");
        }
    }

    @NotNull
    private static MemoryStack getInstance() {
        return INSTANCES.get();
    }

    @Override
    public void close() throws Exception {
        pop();
    }
}