package net.echonolix.caelum.directx.demo.d3d12

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.directx.api.DirectXApiCatalog
import net.echonolix.caelum.directx.demo.d3d12.dxr.D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS
import net.echonolix.caelum.directx.demo.d3d12.dxr.DxrLayoutContract
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the small handwritten D3D12 ABI surface that reaches the driver. These
 * checks deliberately remain pure JVM tests: a slot or layout mismatch is caught
 * before a hardware-dependent demo can turn it into a native process failure.
 */
public class D3D12DemoAbiTest {
    @Test
    public fun `production PSO bytes preserve the mesh outward counter clockwise convention`() {
        Arena.ofConfined().use { arena ->
            val rasterizer = arena.allocate(44, 4)
            writeTeapotRasterizerState(rasterizer)
            assertEquals(3, rasterizer.get(ValueLayout.JAVA_INT, 0), "D3D12_FILL_MODE_SOLID")
            assertEquals(3, rasterizer.get(ValueLayout.JAVA_INT, 4), "D3D12_CULL_MODE_BACK")
            assertEquals(1, rasterizer.get(ValueLayout.JAVA_INT, 8), "CCW teapot faces must be front faces")
            assertEquals(1, rasterizer.get(ValueLayout.JAVA_INT, 24), "projection depth must remain clipped")

            val schema = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12).primary.requireRecord("D3D12_RASTERIZER_DESC")
            assertEquals(44L, schema.size)
            assertEquals(0L, schema.fields.single { it.name == "FillMode" }.offsetBits)
            assertEquals(32L, schema.fields.single { it.name == "CullMode" }.offsetBits)
            assertEquals(64L, schema.fields.single { it.name == "FrontCounterClockwise" }.offsetBits)
            assertEquals(192L, schema.fields.single { it.name == "DepthClipEnable" }.offsetBits)
        }
    }

    @Test
    public fun `descriptor handles use the generated D3D12 ABI including by value view handles`() {
        val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12)
        assertMethod(profile, "ID3D12Device", "CreateRenderTargetView", D3D12Slots.DEVICE_CREATE_RTV, D3D12Descriptors.VOID_CREATE_VIEW)
        assertMethod(profile, "ID3D12Device", "CreateDepthStencilView", D3D12Slots.DEVICE_CREATE_DSV, D3D12Descriptors.VOID_CREATE_VIEW)
        assertMethod(profile, "ID3D12DescriptorHeap", "GetCPUDescriptorHandleForHeapStart", D3D12Slots.HEAP_GET_CPU_START, D3D12Descriptors.CPU_HANDLE_RETURN)
        assertMethod(profile, "ID3D12GraphicsCommandList", "ClearRenderTargetView", D3D12Slots.LIST_CLEAR_RTV, D3D12Descriptors.VOID_CLEAR_RTV)
        assertMethod(profile, "ID3D12GraphicsCommandList", "ClearDepthStencilView", D3D12Slots.LIST_CLEAR_DSV, D3D12Descriptors.VOID_CLEAR_DSV)

        Arena.ofConfined().use { arena ->
            val handle = arena.allocate(8, 8)
            handle.set(ValueLayout.JAVA_LONG, 0, 0x0123_4567_89ab_cdefL)
            assertEquals(8L, handle.byteSize(), "D3D12_CPU_DESCRIPTOR_HANDLE is SIZE_T")
            assertEquals(0x0123_4567_89ab_cdefL, handle.get(ValueLayout.JAVA_LONG, 0))
        }
    }

    @Test
    public fun `DXR instance and resource contracts retain SDK packing and GPU write capability`() {
        val blas = 0x0123_4567_89ab_cdefL
        val instance = DxrLayoutContract.instanceDescription(blas, instanceMask = 0xa5)
        val bytes = ByteBuffer.wrap(instance).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(64, DxrLayoutContract.INSTANCE_DESCRIPTION_SIZE)
        assertEquals(64, instance.size)
        assertEquals(1, DxrLayoutContract.GLOBAL_ROOT_SIGNATURE_SUBOBJECT_TYPE)
        assertEquals(32, DxrLayoutContract.SHADER_RECORD_SIZE)
        assertEquals(0xa5 shl 24, bytes.getInt(48), "InstanceID:24 | InstanceMask:8")
        assertEquals(0, bytes.getInt(52), "Contribution:24 | Flags:8")
        assertEquals(blas, bytes.getLong(56), "AccelerationStructure GPU virtual address")
        assertEquals(0x4, D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS)

        val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12)
        assertEquals(64L, profile.types.layout("D3D12_RAYTRACING_INSTANCE_DESC").byteSize())
        assertEquals(16L, profile.types.layout("D3D12_STATE_SUBOBJECT").byteSize())
    }

    @Test
    public fun `raytraced frame copy uses the D3D12 placed footprint ABI`() {
        val profile = DirectXApiCatalog.profile(DirectXVersion.DIRECT3D_12)
        assertMethod(profile, "ID3D12GraphicsCommandList", "CopyTextureRegion", D3D12Slots.LIST_COPY_TEXTURE_REGION, D3D12Descriptors.VOID_COPY_TEXTURE_REGION)
        assertEquals(48L, profile.types.layout("D3D12_TEXTURE_COPY_LOCATION").byteSize())
        assertEquals(32L, profile.types.layout("D3D12_PLACED_SUBRESOURCE_FOOTPRINT").byteSize())
    }

    private fun assertMethod(
        profile: net.echonolix.caelum.dxgi.api.CompositeNativeApiCatalog,
        interfaceName: String,
        methodName: String,
        slot: Int,
        descriptor: FunctionDescriptor,
    ) {
        val method = profile.primary.requireInterface(interfaceName).requireMethod(methodName)
        assertEquals(slot, method.slot, "$interfaceName.$methodName vtable slot")
        assertEquals(descriptor, method.functionDescriptor(profile.types), "$interfaceName.$methodName FFM descriptor")
    }
}
