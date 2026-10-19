/* Validate the normalized binding's carriers against the pinned upstream header on both targets. */
#include <stdint.h>
#include "mimalloc.h"
_Static_assert(sizeof(size_t) == sizeof(uint64_t), "size_t carrier");
_Static_assert(sizeof(bool) == sizeof(uint8_t), "bool carrier");
_Static_assert(sizeof(void*) == sizeof(uint64_t), "pointer carrier");
_Static_assert(MI_MALLOC_VERSION == 20502, "pinned native version");
_Static_assert(__builtin_types_compatible_p(__typeof__(&mi_malloc_aligned), void* (*)(size_t, size_t)), "malloc ABI");
_Static_assert(__builtin_types_compatible_p(__typeof__(&mi_zalloc_aligned), void* (*)(size_t, size_t)), "zalloc ABI");
_Static_assert(__builtin_types_compatible_p(__typeof__(&mi_free), void (*)(void*)), "free ABI");
_Static_assert(__builtin_types_compatible_p(__typeof__(&mi_usable_size), size_t (*)(const void*)), "usable size ABI");
_Static_assert(__builtin_types_compatible_p(__typeof__(&mi_collect), void (*)(bool)), "collect ABI");
_Static_assert(__builtin_types_compatible_p(__typeof__(&mi_version), int (*)(void)), "version ABI");
