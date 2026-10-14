# SDL3 integration candidate acceptance

## Source and scope

Fetched all origin heads and tags before constructing the candidate:

- Main: `5883abfa3d93a4658f3d0b4c0a6b7c45ba461b71`.
- SDL3 source: `sdl3`, `1fe22b6f097b4c2d211b5765a2a7d4435e60e301` (`Add SDL3 bindings`).
- Merge base: `fa759265406659eaddc06934897e34facdd3f935`.
- The SDL3-only range contains that one commit. Its 127-file change adds the
  header/parser/generator/runtime/test surface and changes core callback stub
  allocation. The candidate starts at fetched main and merges SDL3 with both
  histories intact. README and settings conflicts retain all newer main modules.

No Caelum checkout `AGENTS.md` or `.agents/skills` exists at these refs; the
workspace `.agents` directory is empty. Reviewed the current `.editorconfig`,
`BINDING-AUTHORING.md`, Gradle conventions, and the Fornax checkout `AGENTS.md`
and render-backend/tech-stack documentation. No prohibited reference code was read.

## Candidate changes

- Add SDL3 to the BOM and publication descriptions; include vendored headers in
  the source jar and provide explicit JUnit Platform launchers.
- Remove the unused legacy type/constant emitters. Bound callback typedef scans
  to one declaration: the old DOTALL expression scanned across unrelated
  declarations and took minutes during the generator suite. The corrected
  clean generation/test/publication gate completed in approximately 7 seconds
  with already provisioned build dependencies.
- Independently scan public header declarations and concrete record names in
  tests. Layout regeneration now discovers names from headers rather than its
  previous snapshot. These checks close inventory omissions; they do not measure
  native field offsets, alignment, or function carriers on Windows.
- Restore `SDL_revision.h` from the recorded official release archive. It was
  the only vendored header that differed from that archive. All 86 SDL headers
  and `LICENSE.SDL.txt` now match it byte for byte.
- Add core manager/descriptor/object-identity/lifetime isolation tests and a
  void SDL callback roundtrip. The public core API signatures remain unchanged.

## CPU and publication validation

Environment: Linux x86_64, Oracle GraalVM `25.0.4+7.1`, Kotlin `2.4.21`, official
Gradle `9.5.0` (distribution checksum verified). The cloud has no JDK 8; a
**temporary, non-repository** init script selects JDK 25 for `ktgen` and
`buildSrc`, whose default toolchains request JDK 8:

```groovy
gradle.beforeProject { project ->
    project.afterEvaluate {
        if (project.name == 'buildSrc' || project.rootProject.name == 'ktgen') {
            project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension)
                ?.toolchain?.languageVersion?.set(JavaLanguageVersion.of(25))
        }
    }
}
```

The invocation uses the cloud's existing HTTPS proxy and existing system Java
truststore. TLS verification remains enabled; no system packages or repository
network settings were changed. The first successful clean SDL gate ran:

```text
gradle -I <temporary-init-script> -PcaelumJavaVersion=25 --no-configuration-cache --max-workers=2
  :caelum-sdl3:clean :caelum-sdl3:codegen:clean
  :caelum-sdl3:codegen:test :caelum-sdl3:test :caelum-core:test
  :caelum-sdl3:jar :caelum-sdl3:sourcesJar
  :caelum-sdl3:generatePomFileForCaelum-sdl3Publication
  :caelum-bom:generatePomFileForCaelum-bomPublication
```

After adding the void callback test, the test/publication targets passed again.
Current totals after the union ABI correction: **12 core + 23 SDL generator + 34 SDL runtime = 69 tests**, no failures,
errors, or skips. Clean `ktgen` executed; unchanged compilation/test outputs may
be reused by Gradle's build cache. `cpu-results.json` records per-suite results.

