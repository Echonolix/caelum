package net.echonolix.caelum;

public interface AllocateScope {
    long _malloc(long byteSize, long byteAlignment);
    long _calloc(long byteSize, long byteAlignment);
}

