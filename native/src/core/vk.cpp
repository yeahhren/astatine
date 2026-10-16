#include "core/vk.h"

#include "core/error.h"

namespace at::vk {
#define AT_VK_DEFINE(name) PFN_##name name = nullptr;
    AT_VK_GLOBAL_FUNCTIONS(AT_VK_DEFINE)
    AT_VK_INSTANCE_FUNCTIONS(AT_VK_DEFINE)
    AT_VK_OPTIONAL_INSTANCE_FUNCTIONS(AT_VK_DEFINE)
    AT_VK_DEVICE_FUNCTIONS(AT_VK_DEFINE)
#undef AT_VK_DEFINE

    static PFN_vkGetInstanceProcAddr instanceProcAddr = nullptr;

    // ReSharper disable once CppParameterMayBeConst
    void loadGlobal(PFN_vkGetInstanceProcAddr getInstanceProcAddr) {
        instanceProcAddr = getInstanceProcAddr;
#define AT_VK_LOAD(name) \
    name = reinterpret_cast<PFN_##name>(instanceProcAddr(nullptr, #name)); \
    if (!name) fail("Vulkan loader is missing " #name);
        AT_VK_GLOBAL_FUNCTIONS(AT_VK_LOAD)
#undef AT_VK_LOAD
    }

    // ReSharper disable once CppParameterMayBeConst
    void loadInstance(VkInstance instance) {
#define AT_VK_LOAD(name) \
    name = reinterpret_cast<PFN_##name>(instanceProcAddr(instance, #name)); \
    if (!name) fail("Vulkan instance is missing " #name);
        AT_VK_INSTANCE_FUNCTIONS(AT_VK_LOAD)
#undef AT_VK_LOAD
#define AT_VK_LOAD_OPTIONAL(name) name = reinterpret_cast<PFN_##name>(instanceProcAddr(instance, #name));
        AT_VK_OPTIONAL_INSTANCE_FUNCTIONS(AT_VK_LOAD_OPTIONAL)
#undef AT_VK_LOAD_OPTIONAL
    }

    void loadDevice(VkDevice device) {
#define AT_VK_LOAD(name) \
    name = reinterpret_cast<PFN_##name>(vkGetDeviceProcAddr(device, #name)); \
    if (!name) fail("Vulkan device is missing " #name);
        AT_VK_DEVICE_FUNCTIONS(AT_VK_LOAD)
#undef AT_VK_LOAD
    }
} // namespace at::vk
