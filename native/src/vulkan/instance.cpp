#include "vulkan/instance.h"

#include "core/error.h"
#include "core/log.h"

#include <cstring>

namespace at {
    static constexpr auto VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";

    static bool hasInstanceExtension(const char *name) {
        uint32_t count = 0;
        vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr);
        std::vector<VkExtensionProperties> extensions(count);
        vkEnumerateInstanceExtensionProperties(nullptr, &count, extensions.data());
        for (const auto &[extensionName, specVersion]: extensions) {
            if (std::strcmp(extensionName, name) == 0) return true;
        }
        return false;
    }

    static bool hasLayer(const char *name) {
        uint32_t count = 0;
        vkEnumerateInstanceLayerProperties(&count, nullptr);
        std::vector<VkLayerProperties> layers(count);
        vkEnumerateInstanceLayerProperties(&count, layers.data());
        for (const auto &layer: layers) {
            if (std::strcmp(layer.layerName, name) == 0) return true;
        }
        return false;
    }

    static VKAPI_ATTR VkBool32 VKAPI_CALL debugCallback(
        VkDebugUtilsMessageSeverityFlagBitsEXT severity,
        VkDebugUtilsMessageTypeFlagsEXT,
        const VkDebugUtilsMessengerCallbackDataEXT *data,
        void *
    ) {
        if (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
            log::write(log::Level::Error, data->pMessage);
        } else {
            log::write(log::Level::Warn, data->pMessage);
        }
        return VK_FALSE;
    }

    Instance::Instance(PFN_vkGetInstanceProcAddr getInstanceProcAddr, const std::vector<std::string> &windowExtensions,
                       bool validation) : getInstanceProcAddr(getInstanceProcAddr) {
        vk::loadGlobal(getInstanceProcAddr);
        auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            getInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion"));
        u32 loaderVersion = VK_API_VERSION_1_0;
        if (enumerateVersion && enumerateVersion(&loaderVersion) != VK_SUCCESS) loaderVersion = VK_API_VERSION_1_0;
        if (loaderVersion >= VK_API_VERSION_1_3) apiVersion = VK_API_VERSION_1_3;
        else if (loaderVersion >= VK_API_VERSION_1_2) apiVersion = VK_API_VERSION_1_2;
        else if (loaderVersion >= VK_API_VERSION_1_1) apiVersion = VK_API_VERSION_1_1;
        bool debug = validation && hasLayer(VALIDATION_LAYER) &&
                     hasInstanceExtension(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
        properties2 = hasInstanceExtension(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);

        std::vector<const char *> extensions;
        for (const auto &extension: windowExtensions) extensions.push_back(extension.c_str());
        if (debug) extensions.push_back(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
        bool portability = hasInstanceExtension(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
        if (portability) extensions.push_back(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
        if (properties2) extensions.push_back(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
        externalCapabilities = properties2 && hasInstanceExtension(VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME) &&
                               hasInstanceExtension(VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME);
        if (externalCapabilities) {
            extensions.push_back(VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME);
            extensions.push_back(VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME);
        }

        VkApplicationInfo application{.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO};
        application.pApplicationName = "Minecraft";
        application.applicationVersion = VK_MAKE_VERSION(1, 8, 9);
        application.pEngineName = "Astatine";
        application.engineVersion = VK_MAKE_VERSION(0, 2, 0);
        application.apiVersion = apiVersion;

        VkInstanceCreateInfo info{.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        info.pApplicationInfo = &application;
        if (portability) info.flags |= VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
        info.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        info.ppEnabledExtensionNames = extensions.data();
        if (debug) {
            info.enabledLayerCount = 1;
            info.ppEnabledLayerNames = &VALIDATION_LAYER;
        }
        check(vkCreateInstance(&info, nullptr, &handle), "vkCreateInstance");
        enabledExtensions.assign(extensions.begin(), extensions.end());
        vk::loadInstance(handle);

        if (debug && vkCreateDebugUtilsMessengerEXT) {
            VkDebugUtilsMessengerCreateInfoEXT messengerInfo{VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT};
            messengerInfo.messageSeverity = VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                            VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
            messengerInfo.messageType = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                                        VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                                        VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
            messengerInfo.pfnUserCallback = debugCallback;
            check(vkCreateDebugUtilsMessengerEXT(handle, &messengerInfo, nullptr, &messenger),
                  "vkCreateDebugUtilsMessengerEXT");
        }
    }

    Instance::~Instance() {
        if (messenger) vkDestroyDebugUtilsMessengerEXT(handle, messenger, nullptr);
        vkDestroyInstance(handle, nullptr);
    }
} // namespace at
