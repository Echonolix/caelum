# D1 review guide

This branch adds a deliberately bounded database module; the binding is generated
by the existing `codegen-c` path and uses real libmdbx. The complete change can be
reviewed with `git diff 49c91580ae0d06fd7098b4e6dc55542de82b33f4 HEAD`.

## Files and interfaces

| File | Reviewer focus |
|---|---|
| `build.gradle.kts`, `include/mdbx-binding.h` | Pinned source/hash, Zig flags, 18-symbol normalized ABI, Windows mode-width bridge |
| `src/main/c/mdbx_abi.c` | Unmodified upstream-header probes/assertions on both targets; version check; fixed-width open bridge |
| `src/main/kotlin/.../Mdbx.kt` | `Mdbx.load()`, exact 0.14.4 version, error operation/code plus thread-safe strerror_r |
| `src/main/kotlin/.../MdbxEnvironment.kt` | `open(path,maxSizeBytes,maxTables,geometry)`, `begin(readOnly)`, quiescent `resize(sizeBytes)`, `close()`; pending-begin reservation and native cleanup |
| `src/main/kotlin/.../MdbxGeometry.kt` | Open-time lower/current/growth/shrink/page-size settings, defaults/minimum semantics, bounds checks |
| `src/main/kotlin/.../MdbxTransaction.kt` | `table(name,create)`, `commit()`, rollback `close()`, table `get/put/delete`; platform-thread confinement and terminal handles |
| `src/test/kotlin/.../MdbxTest.kt` | 13 ABI, real database, lifecycle and snapshot gates |
| `src/test/kotlin/.../MdbxGeometryTest.kt` | 9 actual-file geometry, 64-bit upper bound, concurrency and exception-path gates |
| `../codegen-c/` | Hash-pinned native Linux parser, legacy Windows DLL path retained, configurable/error-checking clang |
| `../buildSrc/` | Per-target link flags, generator JDK launcher, optional JDK 25 override retaining default JDK 24 |
| Root module list/settings/descriptions/BOM | Module publication and resolution wiring |

## Ownership/ABI conclusions

`size_t` uses Caelum's 64-bit `NSize`/Long carrier; bits are retained despite Java's
signed Long representation. Actual copied data lengths are constrained to the
ByteArray range before reinterpretation. Geometry uses explicit signed 64-bit
carriers, not the generator's ambiguous `intptr_t`. The Windows 16-bit/Linux
32-bit `mdbx_mode_t` difference is handled inside the C bridge. C bool uses one
byte. Struct size/alignment/order/offsets and critical FFM descriptors are checked.
Opaque native types are never allocated or dereferenced by the wrapper.

Input arena storage outlives each synchronous downcall. `get()` copies mmap bytes
before any update/transaction termination; no borrowed native segment or handle
escapes. Every table checks its owning transaction. The Java thread and native OS
thread are stable because transactions require platform threads; virtual threads
are rejected before allocation. Native sticky mode allows one active transaction
per platform thread/environment; a failed second begin leaves the first usable.

Begin reserves environment liveness before leaving the monitor for native writer
waiting. Close/resize require no active transaction/reservation. Begin's pending
native handle is aborted on Java-side setup failure before ownership transfer.
Native commit/abort errors consume the handle except THREAD_MISMATCH; environment
close errors consume it except BUSY. Tests cover genuine begin rejection, MAP_FULL
and failed commit, without manufacturing errors or mocking native resources.

Geometry records the upper bound at open and never changes that bound online.
Explicit current-size changes are quiescent. Both automatic and explicit physical
shrinking are tested; neither deleting records nor aborting growth guarantees an
immediate exact-sized file. Native MVCC/GC may retain high pages. This distinction
is essential for a future used-page quota implementation.

## Platform matrix

| Gate | Linux x86_64 | Windows x86_64 |
|---|---|---|
| MDBX native build | Passed, Zig target glibc 2.28 | Passed, cross-compiled MinGW target |
| Required exports/compile-time ABI | All 18 plus carrier/layout assertions | All 18 plus carrier/layout assertions |
| Generated binding/compiler | Passed on GraalVM 25 | Same normalized ABI; Windows execution unrun |
| Real DB/environment/txn/errors | 22 tests passed on workspace overlayfs | Unrun |
| Growth/automatic shrink/explicit shrink/reopen | Passed with measured actual-file sizes | Unrun |
| Reader/writer and queued writers | Passed in bounded real tests | Unrun |
| C generator loader | Actual parser and zstd generation passed | DLL bytes/loading order statically preserved; host run unrun |
| Publication contents | Both binaries, generated sources/headers, LICENSE/NOTICE/COPYRIGHT checked | Windows binary included; runtime unrun |

Targeting glibc 2.28 does not constitute testing on a glibc 2.28 host. macOS/ARM
are outside the current supported native targets. Preprocessor defines now take
effect on both platforms, so no Windows generated-output equivalence is claimed.

## Provenance and remaining scope

All upstream source pins, original/mirror commit distinction and licensing are in
`README.md`/`ACCEPTANCE.md`. Rehashed source archives have only regular relative
members, without traversal, links or device entries; the shared downloader verifies
hashes before extraction. The Debian clang recovery used the approved signed
snapshot and exact package hashes. No credentials, private implementation,
hardcoded workspace/home configuration, extra network permission or security
settings change was introduced.

D1 still needs native Windows runtime/geometry/concurrency acceptance. No
power-loss durability, crash injection, throughput or performance result is
claimed. Application record format, compression, mapped read borrowing, scans,
used-page statistics, writer queues and eviction/quota policy remain future work.
See `ACCEPTANCE.md` for exact test counts, observed geometry behavior and environment
bootstrap details. This review did not modify unrelated rendering/meshing code.
