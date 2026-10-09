# Binding authoring notes

This file records the constraints that must be checked when adding or updating
a Caelum native binding. It is intentionally limited to lessons that affect
the generated ABI.

## Choose the generation model

- Use `buildsrc.convention.codegen-c` for ordinary C header APIs, following
  `caelum-glfw`. Keep a fixed, licensed header input in the module and configure
  names with `codegenC.elementMapper`.
- When the binding also owns its native library, add `buildsrc.convention.cross-native`
  (see `caelum-zstd`). It compiles C, C++ and ISPC sources for every `NativeTarget`
  with the pinned zig/ISPC toolchain and packages them for `NativeLibraries`. Pin
  downloaded sources with `registerPinnedArchive` and a SHA-256, or vendor a single
  unmodified file with its provenance when no stable archive exists.
- Use a custom registry generator only when the upstream API requires one. The
  staged `caelum-vulkan` generator is specific to Vulkan's XML registry and is
  not the default model for C libraries.
- Keep declarations that the generic generator cannot represent in a small
  handwritten layer. Examples include C aggregate-by-value returns, which need
  a `SegmentAllocator` with Java FFM, and header-only inline helpers that have no
  native symbol.

## Normalize and verify the ABI

- Prefer a deterministic, checked-in normalization header when upstream headers
  contain C++ branches, compiler attributes, macro-generated declarations, or
  other constructs unsupported by `codegen-c`. Preserve the upstream header or
  exact provenance and license alongside the normalized input.
- Declare each struct or union field separately in normalized inputs. The
  current C AST adapter does not preserve every declarator in declarations such
  as `float r, g, b, a;`.
- Never change a type merely to make generation compile. Validate function
  carriers and every generated public struct's member list, size, alignment,
  field order, and offsets against the pinned upstream headers. Comparing only
  the upstream and normalized C headers does not validate generated Kotlin.
- C `bool` has no codegen-c carrier. Spelling it `uint8_t` in a normalized header is
  ABI-identical (one byte, values 0 or 1) and acceptable, but pin the result with the
  layout checks above (see `caelum-bc7e`).
- Named C enums generate `NIntEnum` types (32-bit `int` carrier). Integer `#define`
  constants become generated constants; filter them with `elementMapper`.
- C `char` uses the 8-bit `Byte`/`NChar` carrier. Signed and unsigned integer
  specifiers must resolve to the corresponding fixed-width Caelum type, and C
  typedef descriptors/layouts must delegate to their underlying ABI type.
- The generic C preprocessor currently targets `x86_64-pc-windows-gnu`. Bare C
  `long` therefore needs special review: Windows uses a 32-bit `long`, while the
  current basic-type promotion models it as 64-bit. Do not expose a bare `long`
  declaration without first resolving and validating that mismatch. Explicit
  fixed-width types and deliberately normalized aliases are safer.

## Runtime and publication contract

- Generated global functions resolve symbols eagerly when their generated file
  is initialized, through the class-loader lookup of caelum-core. The application
  must load a compatible native library before its first generated binding call.
  Most bindings do not bundle native libraries; `cross-native` modules do and expose a
  `load()` that calls `NativeLibraries.load` and checks the library version where the
  API reports one.
- Check the export table of every cross-compiled target. MinGW gets no
  `visibility("default")` from most upstream headers, so a library built with
  `-fvisibility=hidden` needs its Windows export macro set through
  `crossNative.targetDefines` (for example `ZSTD_DLL_EXPORT=1`).
- Add the module to `settings.gradle.kts`, the root module descriptions, the BOM,
  and the root module list. Document any pinned ABI or build-mode restriction in
  the module README.
- Package third-party notices in the binary and source jars. Include vendored or
  normalized header inputs in the source jar so the published binding remains
  auditable.
- Validate meaningful gates: clean code generation and compilation, upstream
  symbol/surface accounting, ABI layout comparison where structs are exposed,
  publication contents, and an existing-binding regression build after shared
  generator changes. Do not add tests that exercise no behavior or invariant.
