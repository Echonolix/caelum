# DX12 / DXR Jolt museum execution — 2026-10-28

Scope: the dynamic hardware-raytraced museum demo and its model convex collision
integration. This records actual Windows execution; no computer-use tools or
desktop input automation were used. PNGs come directly from GPU readback.

Source: base `be4322390d48624ee5bdcad42082ab4ca171a759`, convex-hull milestone
`48003a6`, plus the accompanying DirectX demo changes. Engine remains official
Jolt v5.6.0 `e77f175595e64cb44218cc9d9d56fc365ad0e36a`, double positions,
16-bit layers, deterministic FP, baseline CPU and exceptions enabled.
Windows x64, JDK 24.0.1, repository-pinned Zig. Shader compiled by dxc
`1.9 - 1.8.0.4907 (06381f2d7)` from Vulkan SDK 1.4.313.0, profile `lib_6_3`.
DXIL SHA-256: `bf2ad2f65318948d3a867e7ba729d6cb77e83367517ee52adeafad708cdbc4ac`.

## Executed results

- The 720-step physics-only gate passed for the actual contact-driven machine.
- Hidden full DX12/DXR runs passed with workers=2 and workers=0, 2 samples/pixel.
- A visible-window bounded run passed with workers=2, 4 samples/pixel. It rendered
  all 721 frames (initial plus 720 steps), then closed itself. Keyboard pause,
  reset and Escape were not driven by automation.
- Hardware DXR reported tier value 12; no fallback path ran. Each run built 18
  mesh BLAS and 721 TLAS, performed 721 DispatchRays calls, and copied each GPU
  output image into the swap chain. TLAS refit/update count is 0: these are rebuilds.
- Scene/world/renderer shutdown completed; Jolt bridge owned-resource count
  returned from 0 to 0. Device-health checks passed. This counter does not measure
  driver allocations or every Jolt-internal reference.

Physics results matched in workers=0/2 runs:

| Measurement | Result |
| --- | --- |
| Bodies / dynamic / rendered instances | 170 / 155 / 165 |
| Contact callbacks | 227,894 |
| Actual non-caller callback threads | 2 with workers=2; 0 with workers=0 |
| Trigger step / sensor entries | 365 / 1 |
| Fallen dominoes / displaced bricks | 32 / 20 |
| Models | 4 teapots + 4 Stanford dragons; all 8 descended and contacted |
| Event-fired ball travel / user data | 11.914978 / 51,966 (`0xcafe`) |
| Maximum kinematic position error | 1.49011612e-8 |
| Physics ray hits / misses | 720 / 720 |

Final-frame GPU results:

| Measurement | Hidden, 2 samples | Visible, 4 samples |
| --- | --- | --- |
| Primary rays/frame | 1,228,800 | 2,457,600 |
| Teapot primary hit samples | 5,086 | 10,158 |
| Dragon primary hit samples | 7,309 | 14,595 |
| Actual shadow occlusions | 126,041 | 252,001 |
| Actual reflection hits | 107,561 | 215,159 |
| Significant changed pixels from initial | 52,872 | 53,084 |

All six checkpoint images showed both model categories; all checkpoint shadow
occlusion/reflection-hit checks passed. Counters are cleared every dispatch and
read only after the submission fence. Instance evidence uses the TLAS list
index, so invisible sensor IDs do not corrupt model categorization.

The independent encoding fixtures compare a known 90-degree Y rotation plus
translation to literal SDK bytes, test two distinct atlas offsets/materials and
object-ID gaps, and reject malformed geometry/affine transforms. Deliberately
corrupted translations, masks, BLAS addresses and atlas offsets were rejected.
The SDK metadata gate checks 34 record sizes, 125 non-bit fields and four instance
bitfields; deliberately corrupted offset/bit-width declarations were rejected.
This compares to the pinned SDK header catalog, not a new native compiler oracle.

The final regression invocation executed all 19 Jolt contract/native tests and
26 DirectX tests, with zero failures/errors/skips. Native/generation outputs were
unchanged and reused; the modified Win32 demo helper was recompiled for the
DirectX tests. This does not exercise the separately tagged Jolt stress suite.

## Reproduction

From repository root with JDK 24:

```powershell
.\gradlew.bat :caelum-directx:joltDxrContracts :caelum-directx:joltDxrMuseumSmoke `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
.\gradlew.bat :caelum-directx:joltDxrMuseumSmoke -PdemoWorkers=0 `
  '-PdemoOutput=C:/temp/caelum-museum-workers0' `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
.\gradlew.bat :caelum-directx:joltDxrMuseumDemo -PdemoFrames=720 -PdemoDxrSamples=4 `
  '-PdemoOutput=C:/temp/caelum-museum-visible' `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
```

Ignored working-tree evidence:

- `build/jolt-dxr/physics.log`, `gpu-smoke.log`, `contracts-workers0.log`, `visible.log`, `regressions.log`.
- `directx/build/jolt-dxr-museum/`, `jolt-dxr-museum-workers0/`, `jolt-dxr-museum-visible/`:
  six raw PNGs, GPU checkpoint JSON, transform CSV, summary and lifecycle files.
- `jolt/build/test-results/test/` and `directx/build/test-results/test/` for native/binding regressions.

The initial native-hull build and 19-test run are recorded separately in
[convex-hull.md](convex-hull.md). Later demo builds reused unchanged native outputs;
Kotlin/shader resources were rebuilt when their inputs changed. The checked-in
DXIL was compiled explicitly; ordinary Gradle builds copy it without invoking dxc.

The Stanford display asset is the existing licensed reconstruction; the teapot
is the repository's procedural mesh. Collision uses convex proxies (up to 256
hull vertices, filled concavities), while DXR uses the full display meshes.
Portable release, non-Windows runtime, formal performance measurements, GPU memory
profiling and automated keyboard interaction remain outside this evidence.
