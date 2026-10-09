# caelum-bc7e

Bindings for `bc7e.ispc`, the SIMD BC7 encoder from
[bc7enc_rdo](https://github.com/richgel999/bc7enc_rdo), with the library built for
Windows x86_64 and Linux x86_64 and bundled in the jar. The library contains SSE2,
SSE4, AVX2 and AVX-512 (SKX) code paths and picks one at runtime.

## Usage

```kotlin
Bc7e.load() // once; loads the library and builds the encoder tables
bc7e_compress_block_params_init_basic(params, 1u) // 1u = perceptual metrics
bc7e_compress_blocks(blockCount, outBlocks, rgbaPixels, params)
```

Input is 16 RGBA8 texels (64 bytes, row order) per 4x4 block, output is 16 bytes per
block. Blocks are independent and `bc7e_compress_blocks` is thread-safe after
`Bc7e.load()`, so callers split work across threads by block ranges. Presets from
fastest to best quality: `ultrafast` (opaque blocks use mode 6 only), `veryfast`,
`fast`, `basic`, `slow`, `veryslow`, `slowest`.

## Sources and ABI

- `src/main/ispc/bc7e.ispc` is vendored unmodified from bc7enc_rdo commit
  `b9438627eef73a1157e84201b6fa6eb2ffd6d9f0` (SHA-256
  `e10f27ea5993679305537eafd854d9f0b58836470b5646b13858eac5593b4b8f`). It is a single
  file without a stable release archive, so it is vendored instead of downloaded.
- `include/bc7e.h` is the codegen-c input. It mirrors the header ISPC generates, with
  `bool` spelled `uint8_t` and the anonymous settings structs named
  `bc7e_opaque_settings` and `bc7e_alpha_settings`.
- `src/main/c/bc7e_abi_check.c` statically asserts the ISPC struct sizes and offsets,
  and `Bc7eTest` asserts the same numbers on the generated Kotlin layouts. Update
  all three together when updating `bc7e.ispc`.

## License

`bc7e.ispc` is Copyright (C) 2018-2021 Binomial LLC under the Apache License 2.0.
The jar ships `LICENSE-bc7enc_rdo` (upstream notice) and `LICENSE-Apache-2.0`.
