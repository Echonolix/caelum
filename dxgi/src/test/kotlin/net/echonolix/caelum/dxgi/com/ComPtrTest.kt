package net.echonolix.caelum.dxgi.com

import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ComPtrTest {
    private interface IFake : IUnknown

    private val fakeType = ComInterface<IFake>(
        name = "IFake",
        iid = Guid.parse("62af214d-5c5a-4ece-9a26-2a456c8761e7"),
        vtableSize = 3,
    )

    @Test
    fun copyAndCloseApplyExactlyOneAddRefAndAtMostOneRelease() {
        Arena.ofConfined().use { arena ->
            val fake = FakeUnknown(arena, fakeType.iid)
            val original = ComPtr.adopt(fake.instance, fakeType)
            val copy = original.copy()
            assertEquals(1, fake.addRefs.get())

            copy.close()
            copy.close()
            assertEquals(1, fake.releases.get())

            original.close()
            original.close()
            assertEquals(2, fake.releases.get())
            assertFailsWith<IllegalStateException> { original.segment }
        }
    }

    @Test
    fun queryInterfaceAdoptsOnlySuccessfulNonNullResults() {
        Arena.ofConfined().use { arena ->
            val fake = FakeUnknown(arena, fakeType.iid)
            ComPtr.adopt(fake.instance, fakeType).use { original ->
                original.queryInterface(fakeType).use { queried ->
                    assertEquals(fake.instance.address(), queried.address)
                    assertEquals(1, fake.queryInterfaceAddRefs.get())
                }
                assertEquals(1, fake.releases.get())

                val unsupported = ComInterface<IFake>(
                    name = "IOther",
                    iid = Guid.parse("deef2f8e-6e4f-430d-a8a1-97da73ec009f"),
                    vtableSize = 3,
                )
                assertNull(original.queryInterfaceOrNull(unsupported))
                assertEquals(1, fake.queryInterfaceAddRefs.get())
                assertEquals(1, fake.releases.get())
            }
            assertEquals(2, fake.releases.get())
        }
    }

    private class FakeUnknown(
        arena: Arena,
        private val supportedIid: Guid,
    ) {
        val addRefs: AtomicInteger = AtomicInteger()
        val queryInterfaceAddRefs: AtomicInteger = AtomicInteger()
        val releases: AtomicInteger = AtomicInteger()

        val instance: MemorySegment

        init {
            val lookup = MethodHandles.lookup()
            val query = lookup.findVirtual(
                FakeUnknown::class.java,
                "queryInterface",
                MethodType.methodType(
                    Integer.TYPE,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            ).bindTo(this)
            val addRef = lookup.findVirtual(
                FakeUnknown::class.java,
                "addRef",
                MethodType.methodType(Integer.TYPE, MemorySegment::class.java),
            ).bindTo(this)
            val release = lookup.findVirtual(
                FakeUnknown::class.java,
                "release",
                MethodType.methodType(Integer.TYPE, MemorySegment::class.java),
            ).bindTo(this)

            val linker = Linker.nativeLinker()
            val vtable = arena.allocate(3L * ValueLayout.ADDRESS.byteSize(), ValueLayout.ADDRESS.byteAlignment())
            vtable.set(ValueLayout.ADDRESS, 0L, linker.upcallStub(query, ComPtr.QUERY_INTERFACE_DESCRIPTOR, arena))
            vtable.set(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS.byteSize(),
                linker.upcallStub(addRef, ComPtr.REFERENCE_COUNT_DESCRIPTOR, arena),
            )
            vtable.set(
                ValueLayout.ADDRESS,
                2L * ValueLayout.ADDRESS.byteSize(),
                linker.upcallStub(release, ComPtr.REFERENCE_COUNT_DESCRIPTOR, arena),
            )
            instance = arena.allocate(ValueLayout.ADDRESS)
            instance.set(ValueLayout.ADDRESS, 0L, vtable)
        }

        @Suppress("unused")
        fun queryInterface(self: MemorySegment, iid: MemorySegment, output: MemorySegment): Int {
            check(self.address() == instance.address())
            val requested = Guid.read(iid.reinterpret(Guid.BYTE_SIZE))
            val outputCell = output.reinterpret(ValueLayout.ADDRESS.byteSize())
            return if (requested == supportedIid || requested == ComInterfaces.IUNKNOWN.iid) {
                outputCell.set(ValueLayout.ADDRESS, 0L, instance)
                queryInterfaceAddRefs.incrementAndGet()
                HResult.S_OK.value
            } else {
                outputCell.set(ValueLayout.ADDRESS, 0L, MemorySegment.NULL)
                HResult.E_NOINTERFACE.value
            }
        }

        @Suppress("unused")
        fun addRef(self: MemorySegment): Int {
            check(self.address() == instance.address())
            return addRefs.incrementAndGet()
        }

        @Suppress("unused")
        fun release(self: MemorySegment): Int {
            check(self.address() == instance.address())
            return releases.incrementAndGet()
        }
    }
}
