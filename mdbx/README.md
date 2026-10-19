# caelum-mdbx

Generated libmdbx bindings and a small Kotlin ownership layer. Supported native
platforms are Windows/Linux x86_64 and macOS arm64. Call `Mdbx.load()` before directly using
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

Transactions are confined to their creating platform thread. `begin()` rejects
virtual threads: Java thread identity does not pin a native OS thread, and libmdbx
uses OS thread ownership/TLS. Use a dedicated platform-thread executor when
calling from coroutine or virtual-thread applications. In the default sticky
thread mode, libmdbx allows only one active transaction per thread/environment;
a second begin may fail with a native error without invalidating the first. `close()` aborts unfinished
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

## File geometry and lifecycle

Pass `MdbxGeometry` to `MdbxEnvironment.open()` for lower/current size, growth step,
shrink threshold and page size. The native implementation rounds to database/system
pages and clamps current size to live MVCC pages. `-1` retains an existing setting
or selects the native default; zero shrink disables automatic shrinking. Set both
growth/shrink to zero to disable automatic resizing; libmdbx may round zero growth
up when shrinking is enabled. Zero lower/current/page size requests the native minimum. Page size cannot change
for an existing database. The upper bound remains `maxSizeBytes`.

`env.resize(sizeBytes)` changes only current file size, with all other geometry
settings retained. It rejects active transactions and pending begins, keeps the
maximum mapping bound fixed and serializes against begin/close. Shrinking below
live pages is clamped; it does not discard records. `resize(0)` requests the native
minimum compatible with retained pages, not an exact zero-byte file.

Physical file size is not used-page count. Delete and commit do not guarantee
immediate physical shrinking: native GC/MVCC can retain high pages, and an aborted
growth can leave a larger physical file even after commit/close/reopen. Linux CPU
acceptance covers growth with a live reader, automatic shrink of temporary tail
pages with a live reader, and explicit quiescent shrink with preserved records and
reopen. This is neither application quota enforcement nor a guarantee that every
unused extent can be returned. See `ACCEPTANCE.md` for observed sizes.

## Source and ABI provenance

- libmdbx 0.14.4, official mirror
  `https://github.com/Mithril-mine/libmdbx`.
- Archive commit: `a032e40c1c06eaf695a163cfc32d4a01ffb8b26c`.
- Archive SHA-256:
  `48d16ea17f33ebd7143a7d60701ff8259ecd500c05561f533d9d64273a022f47`.
- The archive's `VERSION.json` records origin commit
  `716ce9d5ae5458fa1de44097dc6e8f80ed1e4809`; it is distinct from the mirror
  archive identity. Both are preserved, not substituted for one another.
- Apache-2.0; binary and source JARs include upstream LICENSE, NOTICE and the COPYRIGHT
  credits referenced by NOTICE.

`include/mdbx-binding.h` is a deliberately limited, normalized API surface,
processed by `codegen-c`. It exposes environment create/options/geometry/open/close,
transaction begin/commit/abort, table open, get/put/delete, and error formatting.
The environment-open entry point is `caelum_mdbx_env_open`, a small C bridge
calling upstream `mdbx_env_open`. It accepts fixed 32-bit mode/flags because
`mdbx_mode_t` is 16-bit on Windows/macOS and 32-bit on Linux; a shared generated
function descriptor cannot describe both signatures. C enums/flags use 32-bit carriers; `size_t` and `intptr_t` use explicit 64-bit
carriers on the supported targets. C bool is normalized to a one-byte
unsigned carrier. `MDBX_val` retains upstream pointer/length field order.
Environment/transaction/latency structs are opaque and must never be allocated
or dereferenced. No by-value aggregate returns, public cursor handles, recovery tools,
callbacks, or inline-only helpers are claimed as bound.

`src/main/c/mdbx_abi.c` includes the unmodified upstream header, checks carriers
and constants at compile time on all targets, and provides version/layout probes.
CPU tests compare the actual generated Kotlin struct size, alignment, names,
and offsets against these probes and execute all included database operations.
The source JAR carries the normalized input, probes, upstream header and version
metadata so the binding is auditable.

