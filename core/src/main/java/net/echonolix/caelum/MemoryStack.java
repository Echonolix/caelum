package net.echonolix.caelum;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.util.Arrays;

public final class MemoryStack extends WrappedAllocateScope implements SegmentAllocator, AutoCloseable {
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
        this.baseSegment = arena.allocate(size, 4096);
        CLEANER.register(this, arena::close);
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
        long baseAddress = baseSegment.address();
        long sliceOffset = ((baseAddress + offset + byteAlignment - 1) & -byteAlignment) - baseAddress;
        offset = sliceOffset + byteSize;
        return sliceOffset;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return baseSegment.asSlice(allocateOffset(byteSize, byteAlignment), byteSize, byteAlignment);
    }

    private void rangeFillZeros(long offset0, long size0) {
        if (size0 <= 0) {
            return;
        }
        long offset = offset0;
        long remaining = size0;
        if ((offset & 1) != 0) {
            NInt8.INSTANCE.getArrayVarHandle().set(baseSegment, offset, 0L, (byte) 0);
            offset += Byte.BYTES;
            remaining -= Byte.BYTES;
        }
        if ((offset & 3) != 0 && remaining >= 2) {
            NInt16.INSTANCE.getArrayVarHandle().set(baseSegment, offset, 0L, (short) 0);
            offset += Short.BYTES;
            remaining -= Short.BYTES;
        }
        if ((offset & 7) != 0 && remaining >= 4) {
            NInt32.INSTANCE.getArrayVarHandle().set(baseSegment, offset, 0L, 0);
            offset += Integer.BYTES;
            remaining -= Integer.BYTES;
        }
        long loopCount = remaining / Long.BYTES;
        for (long i = 0; i < loopCount; i++) {
            NInt64.INSTANCE.getArrayVarHandle().set(baseSegment, offset, i, 0L);
        }
        offset += loopCount * Long.BYTES;
        remaining -= loopCount * Long.BYTES;
        if (remaining >= Integer.BYTES) {
            NInt32.INSTANCE.getArrayVarHandle().set(baseSegment, offset, 0L, 0);
            offset += Integer.BYTES;
            remaining -= Integer.BYTES;
        }
        if (remaining >= Short.BYTES) {
            NInt16.INSTANCE.getArrayVarHandle().set(baseSegment, offset, 0L, (short) 0);
            offset += Short.BYTES;
            remaining -= Short.BYTES;
        }
        if (remaining >= Byte.BYTES) {
            NInt8.INSTANCE.getArrayVarHandle().set(baseSegment, offset, 0L, (byte) 0);
            offset += Byte.BYTES;
            remaining -= Byte.BYTES;
        }
        assert remaining == 0 : "Remaining bytes after filling with zeros: " + remaining;
        assert offset == offset0 + size0 : "Offset after filling with zeros: " + offset + ", expected: " + (offset0 + size0);
    }

    @Override
    public long _malloc(@NotNull MemoryLayout layout) {
        return baseSegment.address() + allocateOffset(layout.byteSize(), layout.byteAlignment());
    }

    @Override
    public long _calloc(@NotNull MemoryLayout layout) {
        long offset = allocateOffset(layout.byteSize(), layout.byteAlignment());
        rangeFillZeros(offset, layout.byteSize());
        return baseSegment.address() + offset;
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
        if (count <= 0) {
            return 0L;
        }
        long offset = allocateOffset(layout.byteSize() * count, layout.byteAlignment());
        rangeFillZeros(offset, layout.byteSize() * count);
        return baseSegment.address() + offset;
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