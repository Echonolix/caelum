package net.echonolix.caelum.dxgi

import net.echonolix.caelum.dxgi.com.ComInterface
import net.echonolix.caelum.dxgi.com.ComPtr
import net.echonolix.caelum.dxgi.com.HResult
import net.echonolix.caelum.dxgi.com.IUnknown
import net.echonolix.caelum.dxgi.win32.WindowsLibrary
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/** Typed entry points exported by dxgi.dll. */
public object Dxgi {
    public const val CREATE_FACTORY_DEBUG: Int = 0x1

    private val library: Lazy<WindowsLibrary?> = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (isSupportedPlatform) WindowsLibrary.openOrNull("dxgi") else null
    }
    private val debugLibrary: Lazy<WindowsLibrary?> = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (isSupportedPlatform) WindowsLibrary.openOrNull("dxgidebug") else null
    }

    public val isSupportedPlatform: Boolean
        get() = WindowsLibrary.isSupportedPlatform

    public val isAvailable: Boolean get() = library.value != null
    public val isDebugAvailable: Boolean get() = debugLibrary.value != null

    public fun libraryOrNull(): WindowsLibrary? = library.value
    public fun debugLibraryOrNull(): WindowsLibrary? = debugLibrary.value

    public fun createFactory(): ComPtr<IDXGIFactory> = createFactory(DxgiInterfaces.IDXGIFactory)

    public fun <T : IDXGIFactory> createFactory(type: ComInterface<T>): ComPtr<T> =
        create(type, "CreateDXGIFactory", CREATE_FACTORY_DESCRIPTOR)

    public fun createFactory1(): ComPtr<IDXGIFactory1> = createFactory1(DxgiInterfaces.IDXGIFactory1)

    public fun <T : IDXGIFactory> createFactory1(type: ComInterface<T>): ComPtr<T> =
        create(type, "CreateDXGIFactory1", CREATE_FACTORY_DESCRIPTOR)

    public fun createFactory2(flags: Int = 0): ComPtr<IDXGIFactory2> =
        createFactory2(flags, DxgiInterfaces.IDXGIFactory2)

    public fun <T : IDXGIFactory> createFactory2(flags: Int, type: ComInterface<T>): ComPtr<T> {
        val function = requireLibrary().downcall("CreateDXGIFactory2", CREATE_FACTORY2_DESCRIPTOR)
        return create(type, "CreateDXGIFactory2") { iid, output ->
            function.invokeExact(flags, iid, output) as Int
        }
    }

    public fun getDebugInterface(): ComPtr<IDXGIDebug> = getDebugInterface(DxgiInterfaces.IDXGIDebug)

    public fun <T : IUnknown> getDebugInterface(type: ComInterface<T>): ComPtr<T> {
        val function = requireDebugLibrary().downcall("DXGIGetDebugInterface", CREATE_FACTORY_DESCRIPTOR)
        return createComObject(type, "DXGIGetDebugInterface") { iid, output ->
            function.invokeExact(iid, output) as Int
        }
    }

    public fun getDebugInterface1(flags: Int = 0): ComPtr<IDXGIDebug1> =
        getDebugInterface1(flags, DxgiInterfaces.IDXGIDebug1)

    public fun <T : IUnknown> getDebugInterface1(flags: Int, type: ComInterface<T>): ComPtr<T> {
        val function = requireLibrary().downcall("DXGIGetDebugInterface1", CREATE_FACTORY2_DESCRIPTOR)
        return createComObject(type, "DXGIGetDebugInterface1") { iid, output ->
            function.invokeExact(flags, iid, output) as Int
        }
    }

    public fun declareAdapterRemovalSupport(): HResult {
        val function = requireLibrary().downcall("DXGIDeclareAdapterRemovalSupport", HRESULT_NO_ARGS_DESCRIPTOR)
        return HResult(function.invokeExact() as Int).check("DXGIDeclareAdapterRemovalSupport")
    }

    public fun disableVBlankVirtualization(): HResult {
        val function = requireLibrary().downcall("DXGIDisableVBlankVirtualization", HRESULT_NO_ARGS_DESCRIPTOR)
        return HResult(function.invokeExact() as Int).check("DXGIDisableVBlankVirtualization")
    }

    private fun <T : IDXGIFactory> create(
        type: ComInterface<T>,
        operation: String,
        descriptor: FunctionDescriptor,
    ): ComPtr<T> {
        val function = requireLibrary().downcall(operation, descriptor)
        return create(type, operation) { iid, output -> function.invokeExact(iid, output) as Int }
    }

    private inline fun <T : IDXGIFactory> create(
        type: ComInterface<T>,
        operation: String,
        invoke: (MemorySegment, MemorySegment) -> Int,
    ): ComPtr<T> = createComObject(type, operation, invoke)

    private inline fun <T : IUnknown> createComObject(
        type: ComInterface<T>,
        operation: String,
        invoke: (MemorySegment, MemorySegment) -> Int,
    ): ComPtr<T> = Arena.ofConfined().use { arena ->
        val iid = type.iid.allocate(arena)
        val output = arena.allocate(ValueLayout.ADDRESS)
        output.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
        HResult(invoke(iid, output)).check(operation)
        val address = output.get(ValueLayout.ADDRESS, 0L)
        check(address.address() != 0L) { "$operation succeeded but returned a null ${type.name} pointer" }
        ComPtr.adopt(address, type)
    }

    private fun requireLibrary(): WindowsLibrary = library.value ?: throw UnsupportedOperationException(
        "DXGI requires Windows x64 and an available dxgi.dll",
    )

    private fun requireDebugLibrary(): WindowsLibrary = debugLibrary.value ?: throw UnsupportedOperationException(
        "DXGIGetDebugInterface requires Windows x64 and an available dxgidebug.dll",
    )

    private val CREATE_FACTORY_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
    )
    private val CREATE_FACTORY2_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
    )
    private val HRESULT_NO_ARGS_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT)
}
