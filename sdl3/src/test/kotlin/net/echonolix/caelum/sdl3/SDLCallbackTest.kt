package net.echonolix.caelum.sdl3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.sdl3.functions.SDL_EventFilter
import net.echonolix.caelum.sdl3.functions.SDLFunction
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SDLCallbackTest {
    @Test
    fun `all public callback typedefs are generated deterministically`() {
        assertEquals(40, SDLCallbacks.names.size)
        assertEquals(SDLCallbacks.names.sorted(), SDLCallbacks.names)
        assertEquals(SDLCallbacks.names.size, SDLCallbacks.names.distinct().size)
        assertTrue(SDLCallbacks.unsupported.isEmpty())
        assertEquals(2, SDLCallbacks.erasures.size)
        assertEquals(
            setOf("SDL_WindowsMessageHook.msg" to "MSG *", "SDL_X11EventHook.xevent" to "XEvent *"),
            SDLCallbacks.erasures.mapTo(mutableSetOf()) { "${it.callbackName}.${it.parameterName}" to it.nativeType },
        )
        assertTrue(SDLCallbacks.erasures.all { it.exposedType == "NPointer<*>" })
        assertTrue("SDL_EventFilter" in SDLCallbacks.names)
        assertTrue("SDL_WindowsMessageHook" in SDLCallbacks.names)
        assertTrue("SDL_X11EventHook" in SDLCallbacks.names)
    }

    @Test
    fun `event filter upcall stub can be invoked through a native downcall wrapper`() {
        var receivedUserdata = 0L
        var receivedEvent = 0L
        val callback = SDL_EventFilter { userdata, event ->
            receivedUserdata = userdata._address
            receivedEvent = event._address
            userdata._address != 0L && event._address != 0L
        }

        try {
            val stub = SDL_EventFilter.toPointer(callback)
            val nativeCallback = SDL_EventFilter.fromNativeData(stub._address)

            assertTrue(nativeCallback(NPointer<NChar>(0x1234L), NPointer<SDL_Event>(0x5678L)))
            assertEquals(0x1234L, receivedUserdata)
            assertEquals(0x5678L, receivedEvent)
            assertFalse(nativeCallback(NPointer<NChar>(0L), NPointer<SDL_Event>(0x5678L)))
        } finally {
            SDLFunction.freeFunctionStubs()
        }
    }

    @Test
    fun `the same callback object keeps a stable native identity`() {
        val callback = SDL_EventFilter { _, _ -> true }

        try {
            val first = SDL_EventFilter.toPointer(callback)
            val second = SDL_EventFilter.toPointer(callback)

            assertEquals(first._address, second._address)
        } finally {
            SDLFunction.freeFunctionStubs()
        }
    }

    @Test
    fun `different callback objects keep independent native identities`() {
        val firstCallback = EqualEventFilter()
        val secondCallback = EqualEventFilter()

        try {
            val first = SDL_EventFilter.toPointer(firstCallback)
            val second = SDL_EventFilter.toPointer(secondCallback)

            assertNotEquals(first._address, second._address)
        } finally {
            SDLFunction.freeFunctionStubs()
        }
    }

    @Test
    fun `concurrent conversion creates one stub for a callback object`() {
        val callback = SDL_EventFilter { _, _ -> true }
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        try {
            val futures = List(8) {
                executor.submit<Long> {
                    ready.countDown()
                    start.await()
                    SDL_EventFilter.toPointer(callback)._address
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            val addresses = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, addresses.distinct().size)
        } finally {
            executor.shutdownNow()
            SDLFunction.freeFunctionStubs()
        }
    }

    @Test
    fun `freeing callback stubs closes old lifetime and rebuilds the callback`() {
        val callback = SDL_EventFilter { _, _ -> true }
        val oldAllocator = SDLFunction.stubAllocator
        SDL_EventFilter.toPointer(callback)

        SDLFunction.freeFunctionStubs()
        assertFalse(oldAllocator.scope().isAlive)
        assertTrue(oldAllocator !== SDLFunction.stubAllocator)

        try {
            val second = SDL_EventFilter.toPointer(callback)
            assertTrue(
                SDL_EventFilter.fromNativeData(second._address)(
                    NPointer<NChar>(1L),
                    NPointer<SDL_Event>(2L),
                ),
            )
        } finally {
            SDLFunction.freeFunctionStubs()
        }
    }

    private class EqualEventFilter : SDL_EventFilter {
        override fun invoke(userdata: NPointer<*>, event: NPointer<SDL_Event>): Boolean = true

        override fun equals(other: Any?): Boolean = other is EqualEventFilter

        override fun hashCode(): Int = 0
    }
}

// These declarations are never called. Their compilation protects both raw callback pointers and
// direct Kotlin callback overloads, while callback** remains an explicit output-pointer API.
@Suppress("UNUSED_VARIABLE")
private fun verifyCallbackBindingSignatures() {
    val rawAdd: (NPointer<SDL_EventFilter>, NPointer<*>) -> Boolean = ::SDL_AddEventWatch
    val directAdd: (SDL_EventFilter, NPointer<*>) -> Boolean = ::SDL_AddEventWatch
    val rawSet: (NPointer<SDL_EventFilter>, NPointer<*>) -> Unit = ::SDL_SetEventFilter
    val directSet: (SDL_EventFilter, NPointer<*>) -> Unit = ::SDL_SetEventFilter
    val get: (NPointer<NPointer<SDL_EventFilter>>, NPointer<NPointer<*>>) -> Boolean = ::SDL_GetEventFilter
}
