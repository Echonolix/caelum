package net.echonolix.caelum.sdl3

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle
import java.nio.file.Path
import java.util.function.Supplier

/** Loads SDL3 and resolves its exported functions. */
public object SDL {
    private val loader: SDLLoader = SDLLoader(
        loadLibrary = System::loadLibrary,
        loadPath = System::load,
        lookupAfterLoad = SymbolLookup::loaderLookup,
    )

    /** Whether this process has successfully loaded SDL3 through this object. */
    @get:JvmStatic
    public val isLoaded: Boolean
        get() = loader.isLoaded

    /** Loads SDL3 using the platform library search path. */
    @JvmStatic
    public fun load() {
        requireSupportedPlatform()
        loader.loadLibrary()
    }

    /** Loads SDL3 from [path], converted to a normalized absolute path. */
    @JvmStatic
    public fun load(path: Path) {
        requireSupportedPlatform()
        loader.load(path)
    }

    /** Finds an exported SDL3 symbol after the library has been loaded. */
    @JvmStatic
    public fun findSymbol(name: String): MemorySegment = loader.findSymbol(name)

    /** Creates a downcall handle for an exported SDL3 function. */
    @JvmStatic
    public fun downcallHandle(name: String, descriptor: FunctionDescriptor): MethodHandle =
        Linker.nativeLinker().downcallHandle(findSymbol(name), descriptor)

    private fun requireSupportedPlatform() {
        val osName = System.getProperty("os.name", "")
        val osArch = System.getProperty("os.arch", "")
        check(isSupportedSDLPlatform(osName, osArch)) {
            "The generated SDL3 ABI supports Windows x64 only; current platform is '$osName/$osArch'."
        }
    }
}

internal class SDLLoader(
    private val loadLibrary: (String) -> Unit,
    private val loadPath: (String) -> Unit,
    private val lookupAfterLoad: () -> SymbolLookup,
) {
    private val lock = Any()

    @Volatile
    private var loaded: LoadedSDL? = null

    val isLoaded: Boolean
        get() = loaded != null

    fun loadLibrary() {
        load(LoadSource.LibraryName) {
            loadLibrary(SDL_LIBRARY_NAME)
        }
    }

    fun load(path: Path) {
        val normalizedPath = path.toAbsolutePath().normalize()
        load(LoadSource.AbsolutePath(normalizedPath)) {
            loadPath(normalizedPath.toString())
        }
    }

    fun findSymbol(name: String): MemorySegment {
        require(name.isNotBlank()) { "SDL3 symbol name must not be blank" }
        val lookup = synchronized(lock) {
            loaded?.lookup
                ?: throw IllegalStateException(
                    "SDL3 is not loaded. Call SDL.load() or SDL.load(path) before accessing SDL functions.",
                )
        }
        return lookup.find(name).orElseThrow(
            Supplier { UnsatisfiedLinkError("Unable to find SDL3 symbol '$name'") },
        )
    }

    private inline fun load(source: LoadSource, nativeLoad: () -> Unit) {
        synchronized(lock) {
            val existing = loaded
            if (existing != null) {
                check(existing.source == source) {
                    "SDL3 is already loaded ${existing.source.description}; " +
                        "it cannot be loaded again ${source.description}."
                }
                return
            }

            nativeLoad()
            loaded = LoadedSDL(source, lookupAfterLoad())
        }
    }
}

private data class LoadedSDL(
    val source: LoadSource,
    val lookup: SymbolLookup,
)

private sealed interface LoadSource {
    val description: String

    data object LibraryName : LoadSource {
        override val description: String = "from the platform library path as '$SDL_LIBRARY_NAME'"
    }

    data class AbsolutePath(val path: Path) : LoadSource {
        override val description: String = "from absolute path '$path'"
    }
}

private const val SDL_LIBRARY_NAME = "SDL3"
private val SUPPORTED_ARCHITECTURES: Set<String> = setOf("amd64", "x86_64")

internal fun isSupportedSDLPlatform(osName: String, osArch: String): Boolean =
    osName.startsWith("Windows", ignoreCase = true) && osArch.lowercase() in SUPPORTED_ARCHITECTURES
