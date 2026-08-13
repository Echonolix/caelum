package net.echonolix.caelum.assimp

import net.echonolix.caelum.assimp.functions.aiGetCompileFlags
import net.echonolix.caelum.assimp.functions.aiGetVersionMajor
import net.echonolix.caelum.assimp.functions.aiGetVersionMinor
import net.echonolix.caelum.assimp.functions.aiGetVersionPatch
import java.lang.foreign.ValueLayout
import java.nio.file.Path

/** Loading and ABI compatibility checks for the generated Assimp 6.0.4 binding. */
public object Assimp {
    public const val ABI_VERSION_MAJOR: Int = 6
    public const val ABI_VERSION_MINOR: Int = 0
    public const val ABI_VERSION_PATCH: Int = 4
    public const val COMPILE_FLAG_DOUBLE_SUPPORT: Int = 0x20

    private var loaded: Boolean = false

    /** Loads an Assimp shared library by absolute path and validates its ABI. */
    @Synchronized
    public fun load(path: Path) {
        check(!loaded) { "Assimp has already been loaded" }
        require(path.isAbsolute) { "Assimp library path must be absolute: $path" }
        require(ValueLayout.ADDRESS.byteSize() == Long.SIZE_BYTES.toLong()) {
            "caelum-assimp currently supports only 64-bit processes"
        }
        System.load(path.toString())
        validateLoadedLibrary()
        loaded = true
    }

    /** Validates an Assimp library that the application loaded itself. */
    @Synchronized
    public fun validateLoadedLibrary() {
        val actual = Triple(aiGetVersionMajor(), aiGetVersionMinor(), aiGetVersionPatch())
        require(actual == Triple(ABI_VERSION_MAJOR.toUInt(), ABI_VERSION_MINOR.toUInt(), ABI_VERSION_PATCH.toUInt())) {
            "Expected Assimp 6.0.4 ABI, found ${actual.first}.${actual.second}.${actual.third}"
        }
        val compileFlags = aiGetCompileFlags()
        require(compileFlags and COMPILE_FLAG_DOUBLE_SUPPORT.toUInt() == 0U) {
            "This binding uses Assimp's single-precision ABI, but the loaded library uses double precision"
        }
        loaded = true
    }

    public fun isLoaded(): Boolean = loaded
}
