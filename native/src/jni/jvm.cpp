#include "core/common.h"
#include "jni/jvm.h"
#include "core/log.h"

#include <mutex>

namespace at::jvm {
    static JavaVM *javaVm = nullptr;
    static jclass logClass = nullptr;
    static jmethodID logMethod = nullptr;
    static jclass errorClass = nullptr;

    JavaVM *vm() {
        return javaVm;
    }

    JNIEnv *env() {
        JNIEnv *current = nullptr;
        if (javaVm->GetEnv(reinterpret_cast<void **>(&current), JNI_VERSION_1_8) == JNI_OK) {
            return current;
        }
        javaVm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&current), nullptr);
        return current;
    }

    void throwError(JNIEnv *env, const char *message) {
        if (env->ExceptionCheck()) {
            return;
        }
        env->ThrowNew(errorClass, message);
    }

    std::string string(JNIEnv *env, jstring value) {
        if (!value) {
            return {};
        }
        const char *chars = env->GetStringUTFChars(value, nullptr);
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    }
} // namespace at::jvm

namespace at::log {
    void write(Level level, const std::string &message) {
        JNIEnv *env = jvm::env();
        if (!env || !jvm::logMethod) {
            std::fprintf(stderr, "[Astatine] %s\n", message.c_str());
            return;
        }
        jstring text = env->NewStringUTF(message.c_str());
        env->CallStaticVoidMethod(jvm::logClass, jvm::logMethod, static_cast<jint>(level), text);
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
} // namespace at::log

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    using namespace at::jvm;
    javaVm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }
    if (jclass log = env->FindClass("yt/ren/astatine/NativeLog")) {
        logClass = reinterpret_cast<jclass>(env->NewGlobalRef(log));
        logMethod = env->GetStaticMethodID(logClass, "log", "(ILjava/lang/String;)V");
    }
    env->ExceptionClear();
    errorClass = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/IllegalStateException")));
    return JNI_VERSION_1_8;
}
