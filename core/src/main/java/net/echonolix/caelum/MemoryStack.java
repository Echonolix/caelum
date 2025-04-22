package net.echonolix.caelum;

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
        return getInstance().pushFrame();
    }

    public static void checkEmpty() {
        if (getInstance().frames.size() != 1) {
            throw new IllegalStateException("Frame leak");
        }
    }

    private static MemoryStack getInstance() {
        return INSTANCES.get();
    }

    @SuppressWarnings("resource")
    private Frame pushFrame() {
        Frame lastFrame = frames.peek();
        if (lastFrame == null) {
            throw new IllegalStateException("No frame");
        }
        return lastFrame.push();
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

        public long getAllocated() {
            return allocated;
        }

        public Frame push() {
            MemorySegment remaining = baseSegment.asSlice(allocated);
            if (remaining.byteSize() == 0) {
                throw new IllegalStateException("No space");
            }
            Frame newFrame = new Frame(remaining);
            frames.push(newFrame);
            return newFrame;
        }

        @SuppressWarnings("resource")
        public void pop() {
            if (frames.peek() != this) {
                throw new IllegalStateException("Frame mismatch");
            }
            frames.pop();
        }

        @Override
        public void close() {
            pop();
        }
    }
}