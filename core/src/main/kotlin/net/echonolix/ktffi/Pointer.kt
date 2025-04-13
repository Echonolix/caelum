package net.echonolix.ktffi

@JvmInline
public value class NativePointer<T : NativeType>(
    public val _address: Long,
) : NativeType {
    override val typeDescriptor: TypeDescriptor<NativePointer<*>>
        get() = Companion

    public inline operator fun invoke(block: NativePointer<T>.() -> Unit): NativePointer<T> {
        this.block()
        return this
    }

    public companion object : TypeDescriptor.Impl<NativePointer<*>>(APIHelper.pointerLayout) {
        @JvmStatic
        public fun <T : NativeType> fromNativeData(value: Long): NativePointer<T> = NativePointer<T>(value)

        @JvmStatic
        public fun <T : NativeType> toNativeData(value: NativePointer<T>): Long = value._address
    }
}

public val nullptr: NativePointer<*> = NativePointer<NativeChar>(0L)