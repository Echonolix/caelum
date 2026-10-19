/* Normalized subset of mimalloc v2.5.2 include/mimalloc.h (MIT).
 * x86_64 size_t is 64 bits on both supported targets. No heap-local APIs:
 * allocation uses the calling thread's default heap; mi_free supports remote frees.
 */
#include <stdint.h>
typedef uint64_t size_t;
int mi_version(void);
void* mi_malloc_aligned(size_t size, size_t alignment);
void* mi_zalloc_aligned(size_t size, size_t alignment);
void mi_free(void* p);
size_t mi_usable_size(const void* p);
/* C bool has the same one-byte ABI as uint8_t on the supported targets. */
void mi_collect(uint8_t force);
