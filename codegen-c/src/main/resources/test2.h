# 1 "test.h"
# 1 "<built-in>" 1
# 1 "<built-in>" 3
# 427 "<built-in>" 3
# 1 "<command line>" 1
# 1 "<built-in>" 2
# 1 "test.h" 2
typedef struct Foo {
    int a;
    float b;
} Foo;

enum Baz {
    BAZ_A,
    BAZ_B,
    BAZ_C
}

double bar(Foo* a, int c, float d, enum Baz e);
