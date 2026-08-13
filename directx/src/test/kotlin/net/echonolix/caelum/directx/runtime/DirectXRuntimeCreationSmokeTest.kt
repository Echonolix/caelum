package net.echonolix.caelum.directx.runtime

import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import net.echonolix.caelum.directx.D3D10EntryPoints
import net.echonolix.caelum.directx.D3D11EntryPoints
import net.echonolix.caelum.directx.D3D12EntryPoints
import net.echonolix.caelum.directx.D3D9EntryPoints
import net.echonolix.caelum.directx.D3DDriverType
import net.echonolix.caelum.directx.D3DFeatureLevel
import net.echonolix.caelum.directx.DirectXEntryPoint
import net.echonolix.caelum.directx.DirectXRuntime
import net.echonolix.caelum.directx.DirectXSdkVersions
import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import net.echonolix.caelum.dxgi.com.ComInterface
import net.echonolix.caelum.dxgi.com.ComPtr
import net.echonolix.caelum.dxgi.com.Guid
import net.echonolix.caelum.dxgi.com.HResult
import net.echonolix.caelum.dxgi.com.IUnknown
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Exercises the process creation ABI for each Direct3D generation.
 *
 * This is deliberately a runtime/ABI smoke, not a GPU-capability test: D3D10
 * and D3D11 use WARP, and D3D12 asks Windows for its default adapter. Every
 * native out cell is zeroed before the call and every successful COM reference
 * is adopted by [ComPtr], which releases it exactly once on scope exit.
 */
public class DirectXRuntimeCreationSmokeTest {
    @Test
    public fun `Windows x64 Direct3D creation ABIs create and release base objects`() {
        val runtime = DirectXRuntime.probe()
        assumeTrue(runtime.platformSupported, "Direct3D creation smoke requires Windows x64")

        createD3D9(runtime)
        createD3D10WarpDevice(runtime)
        createD3D11WarpDevice(runtime)
        createD3D12DefaultDevice(runtime)
    }

    private fun createD3D9(runtime: DirectXRuntime) {
        val direct3D9 = interfaceType(DirectXVersion.DIRECT3D_9, "IDirect3D9")
        val create = function(runtime, DirectXVersion.DIRECT3D_9, D3D9EntryPoints.direct3DCreate9)
        val address = create.invokeWithArguments(DirectXSdkVersions.D3D9) as MemorySegment
        require(address.address() != 0L) { "Direct3DCreate9 returned a null IDirect3D9 pointer" }
        ComPtr.adopt(address, direct3D9).use { }

        runtime.symbolOrNull(D3D9EntryPoints.direct3DCreate9Ex)?.let { symbol ->
            val createEx = Linker.nativeLinker().downcallHandle(
                symbol,
                DirectXApiCatalog.functionDescriptor(DirectXVersion.DIRECT3D_9, "Direct3DCreate9Ex"),
            )
            val direct3D9Ex = interfaceType(DirectXVersion.DIRECT3D_9, "IDirect3D9Ex")
            Arena.ofConfined().use { arena ->
                val output = zeroedAddressCell(arena)
                val result = HResult(createEx.invokeWithArguments(DirectXSdkVersions.D3D9, output) as Int)
                result.check("Direct3DCreate9Ex")
                ComPtr.adopt(nonNullOutput(output, "Direct3DCreate9Ex"), direct3D9Ex).use { }
            }
        }
    }

    private fun createD3D10WarpDevice(runtime: DirectXRuntime) {
        val type = interfaceType(DirectXVersion.DIRECT3D_10, "ID3D10Device")
        val create = function(runtime, DirectXVersion.DIRECT3D_10, D3D10EntryPoints.createDevice)
        Arena.ofConfined().use { arena ->
            val output = zeroedAddressCell(arena)
            val result = HResult(
                create.invokeWithArguments(
                    MemorySegment.NULL,
                    D3DDriverType.WARP.rawValue,
                    MemorySegment.NULL,
                    0,
                    DirectXSdkVersions.D3D10,
                    output,
                ) as Int,
            )
            result.check("D3D10CreateDevice(WARP)")
            ComPtr.adopt(nonNullOutput(output, "D3D10CreateDevice(WARP)"), type).use { }
        }
    }

