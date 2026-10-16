#pragma once

#include <thread>

namespace at {
    void lowerPriority(std::thread &thread);
} // namespace at
