#pragma once

namespace demo {
class ImplicitDestructor {
public:
    explicit ImplicitDestructor(int initial);
    int add(int amount);

private:
    int value_;
};
}