Binary/source jars contain `LICENSE`, `LICENSE.SDL.txt`, `README.md`, and
`UPSTREAM.md`. The source jar includes 86 SDL headers plus `caelum_sdl3.h`.
SDL POM exposes the core dependency and SDL description; the BOM includes SDL3.
No native DLL is packaged or remotely published by this candidate.

The existing Vulkan generator also passed `:caelum-vulkan:ktgen`. Compile
`VulkanCallbackProbe.kt` together with production
`vulkan/src/baseSrc/kotlin/net/echonolix/caelum/vulkan/functions/VkFunction.kt`
and generated `VkFuncPtrVoidFunction.kt`, using the candidate core jar:

```text
K2JVMCompiler -jvm-target 25 -jvm-default no-compatibility
  -classpath core/build/libs/caelum-core-1.0-SNAPSHOT.jar -include-runtime
  -d <probe.jar> <probe.kt> <VkFunction.kt> <generated-VkFuncPtrVoidFunction.kt>
java --enable-native-access=ALL-UNNAMED
  -cp <probe.jar>:core/build/libs/caelum-core-1.0-SNAPSHOT.jar VulkanCallbackProbeKt
```

Result: `VULKAN_CALLBACK_CPU_OK`. This exercises stable identity, native
upcall/downcall, lifetime closure and recreation through an existing binding's
actual manager/generated callback. It does not load Vulkan or create a GPU device.

## Official native surface accounting

Source archive URL and SHA-256 remain in `../UPSTREAM.md`.
Official runtime archive:
`https://github.com/libsdl-org/SDL/releases/download/release-3.4.14/SDL3-3.4.14-win32-x64.zip`

- SHA-256: `69a4e55645651af85e6ccfe40981b5a0bc2c594d0004fe7844db680e23cfbdaf`.
- Archive `.git-hash`: `147a8ee32dbf9ac02f3794964490687b6bbda1bc`.
- `objdump -p SDL3.dll` reports 1,270 `SDL_` exports; normalized names are in
  `windows-3.4.14-exports.txt`.
- Independent public-header inventory: 1,271 names = 1,241 generated + 30 skipped.
  Every generated name exists in the official Windows DLL.
- 28 skipped names are exported but intentionally unsupported. The two
  unexported header names are SDL thread creation macros. The sole `SDL_` export
  outside the header inventory is internal `SDL_DYNAPI_entry`.

Export presence does not validate FFM carriers or native execution.

## ABI, lifecycle, downstream boundaries

The loader rejects Linux and non-x64 architectures. ABI stays Windows x64:
32-bit C `long`, 16-bit `wchar_t`, 64-bit pointer/`size_t`, fixed GUID/FColor
aggregate layouts. The checked-in snapshot contains 122 named records and 839
fields, with anonymous union storage and platform-owned/untyped fields explicitly
inventoried; SDL_TLSID has a separate generated atomic layout. Varargs, `va_list`,
platform-owned types and unsupported aggregate signatures stay skipped.

