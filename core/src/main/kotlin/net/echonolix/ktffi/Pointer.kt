@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.ktffi

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.jvm.javaMethod

@JvmInline
public value class NativePointer<T : NativeType>(
    public val _address: Long,
) : NativeType {
    override val typeDescriptor: TypeDescriptor<NativePointer<*>>
        get() = Companion

    public companion object : TypeDescriptor.Impl<NativePointer<*>>(APIHelper.pointerLayout) {
        override val fromNativeDataMH: MethodHandle =
            MethodHandles.lookup().unreflect(::fromNativeData0.javaMethod)

        override val toNativeDataMH: MethodHandle =
            MethodHandles.lookup().unreflect(::toNativeData0.javaMethod)

        @JvmStatic
        public fun fromNativeData0(value: Long): NativePointer<*> = fromNativeData<NativeChar>(value)

        @JvmStatic
        public fun toNativeData0(value: NativePointer<*>): Long = toNativeData(value)

        @JvmStatic
        public inline fun <T : NativeType> fromNativeData(value: Long): NativePointer<T> = NativePointer(value)

        @JvmStatic
        public inline fun <T : NativeType> toNativeData(value: NativePointer<T>): Long = value._address
    }
}

public val nullptr: NativePointer<*> = NativePointer<NativeChar>(0L)