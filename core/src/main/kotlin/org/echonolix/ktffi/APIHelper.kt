package org.echonolix.ktffi

import java.lang.foreign.AddressLayout
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import kotlin.jvm.optionals.getOrElse

object APIHelper {
    @JvmField
    val `_$OMNI_SEGMENT$_` = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE)

    @JvmField
    val linker: Linker = Linker.nativeLinker()

    @JvmField
    val loaderLookup: SymbolLookup = SymbolLookup.loaderLookup()

    @JvmField
    val pointerLayout: AddressLayout =
        ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE))

    @JvmField
    val symbolLookup: SymbolLookup = SymbolLookup { name ->
        val inLoader = loaderLookup.find(name)
        if (inLoader.isEmpty) {
            loaderLookup.find(name)
        } else {
            inLoader
        }
    }

    @JvmStatic
    fun pointerLayout(elementLayout: MemoryLayout): AddressLayout {
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