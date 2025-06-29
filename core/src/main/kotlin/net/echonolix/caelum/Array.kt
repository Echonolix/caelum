@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum

import net.echonolix.caelum.APIHelper.`_$OMNI_SEGMENT$_`

public data class NArray<T : NType>(
    public val _address: Long,
    public val count: Long = 0L,
) {
    public inline fun ptr(): NPointer<T> = NPointer(_address)
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.get(index: Long): T {
    @Suppress("UNCHECKED_CAST")
    return NPointer<E>(NPointer.arrayVarHandle.get(`_$OMNI_SEGMENT$_`, _address, index) as Long) as T
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.set(index: Long, value: T) {
    NPointer.arrayVarHandle.set(`_$OMNI_SEGMENT$_`, _address, index, value._address)
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.get(index: Int): T {
    @Suppress("UNCHECKED_CAST")
    return NPointer<E>(NPointer.arrayVarHandle.get(`_$OMNI_SEGMENT$_`, _address, index.toLong()) as Long) as T
}

public operator fun <E : NType, T : NPointer<E>> NArray<T>.set(index: Int, value: T) {
    NPointer.arrayVarHandle.set(`_$OMNI_SEGMENT$_`, _address, index.toLong(), value._address)
}