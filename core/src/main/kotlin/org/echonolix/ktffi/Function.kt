package org.echonolix.ktffi

import java.lang.foreign.*
import kotlin.jvm.optionals.getOrElse

public object `$FunctionHelper` {
    @JvmStatic
    public val linker: Linker = Linker.nativeLinker()

    @JvmStatic
    public val loaderLookup: SymbolLookup = SymbolLookup.loaderLookup()

    @JvmStatic
    public val pointerLayout: AddressLayout =
        ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE))

    @JvmStatic
    public val symbolLookup: SymbolLookup = SymbolLookup { name ->
        val inLoader = loaderLookup.find(name)
        if (inLoader.isEmpty) {
            loaderLookup.find(name)
        } else {
            inLoader
        }
    }

    @JvmStatic
    public fun findSymbol(symbol: String): MemorySegment =
        symbolLookup.find(symbol).getOrElse { error("unable to find symbol $symbol") }
}
