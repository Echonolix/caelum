# DirectX and DXGI binding contract

This document is persistent design memory for the Windows bindings. It records
the rules that protect the JVM from native ABI mistakes and defines what this
repository means by complete DirectX coverage.

## Versioned source boundary

The generated raw API is a versioned snapshot. Normal builds compile the
committed schema and sources and never depend on whichever Windows SDK happens
to be installed on the developer machine.

* Direct3D 9, 9Ex, 9On12, 10, 10.1, 11.0-11.4, 11On12, D3DCompiler 47, and
  DXGI 1.0-1.6 are based on Windows SDK 10.0.22621.0.
* Current Direct3D 12 is based on Microsoft DirectX-Headers v1.619.5, commit
  `ee479f0bd5f7b884f202bcf0c3f076cc050dd256` (SDK version 619).
* The D3D12 snapshot includes the public runtime, video, debug, shader,
  compatibility, compiler, and shader-cache-registration headers. The D3DX12
  headers are helpers and are not counted as raw runtime ABI.
* DXCore, DirectML, DirectStorage, kernel/DDI headers, RPC scaffolding, and
  private exports are separate API families and are not silently included.

Every snapshot records source paths, hashes, preprocessing target/macros,
declarations, reviewed exclusions, interface inheritance, IID values, and full
COM vtable slots. Runtime support is probed through exported symbols and
`QueryInterface`; a missing newer interface does not remove it from the binding.

## Non-negotiable ABI rules

* Windows x64 pointers and function pointers use `ValueLayout.ADDRESS` with a
  `MemorySegment` carrier. `HRESULT`, `LONG`, `ULONG`, `UINT`, `DWORD`, and
  `BOOL` remain 32-bit. `WCHAR` remains 16-bit.
* A struct passed by value remains a `GroupLayout`; it is not flattened merely
  because its size matches a primitive carrier.
* COM slots, IIDs, enum values, and struct layouts come from the pinned schema.
  They must not be maintained as unexplained magic integers.
* `SUCCEEDED(hr)` means `hr >= 0`; `S_FALSE` is successful.
* A successful creation function or `QueryInterface` returns one owned COM
  reference. It is adopted without another `AddRef`. Borrowing never changes
  the reference count; an explicit copy performs exactly one `AddRef`; close
  performs at most one `Release`.
* Every out-pointer is freshly allocated and zeroed. Ownership is adopted only
  after both a successful `HRESULT` and a non-null result.
* Newer interfaces are capability-probed. DLL file version is not treated as
  proof that a particular IID or Agility interface is available.
* Every `MemorySegment` passed as a native pointer argument must be a native
  segment. Copy JVM arrays and strings into an `Arena` before a downcall. Heap
  segments may be bounded Java-side copy sources, but must not be widened with
  `reinterpret` or passed directly to native code.

## D3D12 descriptor safety regression

`D3D12_DESCRIPTOR_HEAP_TYPE_RTV` has the native value **2**. Value 0 means
`D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV`.

An earlier untracked prototype wrote 0 while labeling it RTV, queried the
increment size for type 0, and then passed that handle to
`CreateRenderTargetView`. Because that method returns `void`, the invalid
descriptor reached the graphics driver and could crash the JVM process.

Consequently:

* generated enum values are tested against a literal SDK oracle (`RTV == 2`),
  never against another project constant;
* descriptor heaps and CPU/GPU handles retain their descriptor kind in the
  convenience API;
* increment size is derived from the heap kind rather than accepted as an
  unrelated caller-supplied integer;
* debug validation reads the native `GetDesc().Type` raw integer.

Do not weaken these constraints while refactoring the renderer or generated
bindings.

## Rasterizer winding contract

The procedural teapot mesh stores outward-facing triangles counter-clockwise,
and its mesh regression checks that each triangle's geometric area vector
agrees with the averaged outward vertex normal. Direct3D 11's null/default
rasterizer state instead uses `FrontCounterClockwise = FALSE`, so it treats
those triangles as back faces and exposes the far side of the model after
back-face culling.

