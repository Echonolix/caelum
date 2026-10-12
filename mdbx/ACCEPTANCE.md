# CPU acceptance — 2026-10-12

Baseline: Caelum `49c91580ae0d06fd7098b4e6dc55542de82b33f4`.
Isolated branch: `d1/mdbx-cpu`. No remote publication or merge.

## Verified

- Real `codegen-c` execution generated the normalized binding input, compiled
  Kotlin 2.4.21, and ran on GraalVM 25.0.4+7.1. Generation reran after the final
  shared generator change; no handwritten substitute binding or mock database.
- MDBX: 13 tests, 0 failures/errors/skips. Binary key `[0,1,-1,42]` and 65537-byte
  value survive write/commit/close/reopen and byte-for-byte readback. Abort preserves
  old values and removes uncommitted new keys/tables. Empty keys/values, missing
  keys, delete, independent named tables, no-overwrite error `-30799`, missing
  table error `-30798`, failed-open cleanup, invalid geometry/name guards, stale
  handles, close with live transactions, read-only guards, cross-thread rejection,
  concurrent writer commit and reader snapshot stability are covered.
- Generated `MDBX_val`: 16 bytes, alignment 8, `iov_base` at 0, `iov_len` at 8;
  names/member list checked against the native probes. Both target compiles assert
  the same upstream layout, flag constants and carriers. Generated FFM descriptor
  checks cover 64-bit geometry, one-byte bool, fixed-width open bridge and get.
- Regression: zstd 2 tests and core 7 tests, all passing. zstd regenerated through
  the real Linux parser and updated preprocessor with its configured defines.
- Both native targets built from pinned libmdbx with Zig 0.17.0. ELF dependencies
  are libc, loader and pthread; Windows imports include ntdll and the platform CRT.
  `nm -D` and `objdump -p` confirm all 18 normalized header symbols are exported
  by each binary (12 upstream functions, open bridge and five ABI/version probes).
- Binary JAR contains both platform binaries, Caelum LICENSE, upstream LICENSE and
  NOTICE. Source JAR contains generated Kotlin, normalized header, C bridge/probes,
  unmodified upstream header, VERSION.json, LICENSE and NOTICE. POM artifact name,
  description and Caelum core dependency checked. `git diff --check` passes.

## Environment and dependency restoration

Endoqa 0.0.11 POM/module metadata and matching JVM artifacts were fetched from
`https://maven.endoqa.io`. Versions came from the published metadata; no transitive
version guessing. Native parser source pins/licenses are in `codegen-c/README.md`.
The Linux parser does not use optional WASM and loads no wasmtime/zstd runtime.

The host only has a JRE 21 and libclang1-19. A user-directory clang was extracted
from the approved Debian snapshot
`https://snapshot.debian.org/archive/debian/20260925T181338Z/`. InRelease signatures
were verified by GPG against `/usr/share/keyrings/debian-archive-keyring.gpg`; the
compressed Packages SHA-256 was verified against the signed index:
`7778d3e3f303b7ddb8ce0fe7c8d57473a076c6bf2e8f241f75421d2396352498`.
All packages are version `1:19.1.7-3+b1`, with SHA-256:

- clang-19: `bbb74458d782b7792b61b0eed92746df5e6d033778c045cf8c2b3835b7c4efc4`.
- libclang-common-19-dev: `4018e40e2d4144fe38c45822e68cca63c1bf6c7872c3073ebb262ad4b2f9b8bc`.
- libclang-cpp19: `a7d2b6df51050834f6ea5c0d5e34869c2298b0bcdaa91eff17ba3db2cd545c47`.

Installed libllvm19/libclang1-19 are the exact matching version; `ldd`/actual clang
execution resolved dependencies. No system installation, sudo, permission changes,
network expansion or safety settings changes were used. Zig global cache was placed
in the writable workspace. libmdbx source/hash and native flags/timestamp are in
`README.md` and `build.gradle.kts`.

The preinstalled Gradle 9.8.1 requested an unavailable Kotlin DSL plugin 6.7.6.
Acceptance used the repository's Gradle 8.14.2 version, downloading its official
bin ZIP and verifying published SHA-256
`7197a12f450794931532469d4ff21a59ea2c1cd59a3ec3f89c035c3c420a6999`.
Gradle daemon ran on host JRE 21; module generators, compilation and tests used
GraalVM 25. The command used `-PcaelumJavaVersion=25` and
`-Porg.gradle.java.installations.paths=<GraalVM25>`.

An environment-only init script configured the existing HTTP(S) proxy and changed
buildSrc/ktgen's Java 8 bootstrap to the available JDK 25, with Java/Kotlin target 21
so the Gradle 21 daemon can load those plugins. Neither repository's bootstrap
source nor system security settings were changed for this workaround. Caelum's
normal default remains JDK 24. clang location was passed using `-Pcodegenc.clang`.

Final task set, `--no-configuration-cache --max-workers=2`:

```
:caelum-mdbx:check :caelum-mdbx:jar :caelum-mdbx:sourcesJar
:caelum-mdbx:generatePomFileForCaelum-mdbxPublication
:caelum-zstd:test :caelum-core:test
```

Result: `BUILD SUCCESSFUL`, 61 actionable tasks, 17 executed and 44 up-to-date.
The native files and JARs are build outputs, not committed binaries.

## Not established

Windows runtime execution, file growth/shrink and Windows reader/writer behavior
were not run. Cross-compilation and export/layout checks are not runtime acceptance.
No GPU, crash/power-loss injection, performance tuning, application storage format,
writer queue, compression integration, quota or eviction architecture was attempted.
There is no remaining blocker to the tested Linux CPU module scope.
