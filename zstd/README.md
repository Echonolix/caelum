# caelum-zstd

Bindings for the stable [Zstandard](https://github.com/facebook/zstd) API (`zstd.h`
without `ZSTD_STATIC_LINKING_ONLY`, plus `zstd_errors.h`), with the library built
for Windows x86_64 and Linux x86_64 and bundled in the jar.

## Usage

```kotlin
Zstd.load() // once, before any function in net.echonolix.caelum.zstd.functions
val written = ZSTD_compress(dst, dstCapacity, src, srcSize, 3)
check(ZSTD_isError(written) == 0u)
```

`Zstd.load()` extracts the bundled library through `NativeLibraries` and checks that
its version matches the bindings. Sizes are `size_t` and map to `Long`.
`ZSTD_getFrameContentSize` returns `ULong`; compare it with
`Zstd.CONTENTSIZE_UNKNOWN` and `Zstd.CONTENTSIZE_ERROR` converted with `toULong()`.

## Build

- Sources: the zstd v1.5.7 release tarball, downloaded by Gradle and verified by
  SHA-256 (see `build.gradle.kts`).
- Compiled with `-O3` and `ZSTD_DISABLE_ASM` (the x86_64 Huffman decoder is
  assembly; the C fallback keeps the build C-only). Only `ZSTDLIB_API` symbols are
  exported.
- Not bound: `ZSTD_cParam_getBounds` and `ZSTD_dParam_getBounds` (struct-by-value
  returns), the experimental static-linking API, and the 64-bit macro constants that
  `Zstd` declares by hand.

## License

zstd is dual-licensed BSD/GPLv2; this module uses it under the BSD license, shipped as
`LICENSE-zstd` in the jar.
