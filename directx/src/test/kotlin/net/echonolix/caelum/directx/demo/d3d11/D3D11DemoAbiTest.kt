package net.echonolix.caelum.directx.demo

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout
import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import net.echonolix.caelum.dxgi.api.NativeApiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guards the small handwritten ABI surface used by the executable D3D11 sample. */
public class D3D11DemoAbiTest {
    @Test
    public fun `demo exports and COM calls match the audited schemas`() {
        val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_11)
        val d3d11 = profile.primary
        val dxgi = profile.catalogs.single { it.api == "dxgi" }
        val common = profile.catalogs.single { it.api == "d3dcommon-compiler" }

        assertFunctionAbi(
            d3d11,
            "D3D11CreateDeviceAndSwapChain",
            D3D11Descriptors.CREATE_DEVICE_AND_SWAP_CHAIN,
            profile.types,
        )
        assertFunctionAbi(common, "D3DCompile", D3D11Descriptors.D3D_COMPILE, profile.types)

        val calls = listOf(
            MethodAbi(dxgi, "IDXGISwapChain", "Release", D3D11Slots.UNKNOWN_RELEASE, D3D11Descriptors.RELEASE),
            MethodAbi(d3d11, "ID3D11Device", "Release", D3D11Slots.UNKNOWN_RELEASE, D3D11Descriptors.RELEASE),
            MethodAbi(common, "ID3D10Blob", "Release", D3D11Slots.UNKNOWN_RELEASE, D3D11Descriptors.RELEASE),
            MethodAbi(common, "ID3D10Blob", "GetBufferPointer", D3D11Slots.BLOB_GET_BUFFER_POINTER, D3D11Descriptors.BLOB_GET_POINTER),
            MethodAbi(common, "ID3D10Blob", "GetBufferSize", D3D11Slots.BLOB_GET_BUFFER_SIZE, D3D11Descriptors.BLOB_GET_SIZE),

            MethodAbi(dxgi, "IDXGISwapChain", "Present", D3D11Slots.SWAP_CHAIN_PRESENT, D3D11Descriptors.PRESENT),
            MethodAbi(dxgi, "IDXGISwapChain", "GetBuffer", D3D11Slots.SWAP_CHAIN_GET_BUFFER, D3D11Descriptors.GET_BUFFER),

            MethodAbi(d3d11, "ID3D11Device", "CreateBuffer", D3D11Slots.DEVICE_CREATE_BUFFER, D3D11Descriptors.CREATE_BUFFER),
            MethodAbi(d3d11, "ID3D11Device", "CreateTexture2D", D3D11Slots.DEVICE_CREATE_TEXTURE2D, D3D11Descriptors.CREATE_TEXTURE_2D),
            MethodAbi(d3d11, "ID3D11Device", "CreateRenderTargetView", D3D11Slots.DEVICE_CREATE_RENDER_TARGET_VIEW, D3D11Descriptors.CREATE_VIEW),
            MethodAbi(d3d11, "ID3D11Device", "CreateDepthStencilView", D3D11Slots.DEVICE_CREATE_DEPTH_STENCIL_VIEW, D3D11Descriptors.CREATE_VIEW),
            MethodAbi(d3d11, "ID3D11Device", "CreateInputLayout", D3D11Slots.DEVICE_CREATE_INPUT_LAYOUT, D3D11Descriptors.CREATE_INPUT_LAYOUT),
            MethodAbi(d3d11, "ID3D11Device", "CreateVertexShader", D3D11Slots.DEVICE_CREATE_VERTEX_SHADER, D3D11Descriptors.CREATE_SHADER),
            MethodAbi(d3d11, "ID3D11Device", "CreatePixelShader", D3D11Slots.DEVICE_CREATE_PIXEL_SHADER, D3D11Descriptors.CREATE_SHADER),
            MethodAbi(d3d11, "ID3D11Device", "CreateRasterizerState", D3D11Slots.DEVICE_CREATE_RASTERIZER_STATE, D3D11Descriptors.CREATE_RASTERIZER_STATE),
            MethodAbi(d3d11, "ID3D11Device", "GetDeviceRemovedReason", D3D11Slots.DEVICE_GET_REMOVED_REASON, D3D11Descriptors.NO_ARGUMENTS),

            MethodAbi(d3d11, "ID3D11DeviceContext", "VSSetConstantBuffers", D3D11Slots.CONTEXT_VS_SET_CONSTANT_BUFFERS, D3D11Descriptors.SET_CONSTANT_BUFFERS),
            MethodAbi(d3d11, "ID3D11DeviceContext", "PSSetShader", D3D11Slots.CONTEXT_PS_SET_SHADER, D3D11Descriptors.SET_SHADER),
            MethodAbi(d3d11, "ID3D11DeviceContext", "VSSetShader", D3D11Slots.CONTEXT_VS_SET_SHADER, D3D11Descriptors.SET_SHADER),
            MethodAbi(d3d11, "ID3D11DeviceContext", "DrawIndexed", D3D11Slots.CONTEXT_DRAW_INDEXED, D3D11Descriptors.DRAW_INDEXED),
            MethodAbi(d3d11, "ID3D11DeviceContext", "Map", D3D11Slots.CONTEXT_MAP, D3D11Descriptors.MAP),
            MethodAbi(d3d11, "ID3D11DeviceContext", "Unmap", D3D11Slots.CONTEXT_UNMAP, D3D11Descriptors.UNMAP),
            MethodAbi(d3d11, "ID3D11DeviceContext", "PSSetConstantBuffers", D3D11Slots.CONTEXT_PS_SET_CONSTANT_BUFFERS, D3D11Descriptors.SET_CONSTANT_BUFFERS),
            MethodAbi(d3d11, "ID3D11DeviceContext", "IASetInputLayout", D3D11Slots.CONTEXT_IA_SET_INPUT_LAYOUT, D3D11Descriptors.ONE_OBJECT),
            MethodAbi(d3d11, "ID3D11DeviceContext", "IASetVertexBuffers", D3D11Slots.CONTEXT_IA_SET_VERTEX_BUFFERS, D3D11Descriptors.IA_SET_VERTEX_BUFFERS),
            MethodAbi(d3d11, "ID3D11DeviceContext", "IASetIndexBuffer", D3D11Slots.CONTEXT_IA_SET_INDEX_BUFFER, D3D11Descriptors.IA_SET_INDEX_BUFFER),
            MethodAbi(d3d11, "ID3D11DeviceContext", "IASetPrimitiveTopology", D3D11Slots.CONTEXT_IA_SET_PRIMITIVE_TOPOLOGY, D3D11Descriptors.ONE_INT),
            MethodAbi(d3d11, "ID3D11DeviceContext", "OMSetRenderTargets", D3D11Slots.CONTEXT_OM_SET_RENDER_TARGETS, D3D11Descriptors.OM_SET_RENDER_TARGETS),
            MethodAbi(d3d11, "ID3D11DeviceContext", "RSSetState", D3D11Slots.CONTEXT_RS_SET_STATE, D3D11Descriptors.ONE_OBJECT),
            MethodAbi(d3d11, "ID3D11DeviceContext", "RSSetViewports", D3D11Slots.CONTEXT_RS_SET_VIEWPORTS, D3D11Descriptors.RS_SET_VIEWPORTS),
            MethodAbi(d3d11, "ID3D11DeviceContext", "CopyResource", D3D11Slots.CONTEXT_COPY_RESOURCE, D3D11Descriptors.COPY_RESOURCE),
            MethodAbi(d3d11, "ID3D11DeviceContext", "UpdateSubresource", D3D11Slots.CONTEXT_UPDATE_SUBRESOURCE, D3D11Descriptors.UPDATE_SUBRESOURCE),
            MethodAbi(d3d11, "ID3D11DeviceContext", "ClearRenderTargetView", D3D11Slots.CONTEXT_CLEAR_RTV, D3D11Descriptors.CLEAR_RTV),
            MethodAbi(d3d11, "ID3D11DeviceContext", "ClearDepthStencilView", D3D11Slots.CONTEXT_CLEAR_DSV, D3D11Descriptors.CLEAR_DSV),
            MethodAbi(d3d11, "ID3D11DeviceContext", "ClearState", D3D11Slots.CONTEXT_CLEAR_STATE, D3D11Descriptors.VOID_NO_ARGUMENTS),
            MethodAbi(d3d11, "ID3D11DeviceContext", "Flush", D3D11Slots.CONTEXT_FLUSH, D3D11Descriptors.VOID_NO_ARGUMENTS),
        )