The manager cache retains each callback object until reset (or the global
manager's process lifetime). Native consumers must unregister callbacks and
quiesce all in-flight invocations before `freeFunctionStubs()`. Reset invalidates
all stubs for that manager and leaves other managers intact; it does not stop SDL
or unregister callbacks. Loaded libraries and lazy downcall handles stay loaded
for the classloader lifetime. Do not let exceptions escape an upcall.

Cygnus standalone window creation remains GLFW-based. Fornax embedded integration
uses borrowed Vulkan handles and synchronization. Adding this optional module
requires no window/backend migration or official pin change. Typed SDL Vulkan
surface APIs remain explicitly skipped because external Vulkan types are not
represented by this generator. This candidate is not an SDL window backend for
Cygnus/Fornax. Full Cygnus/Fornax compilation, mod-classloader runtime compatibility,
GLFW generated callback execution and end-to-end rendering were not validated by
these focused CPU gates.

## Union ABI correction after Windows review

Windows review of candidate `fefb5b69f82f0208b7d45aa15c77a0d8f81d194a`
reported `SDL_HapticEffect` as C/Clang size 72, alignment 8, while its actual
FFM layout was size 68, alignment 8. The shared `NUnion.Impl` delegated directly
to JDK `MemoryLayout.unionLayout`, which uses the largest member size without
rounding it to the union alignment. The largest haptic field is 68 bytes with
lower alignment than another member.

The shared core now rounds only when required, adding an anonymous padding
member covering the **full rounded size**. Union members overlap, so adding a
four-byte padding member would not change the maximum member size. The result
remains a `UnionLayout`; original members, names, layouts and zero offsets are
preserved. Already-aligned unions are returned unchanged. No SDL header or ABI
snapshot was altered to mask the mismatch, and public API signatures are unchanged.

Validation on the same GraalVM 25 cloud toolchain, **offline** (no SDL DLL or
Gradle downloads):

- Before the fix, the focused core suite reproduced two failures: tail padding
  and nested layout. The already-aligned union case passed.
- Four core regressions cover a 68-byte low-alignment member plus an 8-byte
  aligned member, existing aligned unions, nested unions/struct offsets,
  sequence stride and actual `calloc` array storage. A two-element padded union
  allocation reserves 144 bytes aligned to 8; its final byte is accessible.
- Runtime reflection reads the **actual generated FFM layouts** for all 123 SDL
  records (122 public records plus TLSID), checking group kind, size, alignment,
  and all 840 field sizes/offsets against the checked-in snapshot.
  `kotlin-ffm-layout-audit.log` records actual per-record values. Haptic effect
  is now size 72/alignment 8, with two-element sequence size 144 and stride 72.
  This is distinct from independent native Windows ABI verification.
- Clean SDL generator output, SDL runtime suites and core regressions passed:
  **12 + 23 + 34 = 69 tests**, zero failures/errors/skips. The command retained
  the temporary JDK 25 init script and added `--offline` to the documented
  clean generation/test/jar gate. The final runtime audit gate also passed.
- Existing Vulkan generation was rerun. The documented Vulkan callback probe
  was recompiled against the repaired core jar and returned
  `VULKAN_CALLBACK_CPU_OK` again.
- The installed Linux C compiler verified both matching synthetic nested
  layouts and the actual vendored SDL haptic union, without linking SDL:

  ```text
  cc -std=c11 -Wall -Wextra -I sdl3/include sdl3/acceptance/UnionLayoutProbe.c -o <probe>
  <probe>
  SDL_HAPTIC_EFFECT_C_SIZE=72 ALIGN=8
  UNION_LAYOUT_C_OK
  ```

The original candidate history is retained; this fix is a new descendant commit.
Main and downstream pins are unchanged. The external Windows evidence is owned
by the independent acceptance task; this cloud did not read its local disk or
repeat its Windows runs. Windows re-audit and DLL/window smoke remain required.

## Required Windows/local follow-up

- On Windows x64, run the ABI regeneration script with the original Clang
  `22.1.8`/Windows compiler-header setup (`x86_64-pc-windows-msvc`, `NUL` input)
  and review all size/alignment/member/offset differences. This cloud did not
  regenerate native ABI data or independently prove all 839 field layouts.
- With GraalVM 25 and the official SDL `3.4.14` x64 DLL, run
  `:caelum-sdl3:sdl3Smoke -PcaelumJavaVersion=25 -Psdl3Dll=<absolute-DLL-path>`.
  Require `SDL_VERSION=3.4.14`, the GUID roundtrip and `SDL3_SMOKE_OK`; capture
  revision and exports. The smoke performs SDL video initialization, hidden
  window creation, event polling, window destruction and SDL shutdown.
- Coordinate further real-window/input/callback and GPU/Vulkan integration
  testing with the parent. The cloud CPU results are not substitutes for those
  gates. Main and downstream pins remain the responsibility of the single
  integration owner after independent review.
