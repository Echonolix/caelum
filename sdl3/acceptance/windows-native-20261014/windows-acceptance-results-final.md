# Caelum SDL3 Windows native acceptance

Captured: 2026-10-14 UTC
Code under test: `8fa9ac1ae3c6cf79665731d0b820a02730f0313a`
`origin/main` at acceptance: `5883abfa3d93a4658f3d0b4c0a6b7c45ba461b71`
Scope: Caelum SDL3 on Windows x64.

## Tool and dependency provenance

- SDL was downloaded from the official `release-3.4.14` GitHub release. The official release metadata digest and local archive SHA-256 both equal `69a4e55645651af85e6ccfe40981b5a0bc2c594d0004fe7844db680e23cfbdaf`. The extracted DLL reports file/product version `3, 4, 14, 0`; SHA-256 is `9fa7bcb4c3667bfe4cf5e5b740a1da708151af0d499c2d0afee50c9874ba181c`.
- Gradle 9.5.0 `all` was downloaded from `services.gradle.org`. Its SHA-256 `a3c4ba4aca8f0075688b9c5b18939fd28e8cb4357c227da5c1d9f38343791439` matches the official sidecar and the candidate wrapper property.
- Runtime used Oracle GraalVM 25+37.1 and Clang 22.1.8 targeting `x86_64-pc-windows-msvc`. Archives, extracted tools, and Gradle cache stayed under this task-local acceptance directory; no global install or cache was changed.

## ABI and FFM layouts

Clang regenerated **122 named C records / 839 fields**, byte-for-byte matching the candidate `SDLTypeLayouts.kt` snapshot. Windows x64 static assertions passed for pointer/`size_t`/`long`/`wchar_t` widths and `SDL_GUID`, `SDL_FColor`, `SDL_HapticEffect`, and `SDL_TLSID` sizes and alignments.

The rerun Kotlin FFM audit lists **123 layouts / 840 fields** because it also prints the generated `SDL_TLSID` typedef as an additional one-field layout. The Clang record total counts named C `struct`/`union` records, so the `122/839` versus `123/840` totals are consistent.

## DLL and hidden-window smoke

At code SHA `8fa9ac1ae3c6cf79665731d0b820a02730f0313a`, the Gradle SDL smoke loaded the official DLL and reported `SDL_VERSION=3.4.14`, revision `SDL-release-3.4.14-0-g147a8ee32 (libsdl.org)`, the expected SDL GUID, and `SDL3_SMOKE_OK`. It initialized SDL video, created a hidden window, polled events, destroyed the window, and shut down SDL.

A same-process harness then attempted to load a text file as a DLL. Windows rejected it with `UnsatisfiedLinkError`; the SDL loader remained unloaded, and loading the real DLL afterward succeeded. Three additional init / hidden-window create / event-poll / destroy / quit cycles passed. No renderer, Vulkan path, GPU, or performance benchmark was used.

## CPU tests and format check

The candidate's CPU suite was rerun with Gradle 9.5.0 using `--offline --rerun-tasks --no-configuration-cache --max-workers=2` and `-PcaelumJavaVersion=25`:

- Core: 12 passed.
- SDL codegen: 23 passed.
- SDL runtime: 34 passed.
- Total: **69 passed, 0 failed, 0 errors, 0 skipped**.

The repository has no configured Spotless, ktlint, ktfmt, detekt, or `formatCheck` task. `git diff --check origin/main...HEAD` ran and reported 76 whitespace diagnostics in the imported SDL license and four headers plus the generated tab-delimited macro inventory. Those inputs were preserved unchanged; see `format-check-diagnostics.txt`.

## Candidate scope and integration

Before adding this evidence directory, the candidate had 138 tracked paths relative to `origin/main`: 130 under `sdl3/`, and eight outside it. `candidate-files.txt` records the exact source diff. The eight outside `sdl3/` are `README.MD`, `bom/build.gradle.kts`, `build.gradle.kts`, `core/build.gradle.kts`, `core/src/main/kotlin/net/echonolix/caelum/Type.kt`, `core/src/test/kotlin/net/echonolix/caelum/FunctionStubLifecycleTest.kt`, `core/src/test/kotlin/net/echonolix/caelum/UnionLayoutTest.kt`, and `settings.gradle.kts`.

The current commit is an evidence-only child of the tested code SHA. It adds files only under `sdl3/acceptance/windows-native-20261014/`; it does not modify candidate implementation sources. The candidate settings were restored byte-for-byte (SHA-256 `27366ab7d7e98709d7dd682e598f813d0e9d08c1e9b228a46f4944702168c6c6`). The primary worktree remained clean. No Fornax G4 files were touched, and no merge to `main` was performed.

## Evidence files

- `provenance-final.json` and the official `SDL-3.4.14-release-metadata.json`
- `sdl3-smoke-local-ktgen.log` and `sdl3-repeated-lifecycle-and-loader-error.log`
- `windows-clang-layout-generation.log`, `windows-abi-static-asserts.log`, and their harness source files
- `cpu-tests-ffm-audit-rerun.log`, `cpu-test-results-rerun.json`, and `ffm-layout-audit-rerun.log`
- `format-check-diagnostics.txt` and the task-local Gradle init scripts needed to reproduce the checks

Local archives, DLLs, caches, compiled classes, settings backups, and machine-specific paths are excluded from the candidate commit.
