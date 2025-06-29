/*
 * Adopted from LWJGL 3
 *
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package net.echonolix.caelum;

import java.nio.BufferOverflowException;

import static java.lang.Character.isHighSurrogate;
import static java.lang.Character.toCodePoint;

class LWJGLMemoryUtil {
    static int memLengthUTF8(CharSequence value) {
        int len   = value.length();
        int bytes = len + 1; // start with 1:1

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if (c < 0x80) {
                // 1 input char -> 1 output byte
            } else {
                if (c < 0x800) {
                    // c <= 127: 0 (1 input char -> 1 output byte)
                    // c >= 128: 1 (1 input char -> 2 output bytes)
                    bytes += (0x7F - c) >>> 31;
                } else {
                    // non-high-surrogate: 1 input char  -> 3 output bytes
                    //     surrogate-pair: 2 input chars -> 4 output bytes
                    bytes += 2;
                    if (isHighSurrogate(c)) {
                        i++;
                    }
                }
                if (bytes < 0) {
                    throw new BufferOverflowException();
                }
            }
        }

        if (bytes < 0) {
            throw new BufferOverflowException();
        }

        return bytes;
    }

    static int encodeUTF8Unsafe(CharSequence text, long target) throws Throwable {
        int p = 0, i = 0, len = text.length();

        while (i < len) {
            char c = text.charAt(i++);
            if (c < 0x80) {
                p = write8(target, p, c);
            } else {
                int cp = c;
                if (c < 0x800) {
                    p = write8(target, p, 0xC0 | cp >> 6);
                } else {
                    if (!isHighSurrogate(c)) {
                        p = write8(target, p, 0xE0 | cp >> 12);
                    } else {
                        cp = toCodePoint(c, text.charAt(i++));

                        p = write8(target, p, 0xF0 | cp >> 18);
                        p = write8(target, p, 0x80 | cp >> 12 & 0x3F);
                    }
                    p = write8(target, p, 0x80 | cp >> 6 & 0x3F);
                }
                p = write8(target, p, 0x80 | cp & 0x3F);
            }
        }

        p = write8(target, p, 0);

        return p;
    }

    private static int write8(long target, int offset, int value) throws Throwable {
        UnsafeUtil.UNSAFE_PUT_BYTE_NATIVE.invokeExact(target + Integer.toUnsignedLong(offset), (byte) value);
        return offset + 1;
    }
}
