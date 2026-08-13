package net.echonolix.caelum.directx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class DirectXEntryPointsTest {
    @Test
    public fun `entry-point names are unique inside each native library`() {
        DirectXLibrary.entries.forEach { library ->
            val entries: List<DirectXEntryPoint> = DirectXEntryPoints.forLibrary(library)
            assertEquals(entries.size, entries.map(DirectXEntryPoint::name).distinct().size, library.fileName)
        }
    }

    @Test
    public fun `D3D12 catalog includes creation debug interface and root signature exports`() {
        val names: Set<String> = D3D12EntryPoints.all.map(DirectXEntryPoint::name).toSet()
        assertTrue("D3D12CreateDevice" in names)
        assertTrue("D3D12GetDebugInterface" in names)
        assertTrue("D3D12GetInterface" in names)
        assertTrue("D3D12SerializeRootSignature" in names)
        assertTrue("D3D12SerializeVersionedRootSignature" in names)
        assertTrue("D3D12CreateVersionedRootSignatureDeserializerFromSubobjectInLibrary" in names)
        assertEquals(9, names.size)
    }

    @Test
    public fun `D3D12 compiler exports use the Agility SDK state-object compiler DLL`() {
        assertEquals(2, D3D12CompilerEntryPoints.all.size)
        D3D12CompilerEntryPoints.all.forEach {
            assertEquals(DirectXLibrary.D3D12_STATE_OBJECT_COMPILER, it.library)
        }
        assertEquals(11, D3D12EntryPoints.all.size + D3D12CompilerEntryPoints.all.size)
    }

    @Test
    public fun `D3D10 point-one creation is resolved from its own DLL`() {
        assertEquals(DirectXLibrary.D3D10, D3D10EntryPoints.createDevice.library)
        assertEquals(DirectXLibrary.D3D10_1, D3D10EntryPoints.createDevice1.library)
        assertEquals(DirectXLibrary.D3D10_1, D3D10EntryPoints.createDeviceAndSwapChain1.library)
    }

    @Test
    public fun `D3DCompiler 47 catalog covers every public function in d3dcompiler header`() {
        assertEquals(25, D3DCompiler47EntryPoints.all.size)
        assertTrue(D3DCompiler47EntryPoints.all.any { it.name == "D3DCompile" })
        assertTrue(D3DCompiler47EntryPoints.all.any { it.name == "D3DReflectLibrary" })
        assertTrue(D3DCompiler47EntryPoints.all.any { it.name == "D3DDecompressShaders" })
    }

    @Test
    public fun `a synthetic unsupported runtime reports every version without claiming availability`() {
        val capabilities: DirectXCapabilities = DirectXRuntime.probe().capabilities()
        assertEquals(DirectXVersion.entries.toSet(), capabilities.versions.map { it.version }.toSet())
        if (!capabilities.platformSupported) {
            assertTrue(capabilities.availableVersions.isEmpty())
            assertFalse(capabilities.compiler47Available)
        }
    }
}
