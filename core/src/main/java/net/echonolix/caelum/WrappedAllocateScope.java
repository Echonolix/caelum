package net.echonolix.caelum;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

public abstract class WrappedAllocateScope implements AllocateScope {
    protected abstract SegmentAllocator getSegmentAllocator();

    @Override
    public long _malloc(MemoryLayout layout) {
        return getSegmentAllocator().allocate(layout).address();
    }

    @Override
    public long _calloc(MemoryLayout layout) {
        return getSegmentAllocator().allocate(layout).fill((byte)0).address();
    }

    @Override
    public long _malloc(MemoryLayout layout, long count) {
        return getSegmentAllocator().allocate(layout, count).address();
    }

    @Override
    public long _calloc(MemoryLayout layout, long count) {
        return getSegmentAllocator().allocate(layout, count).fill((byte)0).address();
    }

    @Override
    public MemorySegment _allocateCString(String value) {
        return getSegmentAllocator().allocateFrom(value);
    }

    public static class Impl extends WrappedAllocateScope {
        private final SegmentAllocator segmentAllocator;

        public Impl(SegmentAllocator segmentAllocator) {
            this.segmentAllocator = segmentAllocator;
        }

        @Override
        protected SegmentAllocator getSegmentAllocator() {
            return segmentAllocator;
        }
    }
}

