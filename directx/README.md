# caelum-directx

`caelum-directx` provides Windows x64 Direct3D bindings on the JDK 24 Foreign
Function and Memory API. It depends on `caelum-dxgi` for DXGI, the shared COM
ownership model, GUID/HRESULT handling, native DLL loading, and the schema-driven
FFM type system.

The module has two deliberately separate layers:

- a version-pinned raw API catalog containing SDK declarations, IIDs, complete
  COM vtables, exact record layouts, constants, and exported functions; and
- a smaller convenience layer for common runtime probing and device-creation
  entry points.

The convenience layer is not presented as the complete SDK surface. Use
`DirectXApiCatalog` when a declaration is outside that smaller set.

## Version coverage

- Direct3D 9, 9Ex, and 9On12 are pinned to Windows SDK 10.0.22621.0. The
  interface inventory includes `IDirect3D9Ex`, and the entry points include
  `Direct3DCreate9` and `Direct3DCreate9Ex`.
- Direct3D 10 and 10.1 are pinned to Windows SDK 10.0.22621.0. D3D10.0 entry
  points are resolved from `d3d10.dll`; D3D10.1 entry points are resolved from
  `d3d10_1.dll`.
- Direct3D 11, revisions through `ID3D11Device5`, and 11On12 are pinned to
  Windows SDK 10.0.22621.0. A newer COM revision must be obtained with
  `QueryInterface`; the presence of `d3d11.dll` alone is not proof that it is
  supported by a particular device.
- Direct3D 12 is pinned to Microsoft DirectX-Headers v1.619.5, commit
  `ee479f0bd5f7b884f202bcf0c3f076cc050dd256`, SDK ABI 619. The catalog reaches
  `ID3D12Device15` and `ID3D12GraphicsCommandList10` and includes the public
  runtime, video, debug-layer, shader, compatibility, compiler, and shader-cache
  registration headers in that release.
- Feature levels are represented explicitly from `9_1` through `12_2`.
- The shared legacy compiler surface contains all 25 public functions exported
  by the pinned `d3dcompiler.h` profile and is loaded from
  `d3dcompiler_47.dll` when available.

DXGI is versioned independently in `caelum-dxgi` and currently covers DXGI
1.0-1.6, through `IDXGIFactory7`, from Windows SDK 10.0.22621.0. D3DX12 helper
headers, DirectML, DirectStorage, DXCore, driver/DDI declarations, and private
exports are different API families and are not silently counted as Direct3D
runtime coverage.

## Runtime probing

Runtime probing is lazy: it neither creates a GPU device nor loads every
DirectX DLL during class initialization.

```kotlin
val runtime = DirectX.probeRuntime()
val capabilities = runtime.capabilities()

if (capabilities.forVersion(DirectXVersion.DIRECT3D_11).available) {
    val createDevice = runtime.downcall(D3D11EntryPoints.createDevice)
    // Invoke the handle with the carriers described by the entry point.
}
```

`available` means that the DLL and the base creation symbol are present, so a
device-creation attempt can be made. It does not claim that the current adapter
supports a particular feature level, that the returned object implements the
highest cataloged COM revision, or that an Agility SDK component has been
deployed correctly. Confirm those properties with an actual creation call,
HRESULT checks, feature queries, and `QueryInterface`.

The checked-in runtime implementation targets Windows x64 only. It rejects
32-bit stdcall and Windows ARM64 rather than implying that those unverified ABIs
are supported.

## ABI safety contract

- Native pointers and function pointers use `ValueLayout.ADDRESS`.
  `HRESULT`, `UINT`, and Windows `long` remain 32-bit; Windows x64 `SIZE_T`
  remains 64-bit.
- SDK enums use explicit native values, never Kotlin enum ordinals.
- Structs passed by value retain their audited `GroupLayout`.
- COM IIDs, inherited vtable slots, callbacks, and record layouts come from the
  pinned catalog. Unknown types fail explicitly instead of using a guessed
  fallback layout.
- Successful creation and `QueryInterface` results are adopted as exactly one
  owned COM reference. Out-pointer cells are zeroed before every native call.
- `D3D12_DESCRIPTOR_HEAP_TYPE_RTV` is the literal SDK value **2**. An older
  untracked prototype used 0 (CBV/SRV/UAV), which could feed an invalid handle
  to `CreateRenderTargetView` and crash inside a graphics driver.

The full persistent ABI and ownership rationale is in
[`docs/directx-bindings.md`](../docs/directx-bindings.md) in the source repository.

## Stanford Dragon demo

