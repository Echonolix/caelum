# Caelum SDL3

Low-level SDL 3 bindings generated from the vendored SDL 3.4.14 public headers.

The module targets JDK 24 bytecode and is published as
`net.echonolix:caelum-sdl3:1.0-SNAPSHOT`. When consuming it from this repository,
use the project dependency:

```kotlin
dependencies {
    implementation(project(":caelum-sdl3"))
}
```

Programs that call the bindings must enable native access for the module that
contains the caller. For an unnamed classpath application, start the JVM with
`--enable-native-access=ALL-UNNAMED`.

## Loading SDL

Load the native library before calling any generated function:

```kotlin
import java.nio.file.Path
import net.echonolix.caelum.sdl3.SDL
import net.echonolix.caelum.sdl3.SDL_GetVersion

SDL.load(Path.of("C:/path/to/SDL3.dll"))
println(SDL_GetVersion())
```

`SDL.load()` uses the platform library search path. `SDL.load(Path)` uses a
normalized absolute path. Loading is thread-safe and idempotent for the same
source. Native symbols are resolved and cached when each function is first
called, so a library may omit platform-specific entry points that the program
does not use.

Generated callback typedefs accept Kotlin lambdas directly as well as raw
function pointers. Their native stubs stay alive until
`SDLFunction.freeFunctionStubs()` is called; only release them after SDL has
unregistered every callback that may still be invoked.
`SDLCallbacks.erasures` reports callback parameters whose pointee layout is
owned by external platform headers. In this ABI, `MSG*` and `XEvent*` remain
one-level `NPointer<*>` values instead of pretending SDL defines those records.

SDL's 15 public header-only helpers are implemented directly in Kotlin and are
listed in `SDLInlineFunctions.names`; `SDLInlineFunctions.unsupported` records
any future helper that cannot be represented safely. They do not require an
exported DLL symbol.

## ABI scope

This module currently supports the 64-bit Windows SDL ABI only. `SDL.load()`
rejects other operating systems and architectures before loading native code.
This matters for C types such as `long`, `wchar_t`, and pointers. The generated inventory
includes declarations from platform sections of the public SDL headers; calling
one requires that the loaded SDL library exports it.

The verified `SDL_GUID` and `SDL_FColor` by-value APIs use their fixed Windows
x64 layouts. Functions returning an aggregate take a `SegmentAllocator` as
their first Kotlin argument, and the returned `NValue` remains valid for the
lifetime of that allocator. When passing an aggregate `NValue`, its backing
allocation must remain alive until the native call returns. Functions with C
varargs, `va_list`, unsupported by-value aggregate signatures, platform-owned
native handles, and unsupported macro entry points are listed in
`SDLFunctions.skipped` instead of receiving an unsafe signature.

## Verification

Normal tests do not require a native SDL installation. The explicit Windows
smoke test loads a caller-selected DLL, initializes video, creates a hidden
window, polls an event, and shuts SDL down:

```powershell
.\gradlew.bat :caelum-sdl3:sdl3Smoke `
  -Psdl3Dll=C:\absolute\path\to\SDL3.dll
```
