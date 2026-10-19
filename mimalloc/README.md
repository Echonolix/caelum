# caelum-mimalloc

Explicit `mi_*` allocation from Microsoft mimalloc **v2.5.2** (MIT), packaged for
Windows/Linux x86_64 and macOS arm64 using the pinned Zig cross-native toolchain.
The source archive URL and SHA-256 are fixed in `build.gradle.kts`; `include/mimalloc-api.h`
normalizes only the bound subset of upstream `include/mimalloc.h`. `size_t` is 64-bit
and `bool` is one byte on all targets. Upstream header and license ship in the sources jar.

`Mimalloc.malloc(size, alignment)` returns an uninitialized address; `calloc` zeros it.
Both throw `OutOfMemoryError` on native allocation failure. Free each address exactly
once with `Mimalloc.free`, after all borrowers finish. Allocation and free may run
on different threads, including after the allocating thread exits. Never mix allocators.
No JVM Arena, automatic cleanup, or allocation-lifetime guard is attached. A bounded
`MemorySegment.ofAddress(address).reinterpret(size)` view keeps bounds checks but
does not detect use after free; callers must supply an ownership protocol.

The library loads normally on first use, with a native-version check. This build leaves
`MI_MALLOC_OVERRIDE` undefined and sets `MI_WIN_NOREDIRECT=1`: it does not redirect CRT
malloc/free, require early DLL injection, or replace JVM allocations. Release optimization
and basic upstream statistics are enabled; secure/guarded sampling is not enabled.
`collect` is an optional calling-thread maintenance operation, not a per-free step.
On Windows this build defaults `MI_DEFAULT_EAGER_COMMIT=0`: pages commit as needed
instead of eagerly committing a whole native segment per worker. This changes only
this library's default; mimalloc remains responsible for all allocation and caching.

The maintained v2 stable branch is intentional: the initial v3.5.3 Windows build
passed allocation/remote-free tests but exited with `0xc0000005` when
`MIMALLOC_SHOW_STATS=1` was set (default statistics-disabled exits passed). Switching
v3 to the upstream LOCAL TLS option did not remove that failure. v2 must pass both
statistics-enabled and default process exits before acceptance; no upstream source
patches or automatic-statistics suppression are used.

References: [upstream](https://github.com/microsoft/mimalloc/tree/v2.5.2),
[explicit API usage](https://microsoft.github.io/mimalloc/using.html).
