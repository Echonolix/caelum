#include "sample.hpp"
#include <stdexcept>

namespace demo {
int add(int left, int right) { return left + right; }
unsigned int add(unsigned int left, unsigned int right) { return left + right; }
double add(double left, double right) { return left + right; }
int fail() { throw std::runtime_error("fixture failure"); }
const char* greeting() { return "hello from const"; }
namespace first { int value() { return 1; } }
namespace second { int value() { return 2; } }
namespace a { namespace b { int echo(int value) { return value + 1; } } }
namespace a_b { int echo(int value) { return value + 2; } }
int clash(int value) { return value; }
Alpha::~Alpha() = default;
int Alpha::clash(int value) { return value + 10; }
Beta::~Beta() = default;
int Beta::clash(int value) { return value + 20; }
Counter::Counter(int initial) : value_(initial) {}
Counter::~Counter() = default;
int Counter::add(int amount) { return value_ += amount; }
int Counter::get() const { return value_; }
int Counter::twice(int value) { return value * 2; }
}
