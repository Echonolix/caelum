package net.echonolix.caelum.directx

import java.lang.foreign.FunctionDescriptor

/** Native libraries used by the DirectX runtime entry-point layer. */
public enum class DirectXLibrary(public val fileName: String) {
    D3D9("d3d9.dll"),
    D3D10("d3d10.dll"),
    D3D10_1("d3d10_1.dll"),
    D3D11("d3d11.dll"),
    D3D12("d3d12.dll"),
    /** Optional compiler component shipped with the D3D12 Agility SDK. */
    D3D12_STATE_OBJECT_COMPILER("D3D12StateObjectCompiler.dll"),
    D3D_COMPILER_47("d3dcompiler_47.dll"),
}

/** ABI descriptor for an exported DirectX function. */
public data class DirectXEntryPoint(
    public val library: DirectXLibrary,
    public val name: String,
    public val descriptor: FunctionDescriptor,
) {
    init {
        require(name.isNotBlank()) { "Entry-point name must not be blank" }
    }
}
