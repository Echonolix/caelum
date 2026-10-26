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

## Final review and additional Linux gates

The first 13-test run used JUnit's default `/tmp` temp directory, which is tmpfs
on this host. It was still the real native database, but libmdbx selected its
in-core filesystem branch. The final module test run uses a workspace build
subdirectory on overlayfs: the default durable native branch executes, and no
`in-core database` message is present. This is close/reopen and real filesystem
acceptance, not evidence of host power-loss durability.

Added 9 real integration tests; final module total: **22 tests, zero failures,
errors or skips**. Shared generator/core/zstd code did not change during this
review, so their previous 9 passing regression tests were not rerun.

Observed physical file sizes (bytes):

| Case | Before/grown | After |
|---|---:|---:|
| Growth with a live snapshot reader | 65,536 → 2,359,296 | Old snapshot and subsequent readers remained correct |
| Delete with a reader retaining 32 overflow values | 2,359,296 | Same old values readable until reader close |
| Quiescent `resize(0)` after delete and reader release | 2,359,296 | 2,293,760; native clamps to retained MVCC/GC pages |
| Temporary 4 MiB tail inserted/deleted in one write txn with live reader | 4,456,448 | 262,144 automatically on commit; old reader remains valid |
| Aborted 4 MiB growth | 65,536 → 4,456,448 | Subsequent commit, close and reopen retained physical size |
| Explicit quiescent resize after the aborted-growth reopen | 4,456,448 | 65,536; committed marker preserved, aborted key absent |

A pure C diagnostic linked to the same library independently reproduced the
aborted-growth behavior and reported the configured native geometry. Neither
source nor native library was patched to force shrinking. Delete is not a promise
of immediate file truncation; physical file size is not database used-page count.

Further checks: queued writer serialization with a close attempt completing while
the writer waits; failed native begin returning its active reservation; MAP_FULL
put (`-30792`) followed by failed commit (`MDBX_RESULT_TRUE = -1`) consuming the
handle exactly once; continued reads of earlier committed data; resize rejection
with active readers/writers; minimum-size clamp preserving data; rejected virtual
threads; a 64 GiB upper mapping bound with an actual file under 1 MiB; invalid
geometry rejected before file creation. These are small bounded cases, without
stress loops, crash injection or performance claims.

Review repairs:

- Reject virtual threads before native begin: Java thread identity alone does not
  preserve libmdbx's OS-thread/TLS ownership across carrier migration.
- On Java-side failure after successful native begin but before ownership transfer,
  abort the pending handle and retain cleanup failures as suppressed exceptions.
  Keep the environment reservation if native cleanup cannot confirm consumption.
  This exception path was statically reviewed; no fake allocator/OOM injection.
- `MdbxGeometry` makes open-time growth/shrink controls explicit; `resize()` permits
  only quiescent current-size changes and retains the upper mapping bound.
- Package upstream COPYRIGHT alongside LICENSE/NOTICE, since NOTICE references
  those credits. Source/archive identities and flags are unchanged.

Windows generator review: all four existing DLL resources are byte-for-byte
identical to the baseline, and the non-Linux loading order remains
`libzstd → wasmtime → libtree-sitter-0.25 → c`. The clang path override, concurrent
pipe handling and error checks are shared. Configured preprocessor defines now
actually take effect on both platforms; Windows execution/output equivalence was
not tested. The new Linux parser build is packaged alongside those legacy DLLs;
Windows-host execution of that build was not run.

Download/provenance review: the exact three pinned source archives were rehashed;
all archive members are regular files/directories with relative paths, without
`..`, absolute paths, links or special entries. The existing shared downloader
checks HTTP status and SHA-256 before extraction, writes via a partial download,
and publishes extraction under an archive-keyed lock and completion marker. No
new trusted host, permissive TLS or system permission change was introduced.
No machine-specific workspace/home paths, private repository source or secrets
were added to committed production code/documentation. Platform keyring/example
clang paths in the acceptance instructions are portable descriptions, not an
embedded machine configuration.

Remaining boundary: Windows x86_64 native runtime, geometry and concurrency are
still unexecuted. The direct binding intentionally covers the 18-symbol D1 subset;
scans/cursors, used-page statistics, mapped-value borrowing, application encoding,
writer queues and quota/eviction policy are not implemented by this work package.