The runnable sample now uses a real Win32 window and a native Direct3D 12
device, command queue, command list, flip-model swap chain, RTV/DSV heaps,
graphics pipeline state, resource barriers, and fence synchronization. It
loads the checked-in Stanford `dragon_vrip_res3` reconstruction, removes duplicate
faces and unused vertices, reconstructs consistent winding and smooth normals,
uploads the cleaned indexed dragon, and renders it with explicit depth
testing and `CullMode = BACK`, `FrontCounterClockwise = TRUE`, matching the
loader's consistent display-winding contract on two-face manifold adjacencies.

The launcher is intentionally a D3D12 renderer. It rejects any explicit
`-PdirectxVersion` value other than `12`; it never labels another backend as
D3D12. Binding coverage for older generations remains verified separately by
the versioned catalogs and the cross-version device-creation smoke test.

The packaged `stanford_dragon_res3.ply` is the Stanford Graphics Laboratory's
interactive decimation from `dragon_recon.tar.gz`, not a third-party substitute.
The original member SHA-256 is
`F32B87762894BDE78CD45DC05AA9FDE0F5AD390C944168A96E97191FF1FC6D45`.
Its raw 22,998 vertices / 47,794 face records contain duplicate or oppositely
wound faces and 16 unreferenced vertices, consistent with the archive README's
warning that this crude decimation may not preserve topology. The loader keeps
the asset bytes unchanged while deterministically deduplicating faces,
compacting vertices, orienting provable two-face manifold adjacencies, and
rebuilding area-weighted display normals. The resulting GPU mesh contains
22,982 vertices and 46,540 unique triangles. Remaining non-manifold edges are an
upstream property of this interactive scan; the loader does not mislabel it as a
closed manifold.

Stanford permits research use and free redistribution with attribution, but
prohibits commercial use or inclusion in a product for sale without permission.
Credit the Stanford University Computer Graphics Laboratory. Their repository
also asks that the dragon, a symbol of Chinese culture, be used in good taste.
See the packaged `STANFORD_DRAGON_NOTICE.txt` for the exact source URL, archive
hash, member hash, limitations, and cultural-use reminder.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :caelum-directx:directxDragonDemo
```

For a bounded hidden smoke run:

```powershell
.\gradlew.bat :caelum-directx:directxDragonDemo `
    '-PdemoHidden=true' `
    '-PdemoSeconds=1'
```

Hardware ray tracing is opt-in. `-PdemoDxr=true` queries `D3D12_OPTIONS5`,
obtains `ID3D12Device5` and `ID3D12GraphicsCommandList4`, builds an indexed BLAS
from the actual dragon vertex/index buffers and a one-instance TLAS, and creates
a ray-tracing state object and aligned shader table. Ray generation covers the
960 x 720 output at eight samples per pixel by default. A closest hit evaluates
a low-roughness gold Fresnel material and launches a second `TraceRay` into a
high-contrast studio environment; pipeline recursion depth is two. The complete
GPU-produced RGBA8 frame is copied directly into the swap chain, so DXR mode is
not a raster model with an unrelated probe running beside it. A small read-back
marker still verifies that the hardware closest-hit shader executed. An adapter
without DXR support fails explicitly; there is no software or no-op fallback.

```powershell
.\gradlew.bat :caelum-directx:directxDragonDemo `
    '-PdemoDxr=true' `
    '-PdemoDxrSamples=8' `
    '-PdemoHidden=true' `
    '-PdemoSeconds=1'
```

`demoDxrSamples` accepts 1–32 samples per pixel. Higher values reduce
edge/reflection noise and increase ray work; they also increase startup render
time because this demo produces the ray-traced frame before presenting it.

The committed `MinimalRayTracing.dxil` keeps ordinary builds independent of a
locally installed shader compiler. Its matching HLSL source and reproducible
`dxc -T lib_6_3` command are stored beside it. This demo uses the installed
Windows D3D12 runtime; the v1.619.5 header catalog is an ABI snapshot and does
not, by itself, claim that an Agility SDK runtime has been deployed.

A successful raster run reports the backend, mesh counts, DXR switch state,
and rendered frame count. A successful DXR run additionally reports the
ray-tracing tier, two acceleration-structure build calls, output samples per
pixel, exact primary-ray count, maximum recursion depth, one full-frame dispatch,
and the GPU read-back marker `0x48595421`. Both finish with:

```text
CAELUM_DIRECTX12_DEMO_OK
CAELUM_STANFORD_DRAGON_DEMO_OK
```
