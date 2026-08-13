package net.echonolix.caelum.directx.api

import net.echonolix.caelum.directx.DirectXVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.lang.foreign.MemoryLayout
import net.echonolix.caelum.dxgi.api.DxgiApiCatalog

public class DirectXApiCatalogTest {
    @Test
    public fun `loads every Direct3D generation from the resource index`() {
        val catalogs = DirectXApiCatalog.loadAll()

        assertEquals(DirectXVersion.entries.toSet(), catalogs.keys)
        assertEquals("d3d9", catalogs.getValue(DirectXVersion.DIRECT3D_9).api)
        assertEquals("d3d10", catalogs.getValue(DirectXVersion.DIRECT3D_10).api)
        assertEquals("d3d11", catalogs.getValue(DirectXVersion.DIRECT3D_11).api)
        assertEquals("d3d12", catalogs.getValue(DirectXVersion.DIRECT3D_12).api)
        assertTrue(catalogs.getValue(DirectXVersion.DIRECT3D_12).interfaces.size >= 100)
        assertTrue(catalogs.getValue(DirectXVersion.DIRECT3D_12).requireInterface("ID3D12Device15").vtableSize > 3)

        val d3d11 = catalogs.getValue(DirectXVersion.DIRECT3D_11)
        assertTrue(d3d11.requireInterface("ID3D11Device5").iid != null)
        assertTrue(d3d11.requireInterface("ID3D11Device5").requireMethod("CreateBuffer").parameters.isNotEmpty())
        val compilerCatalog = net.echonolix.caelum.dxgi.api.NativeApiResourceIndex
            .loadResource(DirectXApiCatalog.INDEX_RESOURCE)
            .load("d3dcommon-compiler")
        assertTrue(compilerCatalog.requireFunction("D3DCompile").functionDescriptor(compilerCatalog.nativeTypes()).argumentLayouts().isNotEmpty())

        val d3d11Profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_11)
        val createDevice = d3d11Profile.functionDescriptor("D3D11CreateDevice")
        assertEquals(10, createDevice.argumentLayouts().size)
        assertEquals(4, d3d11Profile.types.layout("D3D_DRIVER_TYPE").byteSize())
        assertEquals(4, d3d11Profile.types.layout("DXGI_FORMAT").byteSize())

        val d3d12Profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12)
        assertTrue(d3d12Profile.functionDescriptor("D3D12CreateDevice").argumentLayouts().isNotEmpty())
        assertEquals(4, d3d12Profile.types.layout("DXGI_FORMAT").byteSize())
        assertTrue(d3d12Profile.comMethodDescriptor("ID3D12Device15", "CreateCommittedResource3").argumentLayouts().isNotEmpty())

        val rtv = d3d11Profile.types.layout("D3D11_RENDER_TARGET_VIEW_DESC")
        assertEquals(20, rtv.byteSize())
        assertEquals(8, rtv.byteOffset(MemoryLayout.PathElement.groupElement("D3D11_RENDER_TARGET_VIEW_DESC::\$anonymous1")))
        val resource = d3d12Profile.types.layout("D3D12_RESOURCE_DESC")
        assertEquals(56, resource.byteSize())
        assertEquals(32, resource.byteOffset(MemoryLayout.PathElement.groupElement("Format")))
        val ray = d3d12Profile.types.layout("D3D12_RAYTRACING_INSTANCE_DESC")
        assertEquals(64, ray.byteSize())
    }

    @Test
    public fun `every raw function and COM method has an FFM descriptor`() {
        for (version in DirectXVersion.entries) {
            val profile = DirectXApiCatalog.profile(version)
            val failures = buildList {
                for (function in profile.primary.declarations.functions) {
                    runCatching { function.functionDescriptor(profile.types) }
                        .exceptionOrNull()?.let { add("${profile.primary.api}::${function.name}: ${it.message}") }
                }
                for (type in profile.primary.declarations.interfaces) {
                    for (method in type.methods) {
                        runCatching { method.functionDescriptor(profile.types) }
                            .exceptionOrNull()?.let { add("${type.name}::${method.name}: ${it.message}") }
                    }
                }
            }
            assertTrue(failures.isEmpty(), failures.take(25).joinToString("\n"))
        }
    }

    @Test
    public fun `all audited records produce exact layouts`() {
        val profiles = DirectXVersion.entries.map(DirectXApiCatalog::profile)
        val dxgi = DxgiApiCatalog.load()
        val common = net.echonolix.caelum.dxgi.api.NativeApiResourceIndex
            .loadResource(DirectXApiCatalog.INDEX_RESOURCE).load("d3dcommon-compiler")
        val catalogs = profiles.map { it.primary to it.types } +
            listOf(dxgi to dxgi.nativeTypes(), common to common.nativeTypes())
        val failures = buildList {
            for ((catalog, types) in catalogs) {
                for (record in catalog.declarations.records) {
                    runCatching {
                        val layout = types.layout(record.name)
                        check(layout.byteSize() == record.size) { "size ${layout.byteSize()} != ${record.size}" }
                        check(layout.byteAlignment() == record.alignment) { "align ${layout.byteAlignment()} != ${record.alignment}" }
                    }.exceptionOrNull()?.let { add("${catalog.api}::${record.name}: ${it.message}") }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.take(25).joinToString("\n"))
    }

    @Test
    public fun `function DLL ownership matches SDK exports`() {
        val index = net.echonolix.caelum.dxgi.api.NativeApiResourceIndex.loadResource(DirectXApiCatalog.INDEX_RESOURCE)
        assertEquals("d3d10_1.dll", index.load("d3d10").requireFunction("D3D10CreateDevice1").dll)
        assertEquals(
            "D3D12StateObjectCompiler.dll",
            index.load("d3d12").requireFunction("D3D12CompilerCreateFactory").dll,
        )
        val dxgi = DxgiApiCatalog.load()
        assertEquals("dxgi.dll", dxgi.requireFunction("DXGIGetDebugInterface1").dll)
        assertEquals("dxgidebug.dll", dxgi.requireFunction("DXGIGetDebugInterface").dll)
    }
}
