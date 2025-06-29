package net.echonolix.caelum

import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.foreign.FunctionDescriptor
import java.lang.invoke.MethodHandle
import java.util.function.Supplier

public object APIHelper {
    public val `_$OMNI_SEGMENT$_`: MemorySegment = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE)
    public val LINKER: Linker = Linker.nativeLinker()
    public val LOADER_LOOKUP: SymbolLookup = SymbolLookup.loaderLookup()
    public val POINTER_LAYOUT: MemoryLayout = ValueLayout.JAVA_LONG
    public val SYMBOL_LOOKUP: SymbolLookup =
        SymbolLookup { name: String? ->
            val inLoader = LOADER_LOOKUP.find(name)
            if (inLoader.isEmpty()) {
                return@SymbolLookup LOADER_LOOKUP.find(name)
            } else {
                return@SymbolLookup inLoader
            }
        }

    public fun findSymbol(symbol: String): MemorySegment {
        return SYMBOL_LOOKUP.find(symbol)
            .orElseThrow(Supplier { RuntimeException("Unable to find symbol $symbol") })
    }

    public fun functionDescriptorOf(returnType: NType.Descriptor<*>?, vararg parameters: NType.Descriptor<*>): FunctionDescriptor {
        return if (returnType == null) {
            FunctionDescriptor.ofVoid(
                *parameters.map { it.layout }.toTypedArray()
            )
        } else {
            FunctionDescriptor.of(
                returnType.layout,
                *parameters.map { it.layout }.toTypedArray()
            )
        }
    }

    public fun downcallHandleOf(
        name: String,
        functionDescriptor: FunctionDescriptor,
    ): MethodHandle? {
        return downcallHandleOf(findSymbol(name), functionDescriptor)
    }

    public fun downcallHandleOf(
        functionAddress: MemorySegment,
        functionDescriptor: FunctionDescriptor,
    ): MethodHandle? {
        return if (functionAddress.address() == 0L) {
            null
        } else {
            LINKER.downcallHandle(functionAddress, functionDescriptor)
        }
    }
}