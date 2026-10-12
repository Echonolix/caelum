# C binding generator runtime

The Endoqa Kotlin bindings remain pinned to `io.endoqa:tree-sitter:0.0.11` and
`io.endoqa:libtree_sitter:0.0.11`. Their published Gradle metadata redirects to
`tree-sitter-jvm:0.0.11` and `libtree_sitter-jvm:0.0.11`; the JVM POMs declare
Kotlin stdlib 2.0.21 and the matching libtree_sitter runtime. They do not package
or declare a native runtime. Repository: `https://maven.endoqa.io`.

Linux x86_64 uses a bundled native C parser compiled by the shared Zig toolchain:

- tree-sitter v0.25.3, official archive
  `https://github.com/tree-sitter/tree-sitter/archive/refs/tags/v0.25.3.tar.gz`,
  SHA-256 `862fac52653bc7bc9d2cd0630483e6bdf3d02bcd23da956ca32663c4798a93e3`;
- tree-sitter-c v0.24.1, official archive
  `https://github.com/tree-sitter/tree-sitter-c/archive/refs/tags/v0.24.1.tar.gz`,
  SHA-256 `25dd4bb3dec770769a407e0fc803f424ce02c494a56ce95fedc525316dcf9b48`.

Both upstream projects use the MIT license; notices are included in the generator
JAR. The grammar has language ABI 15, accepted by this tree-sitter runtime.
Native C parsing does not use tree-sitter's optional WASM feature, so the Linux
path has no wasmtime/zstd dependency. Windows retains its existing DLL loading
path. This change does not establish macOS support or replace the Windows runtime.

A clang executable is required to preprocess headers. Override its location with
`-Pcodegenc.clang=/absolute/path/to/clang` when it is not on PATH. Both preprocess
passes use it; configured defines are supplied to the declaration pass. Generator
JavaExec tasks use the module's JDK toolchain rather than the Gradle daemon JVM.

Preprocessing drains stdout/stderr concurrently and fails on a nonzero clang
exit instead of silently generating bindings from partial output.
