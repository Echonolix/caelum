package net.echonolix.caelum.dxgi.win32

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/** A process-lifetime native DLL lookup backed by the JDK 24 FFM API. */
public class WindowsLibrary private constructor(
    public val name: String,
    private val symbols: SymbolLookup,
) {
    public fun findOrNull(symbol: String): MemorySegment? = symbols.find(symbol).orElse(null)

    public fun find(symbol: String): MemorySegment =
        findOrNull(symbol) ?: throw NoSuchElementException("$symbol was not exported by $name")

    public fun downcallOrNull(symbol: String, descriptor: FunctionDescriptor): MethodHandle? =
        findOrNull(symbol)?.let { LINKER.downcallHandle(it, descriptor) }

    public fun downcall(symbol: String, descriptor: FunctionDescriptor): MethodHandle =
        LINKER.downcallHandle(find(symbol), descriptor)

    public companion object {
        private val LINKER: Linker = Linker.nativeLinker()

        /** Whether the process matches the Windows x64 ABI implemented here. */
        public val isSupportedPlatform: Boolean
            get() = isWindowsX64(
                osName = System.getProperty("os.name").orEmpty(),
                architecture = System.getProperty("os.arch").orEmpty(),
                addressBytes = ValueLayout.ADDRESS.byteSize(),
            )

        internal fun isWindowsX64(
            osName: String,
            architecture: String,
            addressBytes: Long,
        ): Boolean {
            val x64 = architecture.equals("amd64", ignoreCase = true) ||
                architecture.equals("x86_64", ignoreCase = true)
            return osName.startsWith("Windows", ignoreCase = true) &&
                x64 && addressBytes == Long.SIZE_BYTES.toLong()
        }

        public fun open(name: String): WindowsLibrary {
            require(name.isNotBlank()) { "DLL name must not be blank" }
            val mappedName = if (name.endsWith(".dll", ignoreCase = true)) name else "$name.dll"
            return WindowsLibrary(mappedName, SymbolLookup.libraryLookup(mappedName, Arena.global()))
        }

        public fun openOrNull(name: String): WindowsLibrary? {
            require(name.isNotBlank()) { "DLL name must not be blank" }
            return try {
                open(name)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: UnsatisfiedLinkError) {
                null
            }
        }
    }
}
