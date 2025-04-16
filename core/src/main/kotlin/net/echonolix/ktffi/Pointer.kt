@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.ktffi

import java.lang.foreign.SegmentAllocator
import java.lang.invoke.VarHandle

@JvmInline
public value class NativePointer<T : NativeType>(
    public val _address: Long,
) : NativeType {
    override val typeDescriptor: TypeDescriptor<NativePointer<*>>
        get() = Companion

    public companion object : TypeDescriptor.Impl<NativePointer<*>>(APIHelper.pointerLayout) {
        @JvmField
        public val arrayVarHandle: VarHandle = layout.arrayElementVarHandle()

        @JvmStatic
        public inline fun <T : NativeType> fromNativeData(value: Long): NativePointer<T> = NativePointer(value)

        @JvmStatic
        public inline fun <T : NativeType> toNativeData(value: NativePointer<T>?): Long = value?._address ?: 0L

        context(allocator: SegmentAllocator)
        public inline fun <T : NativeType> malloc(): NativeValue<NativePointer<T>> =
            NativeValue(allocator.allocate(layout))

        context(allocator: SegmentAllocator)
        public inline fun <T : NativeType> calloc(): NativeValue<NativePointer<T>> =
            NativeValue(allocator.allocate(layout).apply { fill(0) })

        context(allocator: SegmentAllocator)
        public inline fun <T : NativeType> mallocArr(count: Long): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count))

        context(allocator: SegmentAllocator)
        public inline fun <T : NativeType> mallocArr(count: Int): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        public inline fun <T : NativeType> callocArr(count: Long): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count).apply { fill(0) })

        context(allocator: SegmentAllocator)
        public inline fun <T : NativeType> callocArr(count: Int): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })
    }
}

public inline fun <T : NativeType> nullptr(): NativePointer<T> = NativePointer(0L)

public inline operator fun <E : NativeType, T : NativePointer<E>> NativePointer<T>.get(index: Long): T {
    @Suppress("UNCHECKED_CAST")
    return NativePointer<E>(
        NativePointer.arrayVarHandle.get(
            APIHelper.`_$OMNI_SEGMENT$_`,
            _address,
            index
        ) as Long
    ) as T
}

public inline operator fun <E : NativeType, T : NativePointer<E>> NativePointer<T>.set(index: Long, value: T) {
    NativePointer.arrayVarHandle.set(APIHelper.`_$OMNI_SEGMENT$_`, _address, index, value._address)
}

public inline operator fun <E : NativeType, T : NativePointer<E>> NativePointer<T>.get(index: Int): T {
    @Suppress("UNCHECKED_CAST")
    return NativePointer<E>(
        NativePointer.arrayVarHandle.get(
            APIHelper.`_$OMNI_SEGMENT$_`,
            _address,
            index.toLong()
        ) as Long
    ) as T
}

public inline operator fun <E : NativeType, T : NativePointer<E>> NativePointer<T>.set(index: Int, value: T) {
    NativePointer.arrayVarHandle.set(APIHelper.`_$OMNI_SEGMENT$_`, _address, index.toLong(), value._address)
}