## Bounded cursor scan increment (2026-10-12)

Baseline `b1e69cd02890e04f57577b667477e07cb8270506`; isolated branch
`e2/mdbx-prefix-scan`. Only the generic synchronous scan binding/wrapper and
focused tests/docs were added; no downstream application code or format was copied.

The actual code generator regenerated three new cursor functions from the
normalized header. The existing pinned upstream header was independently checked
for signatures and enum ordinals. Both Linux and Windows native compiles passed
cursor-op constant/width assertions; `nm -D` / `objdump -p` found all three cursor
exports. Windows runtime remains unexecuted.

GraalVM25 Linux on workspace filesystem: `MdbxScanTest` **2 tests, zero
failures/errors/skips**. Covers binary order including 0xff, exact-prefix key,
exclusive bounded continuation, missing prefix/end, empty-prefix whole table,
invalid limit/prefix-continuation, copied values after all handles close, stale
table scan and cross-platform-thread rejection.

Task set: `:caelum-mdbx:test --tests '*MdbxScanTest' :caelum-mdbx:jar :caelum-zstd:jar`;
`BUILD SUCCESSFUL`, 55 actionable tasks (34 executed, 21 up-to-date). Existing
point/lifecycle/geometry implementation and zstd sources were unchanged; their
prior regression claims remain those documented above, without an unnecessary full rerun.
Same GraalVM25/Gradle8.14.2/init bootstrap/proxy and verified pinned source/tool
setup as above, restored in a fresh environment. No installation privilege,
network security, or pinned native dependency changes.

### Increment review

Added opt-in point-read and scan copy limits in the same public transaction file;
original method signatures remain available. Native lengths are checked before
copy allocation, with per-key, per-value and total-batch byte budgets. Cursor
closure on bounds exceptions was verified by a subsequent successful cursor in
the same transaction. No new native symbol, source archive, or unrelated public
module/file was changed during this review.

Focused `MdbxScanTest`: **3 tests, zero failures/errors/skips**. Adds 65536-byte
stored-value rejection with a 4096-byte read/scan limit, negative limit rejection,
key/total-budget rejection, successful reads/scans after failures, and a missing
exclusive-continuation key seeking forward. Legacy unrestricted `get(key)` still
returns the original 65536-byte value. Build: `BUILD SUCCESSFUL in 10s`, 47 tasks,
2 executed and 45 up-to-date; native outputs remained up-to-date. Prior two-target
carrier assertions/export checks still describe the unchanged native code.

## 2026-10-26: direct transaction-owned value writes (Windows)

Fornax cold-start JFR found repeated 9,773,372-byte snapshot writes spending most
writer samples in the temporary native allocation/clear/copy. `put()` now fills
`MDBX_RESERVE` storage synchronously, before the next mutation. The pinned 0.14.4
header requires this lifetime and forbids DUPSORT; the wrapper exposes ordinary
tables. Snapshot invalidation, commit ordering and the stored bytes are unchanged.

GraalVM 25 on Windows: `MdbxTest`, `MdbxScanTest`, `MdbxUsageTest` **18/18 passed**.
Includes large value preservation after further writes, failed NOOVERWRITE,
aborted overwrite, size changes and reopen. Fornax owner/writer combination
**121 executions passed** (includes existing repeated unload checks).

Fixed-input A/B/B/A probe, 32 warmup and 128 measured put/delete transaction pairs
per process, 9,773,372-byte values: old writer CPU 1,781.25/1,515.625 ms; candidate
531.25/531.25 ms. Wall time 2,853/2,954 ms versus 1,390/1,522 ms; another game was
running, so this is a local hotspot result, not end-to-end loading/FPS acceptance.

The full suite hung in `growthAndShrinkPreserveActiveSnapshotsAndReopen`: the
writer was inside native put/growth while the reader's executor cleanup waited
for it. The frozen old put implementation reproduced the same stack and exceeded
a separate 20-second limit. Both diagnostic JVMs were terminated after capturing
stacks. That concurrent Windows geometry case is **not passed**, and was not
silently skipped or attributed to this optimization. Linux geometry evidence
above remains separate. Raw logs, frozen source and probes are in Fornax's ignored
`build/diagnostics/lod-slow-20261026/`.
