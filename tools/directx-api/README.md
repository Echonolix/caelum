# DirectX raw API schema extractor

This maintainer-only tool regenerates the versioned raw ABI catalogs shipped by
`caelum-directx` and `caelum-dxgi`. It is deliberately not part of the ordinary
Gradle build: consumers do not need a Windows SDK, Clang, or jextract.

Pinned inputs:

- Microsoft Windows SDK `10.0.22621.0` for D3D9/9Ex/9On12, D3D10/10.1,
  D3D11.0–11.4/11On12, DXGI 1.0–1.6 + debug, `d3dcommon`, and `d3dcompiler`.
- Microsoft DirectX-Headers `1.619.5`, commit
  `ee479f0bd5f7b884f202bcf0c3f076cc050dd256`, for the latest D3D12 core ABI.
- Target `x86_64-pc-windows-msvc` (Windows x64 LLP64 ABI).

Selected public ABI headers (the schema records the individual hashes):

- D3D9: `d3d9.h`, `d3d9types.h`, `d3d9caps.h`, `d3d9on12.h`.
- D3D10: `d3d10_1.h` (intentionally before `d3d10.h`), `d3d10.h`,
  `d3d10misc.h`, `d3d10sdklayers.h`, `d3d10shader.h`,
  `d3d10_1shader.h`, `d3d10effect.h`.
- D3D11: `d3d11.h`, `d3d11_1.h` through `d3d11_4.h`,
  `d3d11on12.h`, `d3d11sdklayers.h`, `d3d11shader.h`,
  `d3d11shadertracing.h`.
- Common/compiler: `d3dcommon.h`, `d3dcompiler.h`.
- D3D12: `d3d12.h`, compatibility, compiler, SDK layers, shader, video,
  events, common, and shader-cache registration headers from the pinned
  DirectX-Headers release.
- DXGI: `dxgi.h`, `dxgi1_2.h` through `dxgi1_6.h`, common, format, type,
  debug and messages headers.

Generate all schemas from a clean checkout:

```powershell
python tools/directx-api/extract_directx_api.py schema `
  --directx-headers C:\src\DirectX-Headers-1.619.5
```

The command records every selected header SHA-256 and compiler/target settings
inside each JSON file. It emits complete flattened C COM vtables: method slot 0
includes `This`, and inherited methods are present exactly as compiled under
`CINTERFACE`.

Validate committed indexes, hashes, vtable coverage, contiguous slots, and all
record/anonymous-record layouts:

```powershell
python tools/directx-api/validate_directx_api.py
```

To regenerate filtered jextract Java sources in a maintainer-selected directory:

```powershell
python tools/directx-api/extract_directx_api.py jextract `
  --profile d3d11 `
  --directx-headers C:\src\DirectX-Headers-1.619.5 `
  --jextract-root C:\tools\jextract-25 `
  --output-root C:\tmp\caelum-directx-raw `
  --target-package net.echonolix.caelum.directx.raw.d3d11 `
  --header-class-name D3D11Raw
```

The jextract mode starts from `--dump-includes` entries owned by the selected
headers. If jextract reports that a selected declaration depends on an excluded
symbol, it adds that symbol from the complete dump and retries until the
dependency closure succeeds. It writes a closure manifest beside the output.
On Windows the tool routes the filtered symbol list through the bundled Java
launcher's response-file support, avoiding the approximately 32 KiB process
command-line limit.

## Deliberate boundaries

- Function-like macros and C++ helper/inline APIs are not ABI declarations.
- `declarations.constants` catalogs selected public object-like macros; it is
  not a claim that every C `VarDecl` is a runtime constant. In particular, the
  one provider GUID and 146 `EVENT_DESCRIPTOR` definitions in `D3D12Events.h`
  are header-local `EXTERN_C __declspec(selectany) const` weak COMDAT data, not
  exports of `d3d12.dll` or `D3D12Core.dll`. Consumers that need those ETW
  descriptors must define or materialize them from the pinned header values.
- Transitive Win32/OLE declarations are referenced by their C signature but are
  not re-cataloged as DirectX declarations.
- OpenJDK jextract skips C bitfields. The JSON schema retains bitfield widths and
  marks the typed-Java gap; do not claim a bitfield-containing generated record
  is complete until a verified carrier-word accessor exists.
- D3D12 Agility interfaces are version pinned. Never substitute same-named
  interfaces from a different `D3D12_SDK_VERSION`; their IID/signature may differ.
