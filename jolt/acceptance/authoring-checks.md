# Authoring checks — 2026-10-24

Scope: source authoring and small checks only, as requested. This record does
not certify native execution or a portable release.

Environment: Windows x86_64, Java 24.0.1 (`24.0.1+9-30`), Kotlin 2.4.21,
Clang 20.1.4 (`x86_64-pc-windows-msvc`) for syntax-only checking. Native release
builds use the separate repository-pinned Zig toolchain and have not completed.

## Results

| Check | Result | Evidence / limit |
| --- | --- | --- |
| `ktgen`, `compileKotlin` | PASS | 59 raw exports and callback signatures generated; managed Kotlin compiles |
| `compileTestKotlin` | PASS | Includes the native ABI, lifecycle, physics, worker and stress test sources; those native tests were not run |
| `compileExamplesKotlin` | PASS | HelloWorld, benchmark and standalone consumer source compile locally; Maven resolution is untested |
| `JoltContractsTest` | PASS, 7 tests, 0 failures/errors | JUnit timestamp `2026-10-25T05:04:58.380Z`; no Jolt library loaded |
| `verifyJoltSources` | PASS | 130 translation units exactly match pinned Jolt.cmake's base source block |
| Two bridge C++ translation units | PASS | `clang++ -fsyntax-only`; no objects linked and no Jolt simulation executed |
| `sourcesJar` and source-content audit | PASS | Apache/MIT notices, header, both bridge files, manifest, build script, provenance, surface and generated/managed Kotlin present; bridge/header bytes match checkout |
| Artifact checker / consumer POM syntax | PASS | Python parsed/loaded; POM XML parsed. Portable binary checker and Maven execution were not run |
| Whitespace | PASS | `git diff --check` plus authored-file scan |

The seven contract tests cover header/export/field accounting; generated array
stride and nested aliasing; copied callback snapshots and guarded settings;
borrow scope expiration/thread checks; layer-table copying and symmetry;
callback/body-lock reentry rejection; ID/update-bit preservation.

Source-jar SHA-256 at this check:

```text
1e3724ebda1c7c14ed9bba93a4caeaecd6cac2898098f933ac7501c5b269e1ea
```

The source-only main jar created as a compilation dependency contains no linked
Jolt library. It is not a native runtime deliverable and must not be published.
An earlier test-compilation invocation requested processResources and began
fetching the native toolchain; it was stopped, and subsequent source-only checks
explicitly excluded that task. No native output library was produced.

## Commands used

From the repository root, with JAVA_HOME pointing to JDK 24:

```powershell
.\gradlew.bat :caelum-jolt:compileTestKotlin :caelum-jolt:compileExamplesKotlin `
  :caelum-jolt:test :caelum-jolt:verifyJoltSources :caelum-jolt:sourcesJar `
  -x :caelum-jolt:processResources `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64 --console=plain
```

The source-only compiler and sourcesJar were run again after the final
update/deferred-queue handling change. Consumer source was added to the examples
compilation for syntax/type checking; its own Maven POM still depends only on
published artifacts.

```powershell
$joltInclude = (Resolve-Path 'jolt/build/archives/joltSources/JoltPhysics-e77f175595e64cb44218cc9d9d56fc365ad0e36a').Path
& 'C:\Program Files\LLVM\bin\clang++.exe' -std=c++17 -fsyntax-only `
  -fexceptions -fno-rtti -ffp-contract=off -DNDEBUG=1 `
  -DJPH_DOUBLE_PRECISION=1 -DJPH_OBJECT_LAYER_BITS=16 -DJPH_CROSS_PLATFORM_DETERMINISTIC=1 `
  -Ijolt/include "-I$joltInclude" `
  jolt/src/main/cpp/caelum_jolt.cpp jolt/src/main/cpp/caelum_jolt_abi.cpp
```

Working-directory logs (ignored build outputs):
`build/jolt-research/final-checks.log`, `final-compile.log`,
`consumer-source-compile.log`, `native-syntax.log`; JUnit XML is under
`jolt/build/test-results/test/`. These can be regenerated using the commands.

Pending: linked native builds for all targets; all native ABI/default/probe
execution; single/thread-pool physics; exception/unregister/close stress;
binary export/dependency/package checks; independent Maven resolution/execution;
performance measurements. Their commands and required evidence are in
[README.md](README.md). No performance or cross-platform compatibility result is
inferred from the checks above.
