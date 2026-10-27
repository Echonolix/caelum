# caelum-jolt upstream and build policy

This module is an original Apache-2.0 Caelum bridge over the MIT-licensed Jolt
Physics library. It has no JNI dependency. Generated Kotlin comes only from
`include/caelum_jolt.h`; opaque declarations are handles, never allocatable C++
layouts. ABI version 1 is a development contract until the release gates pass.

## Pinned inputs

| Input | Pin |
| --- | --- |
| Official Jolt Physics | `v5.6.0`, commit `e77f175595e64cb44218cc9d9d56fc365ad0e36a` |
| Archive | <https://codeload.github.com/jrouwe/JoltPhysics/tar.gz/e77f175595e64cb44218cc9d9d56fc365ad0e36a> |
| SHA-256 | `1f32328fb763135de10a244568d6ccb2ed9b1e6593fafe6dc6db5b2719d330bd` |
| License | [upstream MIT LICENSE](https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/LICENSE), packaged verbatim as `META-INF/licenses/JoltPhysics-LICENSE` |
| jolt-jni design reference | `stephengold/jolt-jni`, commit `ec8dd7bc8bfefd37b0d62f4ab5d6b9f9e227a6a1`, `6.2.1-SNAPSHOT` |
| jolt-jni engine reference | `stephengold/JoltPhysics`, tag `sg261013`, commit `a4b24c3fadd7eade0435f27e9d6a07564273eb5f` |

The original Caelum roadmap compared the JNI project's ownership, const views,
contact adapters and floor/ball example. No JNI implementation or example code
is copied into this module. The reference fork is not used as a build input.
Fork-only helpers, JNI-specific callbacks/Cleaner machinery, V-HACD integration
and Java API compatibility are excluded. Official classes and functions actually
used by this release are accounted for in [SURFACE.md](SURFACE.md).

## Reproducible configuration

`build.gradle.kts` uses `registerPinnedArchive` and the repository's pinned Zig
toolchain in `buildSrc/src/main/kotlin/NativeToolchainService.kt`. The manifest
`native-sources.txt` contains exactly the 130 C++ translation units in the first
`JOLT_PHYSICS_SRC_FILES` block of the pinned `Jolt/Jolt.cmake`. The
`verifyJoltSources` task checks this before every native build. Optional object
streams, shader wrapper translation units, CPU compute and DX12/Vulkan/Metal
backends are not selected. Base Jolt code includes several later-feature classes;
compiling them does not expose them through this bridge. Hair has no enabled
compute backend or public Caelum API; debug-renderer code remains disabled by its
upstream feature guard. No CMake cache or host-installed Jolt library is used.

| Setting | ABI 1 policy |
| --- | --- |
| World coordinates | `JPH_DOUBLE_PRECISION=1`; C transport position is three doubles |
| Velocity, normals, rotations | floats; transport Vec3 is 12/4, RVec3 24/8, Quat 16/4 bytes/alignment |
| Object layers | `JPH_OBJECT_LAYER_BITS=16`; table implementation limits layers to 4096 |
| Broad-phase layers | 1–256, copied uint8 mapping |
| CPU | target baseline; no AVX/AVX2/AVX512/FMA; AArch64 uses baseline NEON |
| Floating point | `JPH_CROSS_PLATFORM_DETERMINISTIC=1`, `-ffp-contract=off`, no fast-math |
| C++ | C++17, `-O2`, `-fexceptions`, `-fno-rtti`, hidden visibility, `NDEBUG=1` |
| Optional features | no `JPH_USE_DX12/VK/MTL/CPU_COMPUTE`, `JPH_DEBUG_RENDERER`, `JPH_OBJECT_STREAM` |
| Exports | `CJ_API`: explicit Windows dllexport / default ELF and Mach-O visibility |
| Linux baseline | repository `x86_64-linux-gnu.2.28`, pthread and dl |
| Runtime | JDK 24 default, `--enable-native-access=ALL-UNNAMED` |

Compile-time guards reject incompatible feature/precision builds. Deterministic
flags are a configuration choice, not evidence of bit-identical results on
unverified platforms. Bootstrap uses exact FFM address carriers to check ABI,
precision, layer width and the entire build ID before generated global functions
resolve their symbols:

```text
caelum-jolt/1;jolt=e77f175595e64cb44218cc9d9d56fc365ad0e36a;real=64;layer=16;cpu=baseline;fp=deterministic;exceptions=on;gpu=off
```

The Jolt sources link into a single `caelum_jolt` shared library. Local runtime
jars use the host target. Maven publication uses the existing `portableJar` and
requires all three native outputs; no publication task substitutes a host-only
jar. Build scripts, headers, bridge sources, manifest, provenance and licenses
are included in the source jar. Actual toolchain/link/runtime dependency and
three-platform acceptance results remain pending; see [acceptance](acceptance/README.md).

## Upgrade procedure

Change the commit/archive hash together, compare the official C++ declarations
listed in SURFACE, update the source manifest and run its verification task.
Revisit every body default, enum conversion, field and callback contract. An
incompatible public layout/symbol/policy change requires an ABI/build-ID update
on both sides. Run all native layout, scenario, stress, package and independent
consumer gates on each declared platform before publishing a portable release.
