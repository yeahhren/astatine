#pragma once

#include <cstdio>
#include <string>

namespace at::log {
    enum class Level : int { Debug = 0, Info = 1, Warn = 2, Error = 3 };

    void write(Level level, const std::string &message);

    template<typename... Args>
    std::string format(const char *pattern, Args... args) {
        if constexpr (sizeof...(Args) == 0) {
            return pattern;
        } else {
            const int length = std::snprintf(nullptr, 0, pattern, args...);
            std::string text(static_cast<std::size_t>(length > 0 ? length : 0), '\0');
            std::snprintf(text.data(), text.size() + 1, pattern, args...);
            return text;
        }
    }

    template<typename... Args>
    void info(const char *pattern, Args... args) {
        write(Level::Info, format(pattern, args...));
    }

    template<typename... Args>
    void warn(const char *pattern, Args... args) {
        write(Level::Warn, format(pattern, args...));
    }

    template<typename... Args>
    void error(const char *pattern, Args... args) {
        write(Level::Error, format(pattern, args...));
    }
} // namespace at::log
