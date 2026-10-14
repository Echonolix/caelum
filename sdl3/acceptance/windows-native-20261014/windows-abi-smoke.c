#include <stddef.h>
#include "SDL3/SDL_guid.h"
#include "SDL3/SDL_gpu.h"
#include "SDL3/SDL_haptic.h"
#include "SDL3/SDL_thread.h"

_Static_assert(sizeof(void *) == 8, "Windows x64 pointer width");
_Static_assert(sizeof(size_t) == 8, "Windows x64 size_t width");
_Static_assert(sizeof(long) == 4, "Windows LLP64 long width");
_Static_assert(sizeof(wchar_t) == 2, "Windows wchar_t width");

_Static_assert(sizeof(SDL_GUID) == 16, "SDL_GUID size");
_Static_assert(_Alignof(SDL_GUID) == 1, "SDL_GUID alignment");
_Static_assert(sizeof(SDL_FColor) == 16, "SDL_FColor size");
_Static_assert(_Alignof(SDL_FColor) == 4, "SDL_FColor alignment");
_Static_assert(sizeof(SDL_HapticEffect) == 72, "SDL_HapticEffect size and union tail padding");
_Static_assert(_Alignof(SDL_HapticEffect) == 8, "SDL_HapticEffect alignment");
_Static_assert(sizeof(SDL_TLSID) == 4, "SDL_TLSID size");
_Static_assert(_Alignof(SDL_TLSID) == 4, "SDL_TLSID alignment");

int main(void) { return 0; }