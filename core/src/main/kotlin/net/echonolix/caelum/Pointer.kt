@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import java.lang.foreign.SegmentAllocator
import java.lang.invoke.VarHandle

@JvmInline
public value class NPointer<T : NType>(
    public val _address: Long,
) : NType {
    override val typeDescriptor: NType.Descriptor<NPointer<*>>
        get() = Companion

    public companion object : NType.Descriptor.Impl<NPointer<*>>(APIHelper.pointerLayout) {
        @JvmField
        public val arrayVarHandle: VarHandle = layout.arrayElementVarHandle()

        @JvmStatic
        public inline fun <T : NType> fromNativeData(value: Long): NPointer<T> = NPointer(value)

        @JvmStatic
        public inline fun <T : NType> toNativeData(value: NPointer<T>?): Long = value?._address ?: 0L

        @JvmName("malloc-114")
        public fun <T : NType> malloc(allocator: SegmentAllocator): NValue<NPointer<T>> =
            NValue(allocator.allocate(layout))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-514")
        public fun <T : NType> malloc(): NValue<NPointer<T>> =
            NValue(allocator.allocate(layout))

        @JvmName("malloc-191")
        public fun <T : NType> malloc(allocator: SegmentAllocator, count: ULong): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()))

        @JvmName("malloc-981")
        public fun <T : NType> malloc(allocator: SegmentAllocator, count: UInt): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()))

        @JvmName("malloc-069")
        public fun <T : NType> malloc(allocator: SegmentAllocator, count: Long): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count))

        @JvmName("malloc-420")
        public fun <T : NType> malloc(allocator: SegmentAllocator, count: Int): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-911")
        public fun <T : NType> malloc(count: ULong): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-666")
        public fun <T : NType> malloc(count: UInt): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-888")
        public fun <T : NType> malloc(count: Long): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count))

        context(allocator: SegmentAllocator)
        @JvmName("malloc-233")
        public fun <T : NType> malloc(count: Int): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()))


        @JvmName("calloc-114")
        public fun <T : NType> calloc(allocator: SegmentAllocator, count: ULong): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        @JvmName("calloc-514")
        public fun <T : NType> calloc(allocator: SegmentAllocator, count: UInt): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        @JvmName("calloc-191")
        public fun <T : NType> calloc(allocator: SegmentAllocator, count: Long): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count).apply { fill(0) })

        @JvmName("calloc-981")
        public fun <T : NType> calloc(allocator: SegmentAllocator, count: Int): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-069")
        public fun <T : NType> calloc(count: ULong): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-420")
        public fun <T : NType> calloc(count: UInt): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-911")
        public fun <T : NType> calloc(count: Long): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-666")
        public fun <T : NType> calloc(count: Int): NArray<NPointer<T>> =
            NArray(allocator.allocate(layout, count.toLong()).apply { fill(0) })

        @JvmName("calloc-888")
        public fun <T : NType> calloc(allocator: SegmentAllocator): NValue<NPointer<T>> =
            NValue(allocator.allocate(layout).apply { fill(0) })

        context(allocator: SegmentAllocator)
        @JvmName("calloc-233")
        public fun <T : NType> calloc(): NValue<NPointer<T>> =
            NValue(allocator.allocate(layout).apply { fill(0) })
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