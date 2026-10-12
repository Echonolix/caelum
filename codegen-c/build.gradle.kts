import buildsrc.convention.registerPinnedArchive

plugins {
    id("buildsrc.convention.codegen")
    id("buildsrc.convention.cross-native")
}

// Endoqa 0.0.11 exposes tree-sitter 0.25 API; C parsing uses native grammar,
// without the optional WASM runtime. Build from official, hash-pinned sources.
val treeSources = registerPinnedArchive(
    "treeSitterSources",
    "https://github.com/tree-sitter/tree-sitter/archive/refs/tags/v0.25.3.tar.gz",
    "862fac52653bc7bc9d2cd0630483e6bdf3d02bcd23da956ca32663c4798a93e3",
)
val grammarSources = registerPinnedArchive(
    "treeSitterCSources",
    "https://github.com/tree-sitter/tree-sitter-c/archive/refs/tags/v0.24.1.tar.gz",
    "25dd4bb3dec770769a407e0fc803f424ce02c494a56ce95fedc525316dcf9b48",
)
val treeRoot = treeSources.flatMap { it.outputDirectory.dir("tree-sitter-0.25.3") }
val grammarRoot = grammarSources.flatMap { it.outputDirectory.dir("tree-sitter-c-0.24.1") }

crossNative {
    libraryName.set("caelum_codegen_c")
    cSources.from(treeRoot.map { it.file("lib/src/lib.c") }, grammarRoot.map { it.file("src/parser.c") })
    includeDirs.from(treeRoot.map { it.dir("lib/include") }, treeRoot.map { it.dir("lib/src") }, grammarRoot.map { it.dir("src") })
    defines.put("_DEFAULT_SOURCE", "1")
}
// Existing Windows resources retain their existing optional WASM runtime.
// Linux builds just the native parser, so zstd and wasmtime are unnecessary.
tasks.named("compileNativeWindowsX64") { enabled = false }

dependencies {
    implementation(project(":codegen-c:adapter"))
}

tasks.jar {
    from(treeRoot.map { it.file("LICENSE") }) { rename { "LICENSE-tree-sitter" } }
    from(grammarRoot.map { it.file("LICENSE") }) { rename { "LICENSE-tree-sitter-c" } }
}
