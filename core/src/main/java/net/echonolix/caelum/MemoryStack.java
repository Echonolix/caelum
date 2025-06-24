package net.echonolix.caelum;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.util.Arrays;

public final class MemoryStack implements SegmentAllocator, AutoCloseable {
    private static final long DEFAULT_SIZE = 4 * 1024 * 1024; // 4 MiB
    private static final long MAX_SIZE = 16 * 1024 * 1024; // 16 MiB

    private static final ThreadLocal<MemoryStack> INSTANCES = ThreadLocal.withInitial(() -> new MemoryStack(DEFAULT_SIZE));
    private static final Cleaner CLEANER = Cleaner.create();

    private final MemorySegment baseSegment;
    private long offset = 0L;
    private long[] frame = new long[16];
    private int frameTop = -1;

    MemoryStack(long size) {
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Memory stack size must not exceed 16 MiB");
        }
        @SuppressWarnings("resource")
        Arena arena = Arena.ofShared();
        this.baseSegment = arena.allocate(size);
        CLEANER.register(this, arena::close);
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

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        long baseAddress = baseSegment.address();
        long sliceOffset = ((baseAddress + offset + byteAlignment - 1) & -byteAlignment) - baseAddress;
        MemorySegment slice = baseSegment.asSlice(sliceOffset, byteSize, byteAlignment);
        offset = (sliceOffset) + byteSize;
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
    private static MemoryStack getInstance() {
        return INSTANCES.get();
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

    @Override
    public void close() throws Exception {
        pop();
    }
}