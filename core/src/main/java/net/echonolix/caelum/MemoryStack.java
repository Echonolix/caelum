package net.echonolix.caelum;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.util.Arrays;

public final class MemoryStack extends WrappedAllocateScope implements SegmentAllocator, AutoCloseable {
    private static final long DEFAULT_SIZE = 4 * 1024 * 1024; // 4 MiB
    private static final long MAX_SIZE = 16 * 1024 * 1024; // 16 MiB
    private static final VarHandle LONG_VAR_HANDLE = ValueLayout.JAVA_LONG.varHandle();

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
        this.baseSegment = arena.allocate(size, 4096);
        CLEANER.register(this, arena::close);
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
    @NotNull
    public SegmentAllocator getSegmentAllocator() {
        return this;
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

    private long allocateOffset(long byteSize, long byteAlignment) {
        byteAlignment = Math.max(byteAlignment, 8);
        long baseAddress = baseSegment.address();
        long sliceOffset = ((baseAddress + offset + byteAlignment - 1) & -byteAlignment) - baseAddress;
        offset = sliceOffset + byteSize;
        return sliceOffset;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return baseSegment.asSlice(allocateOffset(byteSize, byteAlignment), byteSize, byteAlignment);
    }

    @Override
    public long _malloc(@NotNull MemoryLayout layout) {
        return baseSegment.address() + allocateOffset(layout.byteSize(), layout.byteAlignment());
    }

    @Override
    public long _calloc(@NotNull MemoryLayout layout) {
        return _malloc(layout);
    }

    @Override
    public long _malloc(@NotNull MemoryLayout layout, long count) {
        if (count <= 0) {
            return 0L;
        }
        return baseSegment.address() + allocateOffset(layout.byteSize() * count, layout.byteAlignment());
    }

    @Override
    public long _calloc(@NotNull MemoryLayout layout, long count) {
        return _malloc(layout, count);
    }

    @NotNull
    public MemoryStack push() {
        pushFrame(offset);
        return this;
    }

    public void pop() {
        long prevOffset = offset;
        offset = popFrame();
        baseSegment.asSlice(offset, prevOffset - offset).fill((byte) 0);
    }

    @Override
    public void close() throws Exception {
        pop();
    }
}