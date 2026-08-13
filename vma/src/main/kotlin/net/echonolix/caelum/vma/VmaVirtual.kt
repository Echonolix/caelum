package net.echonolix.caelum.vma

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/**
 * Complete low-level binding to VMA 3.4.0's GPU-independent virtual allocator.
 *
 * Pointer and handle values use [Long] to match Caelum's existing native pointer
 * representation. A loaded instance owns its library lookup arena and should be closed.
 */
public class VmaVirtual private constructor(
    private val arena: Arena,
    private val lookup: SymbolLookup,
) : AutoCloseable {
    init {
        require64BitAddressSpace()
    }

    private val linker = Linker.nativeLinker()
    private val pointer = ValueLayout.JAVA_LONG
    private val int32 = ValueLayout.JAVA_INT

    private fun function(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(
            lookup.find(name).orElseThrow { UnsatisfiedLinkError("VMA library does not export $name") },
            descriptor
        )

    private val createVirtualBlock = function("vmaCreateVirtualBlock", FunctionDescriptor.of(int32, pointer, pointer))
    private val destroyVirtualBlock = function("vmaDestroyVirtualBlock", FunctionDescriptor.ofVoid(pointer))
    private val isVirtualBlockEmpty = function("vmaIsVirtualBlockEmpty", FunctionDescriptor.of(int32, pointer))
    private val getVirtualAllocationInfo = function("vmaGetVirtualAllocationInfo", FunctionDescriptor.ofVoid(pointer, pointer, pointer))
    private val virtualAllocate = function("vmaVirtualAllocate", FunctionDescriptor.of(int32, pointer, pointer, pointer, pointer))
    private val virtualFree = function("vmaVirtualFree", FunctionDescriptor.ofVoid(pointer, pointer))
    private val clearVirtualBlock = function("vmaClearVirtualBlock", FunctionDescriptor.ofVoid(pointer))
    private val setVirtualAllocationUserData = function("vmaSetVirtualAllocationUserData", FunctionDescriptor.ofVoid(pointer, pointer, pointer))
    private val getVirtualBlockStatistics = function("vmaGetVirtualBlockStatistics", FunctionDescriptor.ofVoid(pointer, pointer))
    private val calculateVirtualBlockStatistics = function("vmaCalculateVirtualBlockStatistics", FunctionDescriptor.ofVoid(pointer, pointer))
    private val buildVirtualBlockStatsString = function("vmaBuildVirtualBlockStatsString", FunctionDescriptor.ofVoid(pointer, pointer, int32))
    private val freeVirtualBlockStatsString = function("vmaFreeVirtualBlockStatsString", FunctionDescriptor.ofVoid(pointer, pointer))

    public fun createVirtualBlock(createInfo: MemorySegment, outputBlock: MemorySegment): Int =
        createVirtualBlock.invokeExact(createInfo.address(), outputBlock.address()) as Int

    public fun destroyVirtualBlock(block: Long): Unit { destroyVirtualBlock.invokeExact(block) }
    public fun isVirtualBlockEmpty(block: Long): Boolean = (isVirtualBlockEmpty.invokeExact(block) as Int) != 0
    public fun getVirtualAllocationInfo(block: Long, allocation: Long, outputInfo: MemorySegment): Unit =
        run { getVirtualAllocationInfo.invokeExact(block, allocation, outputInfo.address()); Unit }

    public fun virtualAllocate(block: Long, createInfo: MemorySegment, outputAllocation: MemorySegment, outputOffset: MemorySegment): Int =
        virtualAllocate.invokeExact(block, createInfo.address(), outputAllocation.address(), outputOffset.address()) as Int

    public fun virtualFree(block: Long, allocation: Long): Unit { virtualFree.invokeExact(block, allocation) }
    public fun clearVirtualBlock(block: Long): Unit { clearVirtualBlock.invokeExact(block) }
    public fun setVirtualAllocationUserData(block: Long, allocation: Long, userData: Long): Unit =
        run { setVirtualAllocationUserData.invokeExact(block, allocation, userData); Unit }
    public fun getVirtualBlockStatistics(block: Long, outputStatistics: MemorySegment): Unit =
        run { getVirtualBlockStatistics.invokeExact(block, outputStatistics.address()); Unit }
    public fun calculateVirtualBlockStatistics(block: Long, outputStatistics: MemorySegment): Unit =
        run { calculateVirtualBlockStatistics.invokeExact(block, outputStatistics.address()); Unit }
    public fun buildVirtualBlockStatsString(block: Long, outputString: MemorySegment, detailedMap: Boolean): Unit =
        run { buildVirtualBlockStatsString.invokeExact(block, outputString.address(), if (detailedMap) 1 else 0); Unit }
    public fun freeVirtualBlockStatsString(block: Long, stringAddress: Long): Unit =
        run { freeVirtualBlockStatsString.invokeExact(block, stringAddress); Unit }

    override fun close(): Unit = arena.close()

    public companion object {
        private const val NATIVE_RESOURCE_ROOT: String = "META-INF/caelum/native"
        private const val CACHE_DIRECTORY: String = "caelum/vma"

        /** Opens exactly [library], keeping symbol resolution scoped to that file. */
        @JvmStatic public fun load(library: Path): VmaVirtual {
            require64BitAddressSpace()
            val arena = Arena.ofShared()
            return try {
                VmaVirtual(arena, SymbolLookup.libraryLookup(library, arena))
            } catch (failure: Throwable) {
                arena.close()
                throw failure
            }
        }

        /** Opens a platform library name with a library-scoped lookup. */
        @JvmStatic public fun load(libraryName: String): VmaVirtual {
            require64BitAddressSpace()
            val arena = Arena.ofShared()
            return try {
                VmaVirtual(arena, SymbolLookup.libraryLookup(libraryName, arena))
            } catch (failure: Throwable) {
                arena.close()
                throw failure
            }
        }

        /**
         * Extracts the native library packaged for this host from the class path,
         * verifies the copied bytes, and opens it with a library-scoped lookup.
         *
         * Artifacts are cached below the JVM temporary directory by their SHA-256
         * value. The cache is safe to reuse across class loaders and JVM runs: an
         * existing file is reused only after its digest has been checked. A corrupt
         * or concurrently-created cache entry is never replaced: a private,
         * create-new candidate is used instead.
         *
         * Only the platform binary embedded in this JAR can be loaded. Publish one
         * artifact per target platform, or use [load] to supply an explicit binary.
         */
        @JvmStatic public fun loadBundled(): VmaVirtual {
            require64BitAddressSpace()
            val arena = Arena.ofShared()
            return try {
                VmaVirtual(arena, bundledLibraryLookup(arena))
            } catch (failure: Throwable) {
                arena.close()
                throw failure
            }
        }

        /**
         * Returns the verified bundled artifact path for diagnostics and callers
         * which need to inspect it. Loading code should use [bundledLibraryLookup]
         * so verification happens directly before `libraryLookup`.
         */
        internal fun extractBundledLibrary(): Path = extractBundledLibraryWithDigest().path

        /** Resolves the bundled native lookup after an immediate no-follow digest check. */
        internal fun bundledLibraryLookup(arena: Arena): SymbolLookup {
            val bundled = extractBundledLibraryWithDigest()
            if (!isVerifiedRegularFile(bundled.path, bundled.digest)) {
                throw SecurityException("Bundled VMA library changed before it could be loaded: ${bundled.path}")
            }
            return SymbolLookup.libraryLookup(bundled.path, arena)
        }

        private fun extractBundledLibraryWithDigest(): BundledLibrary = synchronized(VmaVirtual::class.java) {
            require64BitAddressSpace()
            val fileName = bundledLibraryFileName()
            val resourcePath = "$NATIVE_RESOURCE_ROOT/${hostPlatformDirectory()}/$fileName"
            val stream = VmaVirtual::class.java.classLoader
                ?.getResourceAsStream(resourcePath)
                ?: VmaVirtual::class.java.getResourceAsStream("/$resourcePath")
                ?: throw UnsatisfiedLinkError(
                    "No bundled Caelum VMA library for ${hostPlatformDirectory()} at $resourcePath. " +
                        "Use VmaVirtual.load(Path) or consume an artifact built for this platform.",
                )

            val temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
            if (!Files.isDirectory(temporaryDirectory, NOFOLLOW_LINKS)) {
                throw SecurityException("JVM temporary directory is not a real directory: $temporaryDirectory")
            }
            val cacheRoot = ensureChildDirectory(
                ensureChildDirectory(temporaryDirectory, "caelum"),
                "vma",
            )
            val stagedLibrary = Files.createTempFile(cacheRoot, "caelum-vma-", ".part")
            try {
                val digest = copyAndDigest(stream, stagedLibrary)
                val contentDirectory = ensureChildDirectory(cacheRoot, digest)
                val primary = contentDirectory.resolve(fileName)
                if (isVerifiedRegularFile(primary, digest)) {
                    return@synchronized BundledLibrary(primary, digest)
                }

                // Do not use Files.move here. Java's ATOMIC_MOVE does not give
                // a portable no-replace guarantee. First publish the stable
                // digest cache name exclusively; a successful first writer
                // enables reuse across future calls and JVMs. If another writer
                // has occupied that name with invalid or partial content, leave
                // it untouched and fall back to a private CREATE_NEW candidate.
                BundledLibrary(publishStagedLibrary(stagedLibrary, contentDirectory, fileName, digest), digest)
            } finally {
                Files.deleteIfExists(stagedLibrary)
            }
        }

        /**
         * Publishes [stagedLibrary] through an exclusive CREATE_NEW open. First
         * attempts the stable [fileName] cache path; an existing valid primary
         * is reused, while an invalid/partial entry is left untouched and causes
         * a fallback to a randomized private candidate.
         */
        internal fun publishStagedLibrary(
            stagedLibrary: Path,
            contentDirectory: Path,
            fileName: String,
            expectedDigest: String,
        ): Path {
            require(Files.isRegularFile(stagedLibrary, NOFOLLOW_LINKS)) {
                "Staged VMA library is not a regular file: $stagedLibrary"
            }
            require(Files.isDirectory(contentDirectory, NOFOLLOW_LINKS) && !Files.isSymbolicLink(contentDirectory)) {
                "VMA cache content directory is not a real directory: $contentDirectory"
            }
            val primary = contentDirectory.resolve(fileName)
            try {
                return publishCreateNew(stagedLibrary, primary, expectedDigest)
            } catch (_: FileAlreadyExistsException) {
                if (isVerifiedRegularFile(primary, expectedDigest)) return primary
            } catch (_: SecurityException) {
                // The primary was changed or remains invalid after our
                // exclusive write. Leave it untouched and use a private name.
            }

            val separator = fileName.lastIndexOf('.')
            val stem = if (separator > 0) fileName.substring(0, separator) else fileName
            val suffix = if (separator > 0) fileName.substring(separator) else ""
            while (true) {
                val candidate = contentDirectory.resolve(
                    "$stem-${ProcessHandle.current().pid()}-${UUID.randomUUID()}$suffix",
                )
                try {
                    return publishCreateNew(stagedLibrary, candidate, expectedDigest)
                } catch (_: FileAlreadyExistsException) {
                    // UUID collision is exceptionally unlikely but harmless:
                    // choose another candidate without touching the existing one.
                }
            }
        }

        private fun publishCreateNew(
            stagedLibrary: Path,
            candidate: Path,
            expectedDigest: String,
        ): Path {
            try {
                java.nio.channels.FileChannel.open(candidate, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { destination ->
                    Files.newByteChannel(stagedLibrary, setOf(READ, NOFOLLOW_LINKS)).use { source ->
                        val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
                        while (source.read(buffer) >= 0) {
                            buffer.flip()
                            while (buffer.hasRemaining()) destination.write(buffer)
                            buffer.clear()
                        }
                    }
                    destination.force(true)
                }
                if (!isVerifiedRegularFile(candidate, expectedDigest)) {
                    throw SecurityException("Published VMA library did not pass SHA-256 verification: $candidate")
                }
                return candidate
            } catch (failure: Throwable) {
                // Do not delete a failed candidate. Once an entry is visible in
                // a shared temp directory, a same-privilege process could
                // replace it before cleanup; deletion could then affect a file
                // not created by this invocation. The randomized CREATE_NEW
                // name is never reused as a cache hit, so a partial file is
                // harmless and can never be loaded.
                throw failure
            }
        }

        private fun copyAndDigest(input: InputStream, destination: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
                DigestInputStream(source, digest).use { digested ->
                    Files.newOutputStream(destination).use { output -> digested.copyTo(output) }
                }
            }
            return HexFormat.of().formatHex(digest.digest())
        }

        private fun isVerifiedRegularFile(path: Path, expectedDigest: String): Boolean =
            Files.isRegularFile(path, NOFOLLOW_LINKS) && sha256(path) == expectedDigest

        private fun sha256(path: Path): String =
            Files.newByteChannel(path, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                val digest = MessageDigest.getInstance("SHA-256")
                java.nio.channels.Channels.newInputStream(channel).use { input ->
                    DigestInputStream(input, digest).use { it.copyTo(java.io.OutputStream.nullOutputStream()) }
                }
                HexFormat.of().formatHex(digest.digest())
            }

        private fun ensureChildDirectory(parent: Path, name: String): Path {
            val child = parent.resolve(name)
            try {
                Files.createDirectory(child)
            } catch (_: FileAlreadyExistsException) {
                // The postcondition below verifies a pre-existing entry is a
                // real directory rather than accepting a link or ordinary file.
            }
            if (!Files.isDirectory(child, NOFOLLOW_LINKS) || Files.isSymbolicLink(child)) {
                throw SecurityException("VMA native cache directory is not a real directory: $child")
            }
            return child
        }

        private data class BundledLibrary(val path: Path, val digest: String)

        private fun hostPlatformDirectory(): String = "${hostOperatingSystem()}-${hostArchitecture()}"

        private fun hostOperatingSystem(): String = when {
            System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows"
            System.getProperty("os.name").startsWith("Mac", ignoreCase = true) ||
                System.getProperty("os.name").startsWith("Darwin", ignoreCase = true) -> "macos"
            System.getProperty("os.name").startsWith("Linux", ignoreCase = true) -> "linux"
            else -> System.getProperty("os.name").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }

        private fun hostArchitecture(): String = when (System.getProperty("os.arch").lowercase()) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> System.getProperty("os.arch").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }

        private fun bundledLibraryFileName(): String = when (hostOperatingSystem()) {
            "windows" -> "caelum_vma.dll"
            "macos" -> "libcaelum_vma.dylib"
            else -> "libcaelum_vma.so"
        }

        private fun librarySuffix(): String = when (hostOperatingSystem()) {
            "windows" -> ".dll"
            "macos" -> ".dylib"
            else -> ".so"
        }

        private fun require64BitAddressSpace(): Unit {
            check(ValueLayout.ADDRESS.byteSize() == Long.SIZE_BYTES.toLong()) {
                "Caelum VMA currently supports only 64-bit address spaces; found ${ValueLayout.ADDRESS.byteSize() * 8}-bit pointers."
            }
        }
    }
}