## Reproducible native build

Uses Caelum `registerPinnedArchive` and `cross-native`, Zig 0.17.0 pinned by the
shared toolchain. Linux target: `x86_64-linux-gnu.2.28`; Windows:
`x86_64-windows-gnu`; macOS: `aarch64-macos-none`. All use C11, `-O2`,
shared linking and `-pthread`; Unix targets use `-fPIC`, Linux also sets
`_SYS_CACHECTL_H=1`, and Windows links `-lntdll`.
All define `MDBX_HAVE_BUILTIN_CPU_SUPPORTS=0`,
`MDBX_BUILD_SHARED_LIBRARY=1`, `LIBMDBX_EXPORTS=1`.
Build metadata uses the fixed timestamp `2026-10-12T00:00:00Z` and fixed build
flags description. This timestamp is metadata, not a claim of actual execution
time. CPU dispatch is disabled for Zig link compatibility; this is a compatibility
baseline, not a performance result. Binary outputs are stripped.

Run `:caelum-mdbx:test :caelum-mdbx:jar :caelum-mdbx:sourcesJar` for CPU integration
and publication artifacts. Windows runtime behavior (including file growth and
shrink) needs native Windows acceptance; cross-compilation and export inspection
alone do not establish it.

## Bounded prefix scans

`MdbxTable.scanPrefix(prefix, limit = 256, after = null)` scans an ordinary binary
lexicographic table with a cursor. `limit` is 1..4096; `after` is an exclusive key
from the same prefix. An empty prefix scans the table from its first key. Resume
with the last returned key, even when application filtering discards all results.
A short result denotes the end of that prefix; an exactly full result may require
one further scan to establish the end. A missing continuation key seeks the next
key, so deleting the continuation does not restart enumeration.

`MdbxEntry` owns copied key/value byte arrays; nothing borrows mmap storage or
escapes with a live native cursor. The cursor opens/closes within one synchronous
transaction call, including on error. Transaction/thread/lifecycle checks are the
same as point reads. No cursor handle, DUPSORT API, or used-page statistics API is
exposed. An application requiring a stable multi-page snapshot should perform all
scans in one read transaction; separate transactions can observe intervening writes.

The normalized binding adds only `mdbx_cursor_open`, `mdbx_cursor_get`, and
`mdbx_cursor_close`. `MDBX_FIRST=0`, `MDBX_NEXT=8`, `MDBX_SET_RANGE=17`, enum width,
and signatures were checked against the existing pinned 0.14.4 header; native
compile-time assertions check the constants/carrier on all targets.

Copy bounds for untrusted stored blobs are opt-in, preserving the original
`get(key)` and three-argument `scanPrefix` signatures. `get(key, maxValueBytes)`
checks the native value length before copying. The bounded scan overload requires
`maxKeyBytes`, `maxValueBytes`, and `maxTotalBytes`; all lengths and the remaining
batch byte budget are checked before copying. Count alone does not bound byte
allocation. On a byte-limit failure the scan throws and closes its cursor; it
does not return a misleading short batch. The transaction remains usable.

## Transaction used-page statistics

`txn.usage()` reads upstream `mdbx_txn_info(txn, ..., false)` and
`mdbx_env_stat_ex(NULL, txn, ..., sizeof(MDBX_stat))` through a fixed-width C
bridge compiled with the pinned official header. `usedBytes` is exactly
`txn_space_used` (extent through the last used page); `usedPages` divides by
`ms_psize`. `fileBytes` is `txn_space_limit_soft`, provided separately. It is
not a sum of live value lengths or an estimate from physical file size. GC and
MVCC-retained pages may remain in the used extent after deletion. A read
transaction reports its own snapshot. No reader-lock-table scan is performed.
The existing transaction thread/finished-handle contracts apply.
Official source: https://github.com/Mithril-mine/libmdbx/blob/a032e40c1c06eaf695a163cfc32d4a01ffb8b26c/mdbx.h
(`MDBX_txn_info`, `MDBX_stat`, `mdbx_txn_info`, `mdbx_env_stat_ex`).
