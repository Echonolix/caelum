package net.echonolix.caelum;

import java.lang.foreign.SegmentAllocator;

public abstract class WrappedAllocateScope implements AllocateScope {
    protected abstract SegmentAllocator getSegmentAllocator();

    @Override
    public long _malloc(long byteSize, long byteAlignment) {
        return getSegmentAllocator().allocate(byteSize, byteAlignment).address();
    }

    @Override
    public long _calloc(long byteSize, long byteAlignment) {
        return getSegmentAllocator().allocate(byteSize, byteAlignment).address();
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

