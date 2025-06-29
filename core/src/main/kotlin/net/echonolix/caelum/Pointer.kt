@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import java.lang.invoke.VarHandle

@JvmInline
public value class NPointer<T : NType>(
    public val _address: Long,
) : NType {
    override val typeDescriptor: NType.Descriptor<NPointer<*>>
        get() = Companion

    @Suppress("UNCHECKED_CAST")
    public companion object : NType.Descriptor.Impl<NPointer<*>>(APIHelper.pointerLayout) {
        @JvmField
        public val arrayVarHandle: VarHandle = layout.arrayElementVarHandle()

        @JvmStatic
        public inline fun <T : NType> fromNativeData(value: Long): NPointer<T> = NPointer(value)

        @JvmStatic
        public inline fun <T : NType> toNativeData(value: NPointer<T>?): Long = value?._address ?: 0L


        @JvmName("malloc114")
        public fun <T : NType> malloc(allocator: AllocateScope): NValue<NPointer<T>> =
            allocator.malloc(this) as NValue<NPointer<T>>

        @JvmName("malloc514")
        context(allocator: AllocateScope)
        public fun <T : NType> malloc(): NValue<NPointer<T>> =
            allocator.malloc(this) as NValue<NPointer<T>>

        @JvmName("malloc1919")
        public fun <T : NType> malloc(allocator: AllocateScope, count: Long): NArray<NPointer<T>> =
            allocator.malloc(this, count) as NArray<NPointer<T>>

        @JvmName("malloc810")
        context(allocator: AllocateScope)
        public fun <T : NType> malloc(count: Long): NArray<NPointer<T>> =
            allocator.malloc(this, count) as NArray<NPointer<T>>

        @JvmName("calloc114")
        public fun <T : NType> calloc(allocator: AllocateScope): NValue<NPointer<T>> =
            allocator.calloc(this) as NValue<NPointer<T>>

        @JvmName("calloc514")
        context(allocator: AllocateScope)
        public fun <T : NType> calloc(): NValue<NPointer<T>> =
            allocator.calloc(this) as NValue<NPointer<T>>

        @JvmName("calloc1919")
        public fun <T : NType> calloc(allocator: AllocateScope, count: Long): NArray<NPointer<T>> =
            allocator.calloc(this, count) as NArray<NPointer<T>>

        @JvmName("calloc810")
        context(allocator: AllocateScope)
        public fun <T : NType> calloc(count: Long): NArray<NPointer<T>> =
            allocator.calloc(this, count) as NArray<NPointer<T>>
    }
}

public fun <T : NType> nullptr(): NPointer<T> = NPointer(0L)

public operator fun <E : NType, T : NPointer<E>> NPointer<T>.get(index: Long): T {
    @Suppress("UNCHECKED_CAST")
    return NPointer<E>(
        NPointer.arrayVarHandle.get(
            APIHelper.`_$OMNI_SEGMENT$_`,
            _address,
            index
        ) as Long
    ) as T
}

public operator fun <E : NType, T : NPointer<E>> NPointer<T>.set(index: Long, value: T) {
    NPointer.arrayVarHandle.set(APIHelper.`_$OMNI_SEGMENT$_`, _address, index, value._address)
}

public operator fun <E : NType, T : NPointer<E>> NPointer<T>.get(index: Int): T {
    @Suppress("UNCHECKED_CAST")
    return NPointer<E>(
        NPointer.arrayVarHandle.get(
            APIHelper.`_$OMNI_SEGMENT$_`,
            _address,
            index.toLong()
        ) as Long
    ) as T
}

public operator fun <E : NType, T : NPointer<E>> NPointer<T>.set(index: Int, value: T) {
    NPointer.arrayVarHandle.set(APIHelper.`_$OMNI_SEGMENT$_`, _address, index.toLong(), value._address)
}