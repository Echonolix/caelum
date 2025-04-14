package net.echonolix.ktffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayDeque;

public final class MemoryStack {
    private static final ThreadLocal<MemoryStack> INSTANCES = ThreadLocal.withInitial(() -> new MemoryStack(8 * 1024 * 1024));
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();

    MemoryStack(long size) {
        frames.push(new Frame(Arena.ofAuto().allocate(size)));
    }

    public static Frame stackPush() {
        return INSTANCES.get().push();
    }

    public void checkEmpty() {
        if (frames.size() != 1) {
            throw new IllegalStateException("Frame leak");
        }
    }

    @SuppressWarnings("resource")
    public Frame push() {
        Frame lastFrame = frames.peek();
        if (lastFrame == null) {
            throw new IllegalStateException("No frame");
        }
        Frame newFrame = lastFrame.push();
        frames.push(newFrame);
        return newFrame;
    }

    @SuppressWarnings("resource")
    private void pop(Frame frame) {
        if (frames.peek() != frame) {
            throw new IllegalStateException("Frame mismatch");
        }
        frames.pop();
    }

    public final class Frame implements SegmentAllocator, AutoCloseable {
        private final MemorySegment baseSegment;
        private final SegmentAllocator delegated;
        private long allocated = 0L;

        Frame(MemorySegment segment) {
            this.baseSegment = segment;
            this.delegated = SegmentAllocator.slicingAllocator(segment);
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            MemorySegment result = delegated.allocate(byteSize, byteAlignment);
            allocated = (result.address() + result.byteSize()) - baseSegment.address();
            return result;
        }

        public Frame push() {
            MemorySegment remaining = baseSegment.asSlice(allocated);
            if (remaining.byteSize() == 0) {
                throw new IllegalStateException("No space");
            }
            return new Frame(remaining);
        }

        @Override
        public void close() {
            pop(this);
        }
    }
}