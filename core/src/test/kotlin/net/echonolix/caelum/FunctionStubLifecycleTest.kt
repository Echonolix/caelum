package net.echonolix.caelum

import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FunctionStubLifecycleTest {
    interface Callback : NFunction {
        fun invokeNative(value: Int): Int
    }

    private class Manager : NFunction.Manager.Impl()

    private class Descriptor(owner: NFunction.Manager) : NFunction.Descriptor<Callback>(
        "test callback", Callback::class.java, NInt, NInt
    ) {
        override val manager: NFunction.Manager = owner

        override fun fromNativeData(value: MemorySegment): Callback = error("Not used by this test")
    }

    @Test
    fun `cache isolates managers descriptors and equal callback objects`() {
        val firstManager = Manager()
        val secondManager = Manager()
        val first = Descriptor(firstManager)
        val second = Descriptor(firstManager)
        val otherManager = Descriptor(secondManager)
        val callback = EqualCallback(first)
        val equalCallback = EqualCallback(first)
        try {
            val address = first.toNativeData(callback)
            assertEquals(address, first.toNativeData(callback))
            assertNotEquals(address, first.toNativeData(equalCallback))
            assertNotEquals(address, second.toNativeData(callback))
            assertNotEquals(address, otherManager.toNativeData(callback))
            val survivingStub = otherManager.toPointer(callback)
            val firstLifetime = firstManager.stubAllocator
            firstManager.freeFunctionStubs()
            assertFalse(firstLifetime.scope().isAlive)
            assertTrue(secondManager.stubAllocator.scope().isAlive)
            assertEquals(survivingStub._address, otherManager.toNativeData(callback))
            assertEquals(42, invoke(otherManager, survivingStub._address, 41))
            assertEquals(42, invoke(first, first.toNativeData(callback), 41))
        } finally {
            firstManager.freeFunctionStubs()
            secondManager.freeFunctionStubs()
        }
    }

    private fun invoke(descriptor: Descriptor, address: Long, value: Int): Int =
        Linker.nativeLinker().downcallHandle(MemorySegment.ofAddress(address), descriptor.functionDescriptor)
            .invokeWithArguments(value) as Int

    private class EqualCallback(override val typeDescriptor: Descriptor) : Callback {
        override fun invokeNative(value: Int): Int = value + 1

        override fun equals(other: Any?): Boolean = other is EqualCallback

        override fun hashCode(): Int = 0
    }
}