        calls.forEach { call ->
            val method = call.catalog.requireInterface(call.interfaceName).requireMethod(call.methodName)
            val label = "${call.interfaceName}.${call.methodName}"
            assertEquals(method.slot, call.slot, "$label vtable slot")
            assertEquals(method.functionDescriptor(profile.types), call.descriptor, "$label FFM descriptor")
        }
    }

    @Test
    public fun `outward counter clockwise teapot faces are configured as D3D11 front faces`() {
        assertEquals(40L, D3D11Layouts.D3D11_RASTERIZER_DESC.byteSize())
        assertEquals(4L, D3D11Layouts.D3D11_RASTERIZER_DESC.byteAlignment())
        Arena.ofConfined().use { arena ->
            val description = arena.allocate(D3D11Layouts.D3D11_RASTERIZER_DESC)
            D3D11Layouts.initializeTeapotRasterizerDesc(description)

            assertEquals(D3D11_FILL_SOLID, description.get(ValueLayout.JAVA_INT, 0L), "FillMode")
            assertEquals(D3D11_CULL_BACK, description.get(ValueLayout.JAVA_INT, 4L), "CullMode")
            assertEquals(1, description.get(ValueLayout.JAVA_INT, 8L), "FrontCounterClockwise")
            assertEquals(1, description.get(ValueLayout.JAVA_INT, 24L), "DepthClipEnable")
        }
    }

    private fun assertFunctionAbi(
        catalog: NativeApiCatalog,
        name: String,
        actual: FunctionDescriptor,
        types: net.echonolix.caelum.dxgi.api.NativeTypeParser,
    ) {
        assertEquals(catalog.requireFunction(name).functionDescriptor(types), actual, "$name FFM descriptor")
    }

    private data class MethodAbi(
        val catalog: NativeApiCatalog,
        val interfaceName: String,
        val methodName: String,
        val slot: Int,
        val descriptor: FunctionDescriptor,
    )
}
