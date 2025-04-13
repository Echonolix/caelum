package net.echonolix.ktffi

import java.lang.foreign.*
import kotlin.jvm.optionals.getOrElse

public object APIHelper {
    @JvmField
    public val `_$OMNI_SEGMENT$_`: MemorySegment = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE)

    @JvmField
    public val linker: Linker = Linker.nativeLinker()

    @JvmField
    public val loaderLookup: SymbolLookup = SymbolLookup.loaderLookup()

    @JvmField
    public val pointerLayout: AddressLayout =
        ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE))

    @JvmField
    public val symbolLookup: SymbolLookup = SymbolLookup { name ->
        val inLoader = loaderLookup.find(name)
        if (inLoader.isEmpty) {
            loaderLookup.find(name)
        } else {
            inLoader
        }
    }

    @JvmStatic
    public fun pointerLayout(elementLayout: MemoryLayout): AddressLayout {
        return ValueLayout.ADDRESS.withTargetLayout(
            MemoryLayout.sequenceLayout(
                Long.MAX_VALUE / elementLayout.byteSize(),
                elementLayout
            )
        )
    }

    @JvmStatic
    public fun findSymbol(symbol: String): MemorySegment {
        return symbolLookup.find(symbol).getOrElse { error("unable to find symbol $symbol") }
    }
}