package net.echonolix.caelum.dxgi.com

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.concurrent.atomic.AtomicReference

/** A borrowed COM pointer. Constructing one never calls `AddRef`. */
public open class ComRef<T : IUnknown> protected constructor(
    address: MemorySegment,
    public val type: ComInterface<T>,
) {
    private val borrowedAddress: MemorySegment = requireAddress(address, type)

    protected open fun currentAddress(): MemorySegment = borrowedAddress

    public val segment: MemorySegment get() = currentAddress()
    public val address: Long get() = currentAddress().address()

    public fun method(slot: Int, descriptor: FunctionDescriptor): MethodHandle =
        ComVTable.method(currentAddress(), type, slot, descriptor)

    override fun toString(): String = "${type.name}@0x${address.toString(16)}"

    public companion object {
        public fun <T : IUnknown> borrow(address: MemorySegment, type: ComInterface<T>): ComRef<T> =
            ComRef(address, type)

        internal fun <T : IUnknown> requireAddress(address: MemorySegment, type: ComInterface<T>): MemorySegment {
            require(address.address() != 0L) { "Cannot construct ${type.name} from a null COM pointer" }
            // Pointer values returned through a confined out-parameter must outlive
            // that temporary arena. COM governs the pointee lifetime via AddRef /
            // Release, so retain the numeric address in the global scope.
            return MemorySegment.ofAddress(address.address())
        }
    }
}

/**
 * A single owned COM reference.
 *
 * [adopt] consumes an already-owned native reference without calling `AddRef`.
 * [copy] is the only implicit-reference-counting operation and calls `AddRef`
 * exactly once. [close] calls `Release` at most once.
 */
public class ComPtr<T : IUnknown> private constructor(
    address: MemorySegment,
    type: ComInterface<T>,
) : ComRef<T>(address, type), AutoCloseable {
    private val ownedAddress: AtomicReference<MemorySegment> = AtomicReference(address)

    override fun currentAddress(): MemorySegment {
        val address = ownedAddress.get()
        check(address.address() != 0L) { "${type.name} pointer is closed" }
        return address
    }

    public fun copy(): ComPtr<T> = synchronized(this) {
        val address = currentAddress()
        addRef(address)
        adopt(address, type)
    }

    /**
     * Runs [block] while holding this owner's lifecycle lock. Use this for a
     * complete native call when another thread may call [close]; a raw
     * [segment] cannot by itself keep ownership alive after it escapes.
     */
    public fun <R> withAddress(block: (MemorySegment) -> R): R = synchronized(this) {
        block(currentAddress())
    }

    public fun <R : IUnknown> queryInterface(target: ComInterface<R>): ComPtr<R> =
        queryInterfaceOrNull(target) ?: throw ComException("${type.name}.QueryInterface(${target.name})", HResult.E_NOINTERFACE)

    public fun <R : IUnknown> queryInterfaceOrNull(target: ComInterface<R>): ComPtr<R>? = synchronized(this) {
        val address = currentAddress()
        Arena.ofConfined().use { arena ->
            val iid = target.iid.allocate(arena)
            val output = arena.allocate(ValueLayout.ADDRESS)
            output.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
            val result = HResult(queryInterfaceHandle(address).invokeExact(address, iid, output) as Int)
            if (result == HResult.E_NOINTERFACE) return@synchronized null
            result.check("${type.name}.QueryInterface(${target.name})")
            val queried = output.get(ValueLayout.ADDRESS, 0L)
            check(queried.address() != 0L) {
                "${type.name}.QueryInterface(${target.name}) succeeded but returned a null pointer"
            }
            adopt(queried, target)
        }
    }

    override fun close(): Unit = synchronized(this) {
        val address = ownedAddress.getAndSet(MemorySegment.NULL)
        if (address.address() != 0L) {
            releaseHandle(address).invokeExact(address) as Int
        }
    }

    private fun addRef(address: MemorySegment): Int = addRefHandle(address).invokeExact(address) as Int

    private fun queryInterfaceHandle(address: MemorySegment): MethodHandle =
        ComVTable.method(address, type, QUERY_INTERFACE_SLOT, QUERY_INTERFACE_DESCRIPTOR)

    private fun addRefHandle(address: MemorySegment): MethodHandle =
        ComVTable.method(address, type, ADD_REF_SLOT, REFERENCE_COUNT_DESCRIPTOR)

    private fun releaseHandle(address: MemorySegment): MethodHandle =
        ComVTable.method(address, type, RELEASE_SLOT, REFERENCE_COUNT_DESCRIPTOR)

    public companion object {
        public const val QUERY_INTERFACE_SLOT: Int = 0
        public const val ADD_REF_SLOT: Int = 1
        public const val RELEASE_SLOT: Int = 2

        public val QUERY_INTERFACE_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        )
        public val REFERENCE_COUNT_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )

        /** Adopts one native reference that the caller already owns. */
        public fun <T : IUnknown> adopt(address: MemorySegment, type: ComInterface<T>): ComPtr<T> =
            ComPtr(ComRef.requireAddress(address, type), type)
    }
}
