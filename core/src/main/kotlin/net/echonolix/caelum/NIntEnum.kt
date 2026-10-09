package net.echonolix.caelum

/** Base of generated C enums, which use a 32-bit `int` representation. */
public interface NIntEnum<T : NIntEnum<T>> : NEnum<T, Int> {
    override val nType: NPrimitive<Int, Int> get() = NInt32
    override val value: Int
}
