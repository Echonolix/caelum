package net.echonolix.caelum.mimalloc

import net.echonolix.caelum.NativeLibraries
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.mimalloc.functions.*

/** Explicit native allocation; does not replace the process CRT or install allocation hooks. */
public object Mimalloc {
    public const val VERSION: Int = 20502

    init {
        NativeLibraries.load(Mimalloc::class.java, "caelum_mimalloc")
        check(mi_version() == VERSION) { "caelum_mimalloc native version does not match $VERSION" }
    }

    /** Uninitialized storage. Caller owns the address and must [free] it exactly once. */
    @JvmStatic
    public fun malloc(byteSize: Long, alignment: Long = 8): Long {
        assert(byteSize >= 0 && alignment > 0 && alignment and (alignment - 1) == 0L)
        val address = mi_malloc_aligned(byteSize, alignment)._address
        if (address == 0L) throw OutOfMemoryError("mimalloc could not allocate $byteSize bytes")
        return address
    }

    /** Zero-initialized storage with the same ownership as [malloc]. */
    @JvmStatic
    public fun calloc(byteSize: Long, alignment: Long = 8): Long {
        assert(byteSize >= 0 && alignment > 0 && alignment and (alignment - 1) == 0L)
        val address = mi_zalloc_aligned(byteSize, alignment)._address
        if (address == 0L) throw OutOfMemoryError("mimalloc could not allocate $byteSize bytes")
        return address
    }

    /** May run on any thread, after every reader has finished. Address must come from this allocator. */
    @JvmStatic
    public fun free(address: Long): Unit = mi_free(NPointer<NChar>(address))

    /** Actual usable block size, for diagnostics; the allocation must still be live. */
    @JvmStatic
    public fun usableSize(address: Long): Long = mi_usable_size(NPointer<NChar>(address))

    /** Collect the calling thread's unused native pages; not required for correctness or per-free use. */
    @JvmStatic
    public fun collect(force: Boolean = false): Unit = mi_collect(if (force) 1u else 0u)
}
