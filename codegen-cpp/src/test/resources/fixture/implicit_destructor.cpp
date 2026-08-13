#include "implicit_destructor.hpp"

namespace demo {
ImplicitDestructor::ImplicitDestructor(int initial) : value_(initial) {}
int ImplicitDestructor::add(int amount) { return value_ += amount; }
}
