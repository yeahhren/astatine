#pragma once

#include <jni.h>

#include "core/common.h"

#include <exception>
#include <string>
#include <type_traits>

namespace at::jvm {
    JavaVM *vm();

    JNIEnv *env();

    void throwError(JNIEnv *env, const char *message);

    std::string string(JNIEnv *env, jstring value);

    template<typename F>
    inline auto handled(JNIEnv *env, F &&body) -> decltype(body()) {
        using R = decltype(body());
        try {
            return body();
        } catch (const std::exception &error) {
            throwError(env, error.what());
        } catch (...) {
            throwError(env, "unknown native error");
        }
        if constexpr (!std::is_void_v<R>) {
            return R{};
        }
    }
} // namespace at::jvm

#define AT_EXPORT(type, name) extern "C" JNIEXPORT type JNICALL Java_yt_ren_astatine_Native_##name
