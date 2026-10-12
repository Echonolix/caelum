# caelum-mdbx

Generated libmdbx bindings and a small Kotlin ownership layer. Supported native
platforms are Windows and Linux x86_64. Call `Mdbx.load()` before directly using
`functions.*`; `MdbxEnvironment.open()` loads automatically. Uses the Caelum JDK toolchain (default 24, validated with GraalVM 25) and
native access (`--enable-native-access=ALL-UNNAMED`).

```kotlin
MdbxEnvironment.open(existingDirectory).use { env ->
    env.begin().use { txn ->
        txn.table("example", create = true).put(byteArrayOf(0, 1, -1), bytes)
        txn.commit()
    }
    env.begin(readOnly = true).use { txn ->
        val copy = txn.table("example").get(byteArrayOf(0, 1, -1))
    }
}
```

Transactions are confined to their creating thread. `close()` aborts unfinished
transactions; commit consumes the native handle even when libmdbx reports an
error, except `MDBX_THREAD_MISMATCH`, as specified by upstream. Tables are scoped
to their transaction. Environment close refuses active transactions and pending
begins. A blocking writer begin does not hold the environment lifecycle monitor.
The environment supports transactions on different threads; libmdbx serializes
writers. Close all transactions before closing the environment.

Reads return independent `ByteArray` copies. Missing keys return null; zero-length
values return an empty array. Deletes return false for missing keys. Other native
errors throw `MdbxException` with the operation, code, and thread-safe upstream
error text. Read-only write operations and stale handles are rejected before
calling native code. The default open uses durable libmdbx defaults. Geometry is
bounded by `maxSizeBytes`, and does not implement an application quota, eviction,
record formats, compression, world identifiers, or a writer queue.

## Source and ABI provenance

- libmdbx 0.14.4, official mirror
  `https://github.com/Mithril-mine/libmdbx`.
- Archive commit: `a032e40c1c06eaf695a163cfc32d4a01ffb8b26c`.
- Archive SHA-256:
  `48d16ea17f33ebd7143a7d60701ff8259ecd500c05561f533d9d64273a022f47`.
- The archive's `VERSION.json` records origin commit
  `716ce9d5ae5458fa1de44097dc6e8f80ed1e4809`; it is distinct from the mirror
  archive identity. Both are preserved, not substituted for one another.
- Apache-2.0; binary and source JARs include upstream LICENSE and NOTICE.

`include/mdbx-binding.h` is a deliberately limited, normalized API surface,
processed by `codegen-c`. It exposes environment create/options/geometry/open/close,
transaction begin/commit/abort, table open, get/put/delete, and error formatting.
The environment-open entry point is `caelum_mdbx_env_open`, a small C bridge
calling upstream `mdbx_env_open`. It accepts fixed 32-bit mode/flags because
`mdbx_mode_t` is 16-bit on Windows and 32-bit on Linux; a shared generated
function descriptor cannot describe both signatures. C enums/flags use 32-bit carriers; `size_t` and `intptr_t` use explicit 64-bit
carriers on the supported x86_64 targets. C bool is normalized to a one-byte
unsigned carrier. `MDBX_val` retains upstream pointer/length field order.
Environment/transaction/latency structs are opaque and must never be allocated
or dereferenced. No by-value aggregate returns, cursor APIs, recovery tools,
callbacks, or inline-only helpers are claimed as bound.

`src/main/c/mdbx_abi.c` includes the unmodified upstream header, checks carriers
and constants at compile time on both targets, and provides version/layout probes.
CPU tests compare the actual generated Kotlin struct size, alignment, names,
and offsets against these probes and execute all included database operations.
The source JAR carries the normalized input, probes, upstream header and version
metadata so the binding is auditable.

## Reproducible native build

Uses Caelum `registerPinnedArchive` and `cross-native`, Zig 0.17.0 pinned by the
shared toolchain. Linux target: `x86_64-linux-gnu.2.28`; Windows:
`x86_64-windows-gnu`. Both use C11, `-O2`, shared linking and `-pthread`;
Linux additionally uses `-fPIC` and `_SYS_CACHECTL_H=1`; Windows links `-lntdll`.
Both define `MDBX_HAVE_BUILTIN_CPU_SUPPORTS=0`,
`MDBX_BUILD_SHARED_LIBRARY=1`, `LIBMDBX_EXPORTS=1`.
Build metadata uses the fixed timestamp `2026-10-12T00:00:00Z` and fixed build
flags description. This timestamp is metadata, not a claim of actual execution
time. CPU dispatch is disabled for Zig link compatibility; this is a compatibility
baseline, not a performance result. Binary outputs are stripped.

Run `:caelum-mdbx:test :caelum-mdbx:jar :caelum-mdbx:sourcesJar` for CPU integration
and publication artifacts. Windows runtime behavior (including file growth and
shrink) needs native Windows acceptance; cross-compilation and export inspection
alone do not establish it.
