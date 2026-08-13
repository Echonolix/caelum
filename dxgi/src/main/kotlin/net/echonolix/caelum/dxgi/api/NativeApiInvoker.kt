package net.echonolix.caelum.dxgi.api

import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/** Generic downcall handles produced from audited schema declarations. */
public class NativeApiInvoker(
    public val catalog: NativeApiCatalog,
    private val symbolLookup: SymbolLookup? = null,
    private val linker: Linker = Linker.nativeLinker(),
    private val types: NativeTypeParser = catalog.nativeTypes(),
) {

    /** Creates an unbound handle whose first invocation argument is the target address. */
    public fun unbound(function: NativeFunctionDeclaration): MethodHandle =
        linker.downcallHandle(function.functionDescriptor(types))

    /** Creates an unbound COM handle whose first invocation argument is the vtable function address. */
    public fun unbound(method: NativeMethodDeclaration): MethodHandle =
        linker.downcallHandle(method.functionDescriptor(types))

    public fun function(name: String): MethodHandle {
        val declaration = catalog.requireFunction(name)
        val lookup = symbolLookup ?: throw IllegalStateException(
            "No SymbolLookup was supplied for ${catalog.api}; use unbound(declaration) or construct NativeApiInvoker with a DLL lookup",
        )
        val address = lookup.find(name).orElseThrow {
            NoSuchElementException("Native symbol '$name' was not found for ${catalog.api}")
        }
        return linker.downcallHandle(address, declaration.functionDescriptor(types))
    }

    /**
     * Resolves a flattened COM vtable slot and binds a downcall handle to it.
     * The schema method must retain its explicit `This` parameter.
     */
    public fun comMethod(
        instance: MemorySegment,
        interfaceName: String,
        methodName: String,
    ): MethodHandle {
        val declaration = catalog.requireInterface(interfaceName)
        val method = declaration.requireMethod(methodName)
        require(instance.address() != 0L) { "Cannot call $interfaceName::$methodName on a null COM pointer" }
        val vtable = instance.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L)
        check(vtable.address() != 0L) { "$interfaceName has a null vtable pointer" }
        val vtableBytes = Math.multiplyExact(declaration.vtableSize.toLong(), ValueLayout.ADDRESS.byteSize())
        val address = vtable.reinterpret(vtableBytes).get(
            ValueLayout.ADDRESS,
            Math.multiplyExact(method.slot.toLong(), ValueLayout.ADDRESS.byteSize()),
        )
        check(address.address() != 0L) { "$interfaceName::$methodName vtable slot ${method.slot} is null" }
        return linker.downcallHandle(address, method.functionDescriptor(types))
    }
}
