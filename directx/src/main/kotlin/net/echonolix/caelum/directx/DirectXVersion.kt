package net.echonolix.caelum.directx

/** Direct3D API generations represented by the Caelum DirectX binding. */
public enum class DirectXVersion(
    public val major: Int,
    public val libraryName: String,
    public val latestInterfaceTarget: DirectXInterfaceTarget,
) {
    DIRECT3D_9(9, "d3d9.dll", DirectXInterfaceTarget.DIRECT3D9_EX),
    DIRECT3D_10(10, "d3d10.dll", DirectXInterfaceTarget.DIRECT3D10_1),
    DIRECT3D_11(11, "d3d11.dll", DirectXInterfaceTarget.DIRECT3D11_DEVICE5),
    DIRECT3D_12(12, "d3d12.dll", DirectXInterfaceTarget.DIRECT3D12_DEVICE15),
}

/**
 * Highest COM interface revision described by this binding snapshot.
 *
 * This is binding metadata, not proof that a particular OS, driver, or device
 * exposes the interface. Applications must still create a device and use
 * `QueryInterface` before calling a revision-specific method.
 */
public enum class DirectXInterfaceTarget(public val nativeName: String) {
    DIRECT3D9("IDirect3D9"),
    DIRECT3D9_EX("IDirect3D9Ex"),
    DIRECT3D10("ID3D10Device"),
    DIRECT3D10_1("ID3D10Device1"),
    DIRECT3D11_DEVICE5("ID3D11Device5"),
    DIRECT3D12_DEVICE15("ID3D12Device15"),
    DIRECT3D12_GRAPHICS_COMMAND_LIST10("ID3D12GraphicsCommandList10"),
}

/** Latest interfaces represented by the pinned DirectX header snapshot. */
public data class DirectXBindingTargets(
    public val direct3D9: DirectXInterfaceTarget = DirectXInterfaceTarget.DIRECT3D9_EX,
    public val direct3D10: DirectXInterfaceTarget = DirectXInterfaceTarget.DIRECT3D10_1,
    public val direct3D11: DirectXInterfaceTarget = DirectXInterfaceTarget.DIRECT3D11_DEVICE5,
    public val direct3D12Device: DirectXInterfaceTarget = DirectXInterfaceTarget.DIRECT3D12_DEVICE15,
    public val direct3D12CommandList: DirectXInterfaceTarget =
        DirectXInterfaceTarget.DIRECT3D12_GRAPHICS_COMMAND_LIST10,
)
