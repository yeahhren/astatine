#include "backend/backend.h"

#include "core/error.h"
#include "render/renderer.h"
#include "backend/mipmaps.h"
#include "backend/pipelines.h"
#include "backend/programs.h"
#include "backend/render_passes.h"
#include "backend/resources.h"
#include "backend/synchronization.h"
#include "backend/transient.h"
#include "vulkan/device.h"
#include "vulkan/instance.h"

#include <algorithm>
#include <cstring>

namespace at::backend {
    std::unique_ptr<Backend> Backend::instance;
    std::mutex Backend::creation;

    Backend &Backend::get() {
        if (!instance) {
            std::lock_guard guard(creation);
            if (!instance) {
                if (!Renderer::ready()) fail("the renderer is not initialized");
                instance = std::make_unique<Backend>();
            }
        }
        return *instance;
    }

    void Backend::shutdown() {
        std::lock_guard guard(creation);
        if (!instance) return;
        instance->device.waitIdle();
        instance->colorMipmaps.reset();
        instance->depthMipmaps.reset();
        instance->transient.reset();
        instance->passes.reset();
        instance->shaders.reset();
        instance->sync.reset();
        instance->programs.reset();
        instance.reset();
    }

    Backend::Backend()
        : renderer(Renderer::get()), device(*Renderer::get().context().device),
          allocator(*Renderer::get().context().allocator) {
        vkGetPhysicalDeviceProperties(device.physical, &properties);
        if (vkGetPhysicalDeviceProperties2KHR) {
            VkPhysicalDevicePushDescriptorPropertiesKHR push{
                .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PUSH_DESCRIPTOR_PROPERTIES_KHR
            };
            VkPhysicalDeviceSubgroupProperties subgroup{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_PROPERTIES};
            subgroup.pNext = &push;
            VkPhysicalDeviceProperties2 properties2{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
            properties2.pNext = &subgroup;
            vkGetPhysicalDeviceProperties2KHR(device.physical, &properties2);
            maxPushDescriptors = push.maxPushDescriptors;
            subgroupQuads = (subgroup.supportedStages & VK_SHADER_STAGE_COMPUTE_BIT) &&
                            (subgroup.supportedOperations & VK_SUBGROUP_FEATURE_QUAD_BIT);
        }
        anisotropy = hasFeature("VkPhysicalDeviceFeatures2.samplerAnisotropy");
        demote = hasFeature("VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT.shaderDemoteToHelperInvocation");
        geometryShaders = hasFeature("VkPhysicalDeviceFeatures2.geometryShader");
        tessellationShaders = hasFeature("VkPhysicalDeviceFeatures2.tessellationShader");
#ifdef __APPLE__
        updateAfterBind = hasFeature("VkPhysicalDeviceVulkan12Features.descriptorBindingSampledImageUpdateAfterBind") &&
                          hasFeature("VkPhysicalDeviceVulkan12Features.descriptorBindingUniformTexelBufferUpdateAfterBind");
#endif
        u32 version = std::min(std::min(device.apiVersion, Renderer::vulkan().apiVersion), VK_API_VERSION_1_2);
        passes = std::make_unique<RenderPassCache>(device.handle);
        sync = std::make_unique<Synchronization>();
        transient = std::make_unique<TransientMemory>(Renderer::FRAMES + 2);
        shaders = std::make_unique<ShaderCache>(version);
        colorMipmaps = std::make_unique<ColorMipmaps>();
        depthMipmaps = std::make_unique<DepthMipmaps>();
        programs = std::make_unique<ProgramState>();
    }

    Backend::~Backend() = default;

    VkCommandBuffer Backend::commands() {
        renderer.waitIdle();
        if (renderer.externalPass) {
            renderer.ensureRecording();
            renderer.flushBatch();
        } else {
            renderer.endCurrentPass();
        }
        renderer.forgetBindings();
        return renderer.commands();
    }

    VkCommandBuffer Backend::setupCommands() {
        renderer.waitIdle();
        return renderer.setupCommands();
    }

    void Backend::defer(std::function<void()> release) {
        renderer.waitIdle();
        renderer.defer(std::move(release));
    }

    void Backend::waitIdle() {
        renderer.waitIdle();
    }

    void Backend::setName(VkObjectType type, u64 handle, const char *name) const {
        if (!vkSetDebugUtilsObjectNameEXT || !handle || !name || !*name) return;
        VkDebugUtilsObjectNameInfoEXT info{.sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_OBJECT_NAME_INFO_EXT};
        info.objectType = type;
        info.objectHandle = handle;
        info.pObjectName = name;
        vkSetDebugUtilsObjectNameEXT(device.handle, &info);
    }

    bool Backend::hasFeature(const char *name) const {
        return std::any_of(device.enabledFeatures.begin(), device.enabledFeatures.end(),
                           [&](const std::string &feature) { return feature == name; });
    }

    bool Backend::formatFeatures(VkFormat format, VkFormatFeatureFlags required) const {
        VkFormatProperties formatProperties;
        vkGetPhysicalDeviceFormatProperties(device.physical, format, &formatProperties);
        return (formatProperties.optimalTilingFeatures & required) == required;
    }

    void Backend::releaseTexture(Texture *texture) {
        colorMipmaps->release(texture);
        depthMipmaps->release(texture);
    }

    void Backend::releaseView(TextureView *view) {
        passes->release(view->handle);
    }

    void memoryBarrier(VkCommandBuffer commands) {
        barrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
    }

    void barrier(VkCommandBuffer commands, VkPipelineStageFlags sourceStages, VkAccessFlags sourceAccess,
                 VkPipelineStageFlags destinationStages, VkAccessFlags destinationAccess) {
        VkMemoryBarrier memory{.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER};
        memory.srcAccessMask = sourceAccess;
        memory.dstAccessMask = destinationAccess;
        vkCmdPipelineBarrier(commands, sourceStages, destinationStages, 0, 1, &memory, 0, nullptr, 0, nullptr);
    }
} // namespace at::backend
