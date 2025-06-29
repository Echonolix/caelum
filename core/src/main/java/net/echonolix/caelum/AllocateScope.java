package net.echonolix.caelum;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

public interface AllocateScope {
    long _malloc(MemoryLayout layout);
    long _calloc(MemoryLayout layout);
    long _malloc(MemoryLayout layout, long count);
    long _calloc(MemoryLayout layout, long count);
    MemorySegment _allocateCString(String value);
}