Direct3D renderers consuming this mesh must therefore bind an explicit
rasterizer state with `CullMode = BACK` and `FrontCounterClockwise = TRUE`.
Do not repair a backend mismatch by reversing the shared mesh indices: that
would break the geometry/normal contract and move the same bug to other APIs.

The original D3D12 teapot pipeline followed the same contract in its graphics
PSO. A default/null rasterizer policy is not acceptable for a mesh whose import
contract marks counter-clockwise triangles as front-facing, because the
Direct3D default treats clockwise triangles as front-facing.

The DirectX demo's Stanford Dragon asset has a different import contract. Keep
the checked-in official `dragon_vrip_res3.ply` byte-for-byte pinned by SHA-256;
do not silently replace it with a third-party OBJ. Its Stanford-provided crude
decimation contains duplicate/oppositely wound faces, unused vertices, boundary
edges, and non-manifold edges. The loader must deduplicate unordered triangle
keys, compact unused vertices, and propagate winding constraints only across
edges with exactly two incident faces. It must not impose impossible pairwise
orientation constraints on edges with three or more incident faces, and it must
not claim the cleaned display mesh is a closed manifold. Area-weighted shared
normals are a deliberate smooth-metal display policy after this cleanup, not a
topology-repair guarantee. Preserve the packaged attribution, non-commercial
restriction, and Chinese-cultural-symbol use reminder.

## D3D12 demo and DXR proof contract

The runnable DirectX Stanford Dragon sample is a genuine Direct3D 12 backend. Its default path
uses a high-performance DXGI adapter, an explicit D3D12 device/queue/list,
flip-model swap chain, typed RTV/DSV descriptor heaps, graphics PSO, resource
barriers, and fence completion. Fence waits have a bounded timeout and report
the device-removal HRESULT so a GPU fault cannot become an unexplained infinite
spin. Do not relabel a D3D11 renderer as D3D12.

Hardware ray tracing is an explicit opt-in (`-PdemoDxr=true`). The visible DXR
frame and its proof marker must come from the same completed GPU submission. A
successful claim requires all of the following:

* `D3D12_OPTIONS5` reports a nonzero ray-tracing tier and the device/list can be
  queried to `ID3D12Device5`/`ID3D12GraphicsCommandList4`;
* the cleaned indexed Stanford Dragon vertex/index buffers, not a disconnected test
  triangle, feed a BLAS and one-instance TLAS recorded with
  `BuildRaytracingAccelerationStructure`;
* a DXR state object and 32-byte-aligned shader records are used by
  a full-output `DispatchRays(width, height, 1)`; ray generation executes the
  configured 1–32 samples per pixel and closest-hit launches one actual mirror
  reflection `TraceRay` with maximum recursion depth two;
* closest-hit shades a low-roughness gold conductor against a high-contrast
  studio environment, and the aligned RGBA8 GPU buffer is copied into the
  swap-chain back buffer before `Present`; and
* a fence completes and the CPU reads the closest-hit marker `0x48595421` from
  the GPU output buffer.

Feature probing alone, printing a supported tier, or silently taking a raster
fallback must never be reported as a successful hardware-ray-tracing call.
The committed DXIL is paired with its HLSL source and regeneration command so
normal builds remain offline and reproducible. Header snapshot v1.619.5 is an
ABI/code-generation input; it is not evidence that an Agility runtime has been
deployed or activated.

The packaged shader resource is
`net/echonolix/caelum/directx/demo/d3d12/dxr/MinimalRayTracing.dxil`; its pinned
SHA-256 is
`C1C77EAEB770F7680C755E01BC87E3A37393EF929CDDD99B8B4A2BFEF1341276`.
Keep the launcher resource name, HLSL source, regeneration command, and hash in
sync. A missing or mismatched shader resource must fail the opt-in path rather
than silently disabling DXR.
