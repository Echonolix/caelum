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

    companion object : TypeDescriptor.Impl<NativePointer<*>>(APIHelper.pointerLayout)
}

val nullptr: NativePointer<*> = NativePointer<uint8_t>(0L)