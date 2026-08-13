# Caelum OpenAL

`caelum-openal` is a generated Kotlin binding for the OpenAL 1.1 AL and ALC
core APIs. Kotlin global functions retain their C export names (for example,
`alGetError` and `alcGetError`); generated function descriptor and pointer
types use distinct `ALFunc...` and `ALCFunc...` names.

## Native-library loading

This artifact contains bindings only, not a native OpenAL implementation.
Load an OpenAL library before the first binding call using the application
class loader (for example, `System.loadLibrary("OpenAL32")` on Windows or the
appropriate absolute-path load for a bundled library). Caelum resolves symbols
through `SymbolLookup.loaderLookup()`, so the preloaded library must be visible
to that loader. No automatic loader is supplied because library names,
packaging, and deployment policy are application-specific across Windows,
macOS, and Linux.

## Vendored headers and provenance

The fixed header inputs are `include/AL/al.h` and `include/AL/alc.h`, copied
without modification from OpenAL Soft **1.25.2** commit
`b2c48f7718ef3fcf67921a8b6534c4914e328970`:

- <https://github.com/kcat/openal-soft/tree/1.25.2/include/AL>
- Source checkout used during vendoring:
  `build/upstream-binding-research/openal-soft`

Both vendored header files carry their own Unlicense/public-domain notice. The
OpenAL Soft repository's `COPYING` file documents the project-level LGPL-2.0
licensing; this module copies only the two header files and preserves their
upstream notices. No extension header (`alext.h`, `efx.h`, or related files) is
included.

`codegen/openal-core.h` is a generator-only normalized input derived from the
two fixed headers. It retains every API constant, typedef, function-pointer
typedef, and AL/ALC declaration while removing only include guards, C++ linkage
blocks, and declaration-only `__declspec`/`__cdecl` macros that the current C
parser cannot model. The vendored headers remain byte-for-byte upstream copies;
normalization does not alter any AL/ALC function signature.
