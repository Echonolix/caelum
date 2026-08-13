# caelum-assimp

Java FFM/Kotlin bindings for the public C ABI of Assimp 6.0.4. The generated
surface covers scene data, meshes, skeletons, animation, cameras, lights,
materials, custom file I/O, importing, post-processing, math helpers, version
queries, and exporting.

The application must load a native Assimp 6.0.4 shared library before using
generated functions. Prefer `Assimp.load(absolutePath)`, which checks the exact
version, rejects double-precision builds, and rejects unsupported 32-bit
processes. Applications that load the library themselves must immediately call
`Assimp.validateLoadedLibrary()`.

The binding targets Assimp's default single-precision ABI. Native objects
returned by imports remain owned by Assimp and must be released with
`aiReleaseImport`. Export blobs must be released with `aiReleaseExportBlob`.
Callback implementations and their backing arenas must remain alive for as long
as Assimp can call them.

`include/assimp_abi_6_0_4.h` is a normalized, licensed derivative of the
official headers pinned to the commit recorded in that file. It deliberately
does not contain the C++-only `Assimp::aiCreateAnimMesh` helper.
