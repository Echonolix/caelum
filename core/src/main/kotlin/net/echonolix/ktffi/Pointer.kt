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

        @JvmName("malloc-114")
        public fun <T : NativeType> malloc(allocator: SegmentAllocator): NativeValue<NativePointer<T>> =
            NativeValue(allocator.allocate(layout))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-514")
        public fun <T : NativeType> malloc(): NativeValue<NativePointer<T>> =
            NativeValue(allocator.allocate(layout))

        @JvmName("malloc-191")
        public fun <T : NativeType> malloc(allocator: SegmentAllocator, count: ULong): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))

        @JvmName("malloc-981")
        public fun <T : NativeType> malloc(allocator: SegmentAllocator, count: UInt): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))

        @JvmName("malloc-069")
        public fun <T : NativeType> malloc(allocator: SegmentAllocator, count: Long): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count))

        @JvmName("malloc-420")
        public fun <T : NativeType> malloc(allocator: SegmentAllocator, count: Int): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-911")
        public fun <T : NativeType> malloc(count: ULong): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-666")
        public fun <T : NativeType> malloc(count: UInt): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-888")
        public fun <T : NativeType> malloc(count: Long): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-233")
        public fun <T : NativeType> malloc(count: Int): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()))


        @JvmName("calloc-114")
        public fun <T : NativeType> calloc(allocator: SegmentAllocator, count: ULong): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        @JvmName("calloc-514")
        public fun <T : NativeType> calloc(allocator: SegmentAllocator, count: UInt): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        @JvmName("calloc-191")
        public fun <T : NativeType> calloc(allocator: SegmentAllocator, count: Long): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count).apply { fill(0) })

        @JvmName("calloc-981")
        public fun <T : NativeType> calloc(allocator: SegmentAllocator, count: Int): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-069")
        public fun <T : NativeType> calloc(count: ULong): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-420")
        public fun <T : NativeType> calloc(count: UInt): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-911")
        public fun <T : NativeType> calloc(count: Long): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-666")
        public fun <T : NativeType> calloc(count: Int): NativeArray<NativePointer<T>> =
            NativeArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        @JvmName("calloc-888")
        public fun <T : NativeType> calloc(allocator: SegmentAllocator): NativeValue<NativePointer<T>> =
            NativeValue(allocator.allocate(layout).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-233")
        public fun <T : NativeType> calloc(): NativeValue<NativePointer<T>> =
            NativeValue(allocator.allocate(layout).apply { fill(0) })
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