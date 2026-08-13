#pragma once
#include <cstdint>

namespace demo {
enum class Mode : int { Idle = 0, Active = 4 };

int add(int left, int right);
unsigned int add(unsigned int left, unsigned int right);
double add(double left, double right);
int fail();
const char* greeting();

namespace first { enum class State : int { Ready = 1 }; int value(); }
namespace second { enum class State : int { Done = 2 }; int value(); }
namespace a { namespace b { enum class Token : int { Nested = 3 }; int echo(int value); } }
namespace a_b { enum class Token : int { Flat = 4 }; int echo(int value); }

int clash(int value);
class Alpha { public: ~Alpha(); static int clash(int value); };
class Beta { public: ~Beta(); static int clash(int value); };

class Counter {
public:
    explicit Counter(int initial);
    ~Counter();
    int add(int amount);
    int get() const;
    static int twice(int value);
private:
    int value_;
};
}
