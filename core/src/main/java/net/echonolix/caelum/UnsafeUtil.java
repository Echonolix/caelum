package net.echonolix.caelum;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class UnsafeUtil {
    public static final MethodHandles.Lookup LOOKUP;
    public static final Class<?> UNSAFE_CLASS;
    public static final Class<?> STRING_SUPPORT_CLASS;

    public static final Object UNSAFE_OBJECT;
    public static final MethodHandle UNSAFE_PUT_BYTE;
    public static final MethodHandle UNSAFE_PUT_INT;
    public static final MethodHandle UNSAFE_PUT_LONG;
    public static final MethodHandle UNSAFE_SET_MEMORY0;

    public static final MethodHandle UNSAFE_PUT_BYTE_NATIVE;
    public static final MethodHandle UNSAFE_PUT_INT_NATIVE;
    public static final MethodHandle UNSAFE_PUT_LONG_NATIVE;
    public static final MethodHandle UNSAFE_SET_MEMORY0_NATIVE;

    static {
        try {
            LOOKUP = ImplLookupGetter.getLookup();
            UNSAFE_CLASS = LOOKUP.findClass("jdk.internal.misc.Unsafe");
            STRING_SUPPORT_CLASS = LOOKUP.findClass("jdk.internal.foreign.StringSupport");

            MethodHandle unsafeFieldGetter = LOOKUP.findStaticGetter(
                UNSAFE_CLASS,
                "theUnsafe",
                UNSAFE_CLASS
            );

            UNSAFE_OBJECT = unsafeFieldGetter.invoke();

            UNSAFE_PUT_BYTE = LOOKUP.findVirtual(
                UnsafeUtil.UNSAFE_CLASS,
                "putByte",
                MethodType.methodType(void.class, Object.class, long.class, byte.class)
            ).bindTo(UNSAFE_OBJECT);
            UNSAFE_PUT_BYTE_NATIVE = UNSAFE_PUT_BYTE.bindTo(null);
            UNSAFE_PUT_INT = LOOKUP.findVirtual(
                UnsafeUtil.UNSAFE_CLASS,
                "putInt",
                MethodType.methodType(void.class, Object.class, long.class, int.class)
            ).bindTo(UNSAFE_OBJECT);
            UNSAFE_PUT_INT_NATIVE = UNSAFE_PUT_INT.bindTo(null);
            UNSAFE_PUT_LONG = LOOKUP.findVirtual(
                UnsafeUtil.UNSAFE_CLASS,
                "putLong",
                MethodType.methodType(void.class, Object.class, long.class, long.class)
            ).bindTo(UNSAFE_OBJECT);
            UNSAFE_PUT_LONG_NATIVE = UNSAFE_PUT_LONG.bindTo(null);
            UNSAFE_SET_MEMORY0 = LOOKUP.findVirtual(
                UnsafeUtil.UNSAFE_CLASS,
                "setMemory0",
                MethodType.methodType(void.class, Object.class, long.class, long.class, byte.class)
            ).bindTo(UNSAFE_OBJECT);
            UNSAFE_SET_MEMORY0_NATIVE = UNSAFE_SET_MEMORY0.bindTo(null);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void fillZeros(long address, long size) {
        assert (address & 7) == 0 : "Address must be aligned to 8 bytes";
        assert (size & 7) == 0 : "Size must be a multiple of 8 bytes";

        try {
            if (size <= 256) {
                long loopCount = (size + Long.BYTES - 1) / 8;
                for (long i = 0; i < loopCount; i++) {
                    UnsafeUtil.UNSAFE_PUT_LONG_NATIVE.invokeExact(  address + i * Long.BYTES, 0L);
                }
            } else {
                UnsafeUtil.UNSAFE_SET_MEMORY0_NATIVE.invokeExact(  address, size, (byte) 0);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
