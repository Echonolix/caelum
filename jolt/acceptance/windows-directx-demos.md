# Windows Jolt / DirectX execution — 2026-10-27

Scope: Windows x64 native execution and three Caelum Direct3D 11 physics demos.
All graphics runs used hidden Win32 windows and GPU staging-texture readback.
No computer-use tool, desktop capture or simulated input was used.

## Environment

- Local date: 2026-10-27, America/Los_Angeles (2026-10-28 UTC).
- Base checkout: `813eb8128da3145bb5a9e4490aac64d10d1e26a0`, plus the demo changes accompanying this report.
- Windows x64, Java 24.0.1; repository-pinned Zig 0.17.0 native compiler.
- CPU: AMD Ryzen 9 9950X3D; reported physical RAM: 102,024,527,872 bytes.
- D3D11 driver type: hardware; requested/returned feature level: `0xb000` (11_0).
  Adapter selection was left to D3D11; this report does not identify a specific GPU.
- Jolt official v5.6.0 commit: `e77f175595e64cb44218cc9d9d56fc365ad0e36a`.
- Double positions, 16-bit object layers, baseline CPU, deterministic FP, exceptions enabled.
- `jolt/build/native/windows-x86_64/caelum_jolt.dll` SHA-256:
  `8fe859a847a49136a6c401277558914ae1e7a00b6c0f35c34fe535cdddf4f819`.

## Native checks

`:caelum-jolt:compileNativeWindowsX64` completed and the host library was bundled
by `processResources`. `:caelum-jolt:test -PjoltNativeTests` passed 16 tests:
7 contracts, 4 native ABI tests, 5 native world tests; zero failures/errors/skips.
The world tests exercised actual falling/settling, lifecycle/locks/batch,
ray hit/miss/layers, callback exception containment and deferred operations.
The separately tagged stress suite was not enabled.

`:caelum-directx:test` also passed all 26 tests with zero failures/errors/skips,
covering the catalog, ABI layouts, math/mesh helpers and native device-creation
smoke. The final invocation ran these alongside `joltDemosSmoke`.

## Demo results

All scenes ran 360 updates at `1f / 60f`, both with workers=0 and workers=2.
All three individual launcher tasks also passed with hidden windows.
Both configurations produced the following physics and image results:

| Scene | Observed result | Initial → final changed pixels |
| --- | --- | --- |
| Stack | 24 bodies including floor; 23 dynamic bodies descended; all asleep; final maximum speed 0; y range 0.286814–4.248733 | 35,769 |
| Bowling | 15 bodies including floor/rails/backstop; all 10 pins fell and moved; ball advanced 18.848428; maximum final speed 0.306624 | 14,188 |
| Kinematic / sensor / ray | Maximum platform error 2.71e-9 or less; rider moved 2.073823 and stayed at y=1.699992; one sensor entry and exit; deferred userData=1; probe x=7.000001, y=1.1; all 360 platform hits and all 360 misses correct | 17,131 |

The two-worker runs observed contact upcalls on two distinct threads other than
the simulation caller in each scene (`nativeCallbackThreads=2`). Contact counts
were 819 / 4604 / 446 for stack / bowling / kinematic. This witnesses real worker
upcalls for these scenes; it is not the unregister/replacement/close stress gate.

Each scene returned `JoltRuntime.liveNativeResources()` from 0 to 0 after closing
its world. D3D device-health checks passed. Raw PNGs show lit, depth-tested boxes,
spheres/capsules, a lane with pins, and a platform/sensor/ray scene. The same
geometry is transformed with the Jolt batch positions and quaternions.

## Reproduction and evidence

With `JAVA_HOME` pointing to JDK 24, from the repository root:

```powershell
.\gradlew.bat :caelum-jolt:compileNativeWindowsX64
.\gradlew.bat :caelum-jolt:test -PjoltNativeTests `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
.\gradlew.bat :caelum-directx:joltDemosSmoke `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
.\gradlew.bat :caelum-directx:test :caelum-directx:joltDemosSmoke `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
.\gradlew.bat :caelum-directx:joltDemosSmoke -PdemoWorkers=2 `
  '-PdemoOutput=C:/temp/caelum-jolt-workers2' `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
```

Working-directory evidence (ignored build outputs):

- `build/jolt-demos/native-build.log`, `native-tests.log`, `launchers.log`,
  `workers2.log`, `final-checks.log`.
- `jolt/build/test-results/test/TEST-*.xml`.
- `directx/build/jolt-demos/{stack,bowling,kinematic}/`: four raw PNGs,
  sampled transform CSV, summary JSON and lifecycle result for each scene.
- `directx/build/jolt-demos-workers2/{stack,bowling,kinematic}/`: corresponding
  artifacts from the two-worker run.

Run commands and expected assertions are detailed in
[JOLT-DEMOS.md](../../directx/JOLT-DEMOS.md). A fresh run writes `status=running`
first and only writes `passed` after assertions and cleanup; a nonzero process
exit or non-passed status is a failure, even if a prior PNG remains in the folder.

## Remaining acceptance

Linux/macOS linking and runtime, the full native stress suite, export/dependency
audits, portable publication, independent Maven consumption and performance
measurements remain pending. WARP fallback and visible-window interaction were
not exercised. These results do not certify portable release readiness or performance.
