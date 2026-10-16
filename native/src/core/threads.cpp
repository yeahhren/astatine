#include "core/threads.h"

#ifdef _WIN32
#include <windows.h>
#endif

namespace at {
    void lowerPriority(std::thread &thread) {
#ifdef _WIN32
        SetThreadPriority(reinterpret_cast<HANDLE>(thread.native_handle()), THREAD_PRIORITY_BELOW_NORMAL);
#else
        (void) thread;
#endif
    }
} // namespace at
