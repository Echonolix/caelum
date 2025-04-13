package net.echonolix.ktffi

@JvmInline
value class NativePointer<T : NativeType>(
    val _address: Long,
) : NativeType {
    override val typeDescriptor: TypeDescriptor<NativePointer<*>>
        get() = Companion

    inline operator fun invoke(block: NativePointer<T>.() -> Unit): NativePointer<T> {
        this.block()
        return this
    }

    companion object : TypeDescriptor.Impl<NativePointer<*>>(APIHelper.pointerLayout) {
        @JvmStatic
        fun <T : NativeType> fromInt(value: Long) = NativePointer<T>(value)

        @JvmStatic
        fun <T : NativeType> toInt(value: NativePointer<T>) = value._address
    }
}

val nullptr: NativePointer<*> = NativePointer<uint8_t>(0L)