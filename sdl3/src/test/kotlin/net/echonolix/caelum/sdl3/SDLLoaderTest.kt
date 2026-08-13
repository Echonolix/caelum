package net.echonolix.caelum.sdl3

import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SDLLoaderTest {
    @Test
    fun `generated ABI is limited to Windows x64`() {
        assertTrue(isSupportedSDLPlatform("Windows 11", "amd64"))
        assertTrue(isSupportedSDLPlatform("windows server", "x86_64"))
        assertFalse(isSupportedSDLPlatform("Windows 11", "aarch64"))
        assertFalse(isSupportedSDLPlatform("Linux", "amd64"))
    }

    @Test
    fun `symbol resolution requires a successful load`() {
        val loader = testLoader()

        val failure = assertFailsWith<IllegalStateException> {
            loader.findSymbol("SDL_GetVersion")
        }

        assertContains(failure.message.orEmpty(), "SDL3 is not loaded")
        assertContains(failure.message.orEmpty(), "SDL.load()")
        assertFalse(loader.isLoaded)
    }

    @Test
    fun `library-name loading is idempotent`() {
        val loadedNames = mutableListOf<String>()
        var lookups = 0
        val loader = testLoader(
            loadLibrary = loadedNames::add,
            lookupAfterLoad = {
                lookups++
                EMPTY_LOOKUP
            },
        )

        loader.loadLibrary()
        loader.loadLibrary()

        assertEquals(listOf("SDL3"), loadedNames)
        assertEquals(1, lookups)
        assertTrue(loader.isLoaded)
    }

    @Test
    fun `path loading uses a normalized absolute path and is idempotent`() {
        val loadedPaths = mutableListOf<String>()
        val loader = testLoader(loadPath = loadedPaths::add)
        val requested = Path.of("build", "native", "..", "native", "SDL3.dll")
        val normalized = requested.toAbsolutePath().normalize()

        loader.load(requested)
        loader.load(normalized)

        assertEquals(listOf(normalized.toString()), loadedPaths)
        assertTrue(normalized.isAbsolute)
    }

    @Test
    fun `a different load source is rejected clearly`() {
        val firstPath = Path.of("build", "first", "SDL3.dll").toAbsolutePath().normalize()
        val secondPath = Path.of("build", "second", "SDL3.dll").toAbsolutePath().normalize()
        val loader = testLoader()
        loader.load(firstPath)

        val differentPathFailure = assertFailsWith<IllegalStateException> {
            loader.load(secondPath)
        }
        val libraryNameFailure = assertFailsWith<IllegalStateException> {
            loader.loadLibrary()
        }

        assertContains(differentPathFailure.message.orEmpty(), firstPath.toString())
        assertContains(differentPathFailure.message.orEmpty(), secondPath.toString())
        assertContains(differentPathFailure.message.orEmpty(), "already loaded")
        assertContains(libraryNameFailure.message.orEmpty(), firstPath.toString())
        assertContains(libraryNameFailure.message.orEmpty(), "platform library path")
    }

    @Test
    fun `failed native loading leaves the loader retryable`() {
        var attempts = 0
        val loader = testLoader(
            loadLibrary = {
                attempts++
                if (attempts == 1) throw UnsatisfiedLinkError("not found")
            },
        )

        assertFailsWith<UnsatisfiedLinkError> {
            loader.loadLibrary()
        }
        assertFalse(loader.isLoaded)

        loader.loadLibrary()

        assertEquals(2, attempts)
        assertTrue(loader.isLoaded)
    }

    @Test
    fun `lookup is created after native loading and used afterward`() {
        val events = mutableListOf<String>()
        val expected = MemorySegment.ofAddress(0x5DL)
        val loader = testLoader(
            loadLibrary = { events += "load:$it" },
            lookupAfterLoad = {
                events += "lookup"
                SymbolLookup { name ->
                    events += "find:$name"
                    if (name == "SDL_GetVersion") java.util.Optional.of(expected) else java.util.Optional.empty()
                }
            },
        )

        loader.loadLibrary()
        val actual = loader.findSymbol("SDL_GetVersion")

        assertSame(expected, actual)
        assertEquals(listOf("load:SDL3", "lookup", "find:SDL_GetVersion"), events)
    }

    @Test
    fun `missing and blank symbols have useful failures`() {
        val loader = testLoader()
        loader.loadLibrary()

        val missing = assertFailsWith<UnsatisfiedLinkError> {
            loader.findSymbol("SDL_NotAnExport")
        }
        val blank = assertFailsWith<IllegalArgumentException> {
            loader.findSymbol("  ")
        }

        assertContains(missing.message.orEmpty(), "SDL_NotAnExport")
        assertContains(blank.message.orEmpty(), "must not be blank")
    }

    @Test
    fun `concurrent loads perform one native load`() {
        val nativeLoads = AtomicInteger()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val loader = testLoader(
            loadLibrary = {
                nativeLoads.incrementAndGet()
                Thread.yield()
            },
        )
        val executor = Executors.newFixedThreadPool(8)

        try {
            val futures = List(8) {
                executor.submit {
                    ready.countDown()
                    start.await()
                    loader.loadLibrary()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, nativeLoads.get())
        assertTrue(loader.isLoaded)
    }

    private fun testLoader(
        loadLibrary: (String) -> Unit = {},
        loadPath: (String) -> Unit = {},
        lookupAfterLoad: () -> SymbolLookup = { EMPTY_LOOKUP },
    ): SDLLoader = SDLLoader(loadLibrary, loadPath, lookupAfterLoad)

    private companion object {
        val EMPTY_LOOKUP: SymbolLookup = SymbolLookup { java.util.Optional.empty() }
    }
}
