#include <SDL3/SDL_haptic.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

union TailPadded {
    unsigned char bytes[68];
    int64_t aligned;
};

union NestedUnion {
    union TailPadded inner;
    unsigned char bytes[77];
};

struct NestedStruct {
    unsigned char prefix;
    union NestedUnion payload;
    int64_t suffix;
};

_Static_assert(sizeof(union TailPadded) == 72, "union tail padding");
_Static_assert(_Alignof(union TailPadded) == 8, "union alignment");
_Static_assert(sizeof(union NestedUnion) == 80, "nested union tail padding");
_Static_assert(sizeof(struct NestedStruct) == 96, "nested struct tail padding");
_Static_assert(offsetof(struct NestedStruct, payload) == 8, "nested union offset");
_Static_assert(offsetof(struct NestedStruct, suffix) == 88, "suffix offset");
_Static_assert(sizeof(SDL_HapticEffect) == 72, "SDL haptic union tail padding");
_Static_assert(_Alignof(SDL_HapticEffect) == 8, "SDL haptic union alignment");
_Static_assert(sizeof(SDL_HapticEffect[2]) == 144, "SDL haptic array stride");

int main(void) {
    printf("SDL_HAPTIC_EFFECT_C_SIZE=%zu ALIGN=%zu\n", sizeof(SDL_HapticEffect), _Alignof(SDL_HapticEffect));
    puts("UNION_LAYOUT_C_OK");
    return 0;
}