    private fun createD3D11WarpDevice(runtime: DirectXRuntime) {
        val deviceType = interfaceType(DirectXVersion.DIRECT3D_11, "ID3D11Device")
        val contextType = interfaceType(DirectXVersion.DIRECT3D_11, "ID3D11DeviceContext")
        val create = function(runtime, DirectXVersion.DIRECT3D_11, D3D11EntryPoints.createDevice)
        Arena.ofConfined().use { arena ->
            val deviceOutput = zeroedAddressCell(arena)
            val featureLevelOutput = arena.allocate(ValueLayout.JAVA_INT).also {
                it.set(ValueLayout.JAVA_INT, 0L, 0)
            }
            val contextOutput = zeroedAddressCell(arena)
            val result = HResult(
                create.invokeWithArguments(
                    MemorySegment.NULL,
                    D3DDriverType.WARP.rawValue,
                    MemorySegment.NULL,
                    0,
                    MemorySegment.NULL,
                    0,
                    DirectXSdkVersions.D3D11,
                    deviceOutput,
                    featureLevelOutput,
                    contextOutput,
                ) as Int,
            )
            result.check("D3D11CreateDevice(WARP)")
            ComPtr.adopt(nonNullOutput(deviceOutput, "D3D11CreateDevice(WARP) device"), deviceType).use {
                ComPtr.adopt(nonNullOutput(contextOutput, "D3D11CreateDevice(WARP) context"), contextType).use { }
            }
        }
    }

    private fun createD3D12DefaultDevice(runtime: DirectXRuntime) {
        val type = interfaceType(DirectXVersion.DIRECT3D_12, "ID3D12Device")
        val create = function(runtime, DirectXVersion.DIRECT3D_12, D3D12EntryPoints.createDevice)
        Arena.ofConfined().use { arena ->
            val iid = type.iid.allocate(arena)
            val output = zeroedAddressCell(arena)
            val result = HResult(
                create.invokeWithArguments(
                    MemorySegment.NULL,
                    D3DFeatureLevel.LEVEL_11_0.rawValue,
                    iid,
                    output,
                ) as Int,
            )
            result.check("D3D12CreateDevice(default adapter)")
            ComPtr.adopt(nonNullOutput(output, "D3D12CreateDevice(default adapter)"), type).use { }
        }
    }

    private fun interfaceType(version: DirectXVersion, name: String): ComInterface<IUnknown> {
        val declaration = DirectXApiCatalog.profile(version).primary.requireInterface(name)
        val iid = requireNotNull(declaration.iid) { "$name has no IID in the ${version.name} catalog" }
        return ComInterface(name, Guid.parse(iid), declaration.vtableSize)
    }

    private fun zeroedAddressCell(arena: Arena): MemorySegment =
        arena.allocate(ValueLayout.ADDRESS).also { it.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL) }

    private fun nonNullOutput(output: MemorySegment, operation: String): MemorySegment =
        output.get(ValueLayout.ADDRESS, 0L).also {
            require(it.address() != 0L) { "$operation succeeded but returned a null COM pointer" }
        }

    private fun function(
        runtime: DirectXRuntime,
        version: DirectXVersion,
        entryPoint: DirectXEntryPoint,
    ): MethodHandle {
        val symbol = runtime.symbolOrNull(entryPoint)
        assertTrue(symbol != null, "${entryPoint.name} must be exported on supported Windows x64")
        return Linker.nativeLinker().downcallHandle(
            requireNotNull(symbol),
            DirectXApiCatalog.functionDescriptor(version, entryPoint.name),
        )
    }
}
