#include "backend/synchronization.h"

#include "core/error.h"
#include "backend/backend.h"
#include "backend/pipelines.h"
#include "backend/resources.h"

namespace at::backend {
    namespace {
        namespace access_type {
            constexpr u32 SHADER = 0;
            constexpr u32 UNIFORM = 1;
            constexpr u32 VERTEX = 2;
            constexpr u32 INDEX = 3;
            constexpr u32 INDIRECT = 4;
            constexpr u32 COLOR_ATTACHMENT = 5;
            constexpr u32 DEPTH_ATTACHMENT = 6;
            constexpr u32 TRANSFER = 7;
        }

        constexpr VkPipelineStageFlags WORLD_SOURCE_STAGES = VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT;
        constexpr VkPipelineStageFlags WORLD_DESTINATION_STAGES =
                VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT;
        constexpr VkAccessFlags MEMORY_ACCESS = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    } // namespace

    VkPipelineStageFlags Synchronization::shaderStages(u32 stages) {
        VkPipelineStageFlags result = 0;
        if (stages & (1u << shader_stage::VERTEX)) result |= VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
        if (stages & (1u << shader_stage::TESS_CONTROL)) result |= VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT;
        if (stages & (1u << shader_stage::TESS_EVAL)) result |= VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT;
        if (stages & (1u << shader_stage::GEOMETRY)) result |= VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT;
        if (stages & (1u << shader_stage::FRAGMENT)) result |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        if (stages & (1u << shader_stage::COMPUTE)) result |= VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        return result;
    }

    VkPipelineStageFlags Synchronization::defaultStages(u32 stage) {
        switch (stage) {
            case 1: return VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
            case 2: return VK_PIPELINE_STAGE_TRANSFER_BIT;
            default: return VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT;
        }
    }

    SyncAccess Synchronization::decode(const AccessRecord &record, u32 defaultStage) {
        SyncAccess result{};
        switch (record.kind) {
            case access_kind::TEXTURE:
                result.resource = {true, reinterpret_cast<u64>(pointer<Texture>(static_cast<i64>(record.handle))->image)};
                break;
            case access_kind::BUFFER:
                result.resource = {false, reinterpret_cast<u64>(pointer<Buffer>(static_cast<i64>(record.handle))->handle)};
                break;
            case access_kind::ANY_TEXTURE:
                result.resource = {true, 0};
                break;
            default:
                result.resource = {false, 0};
                break;
        }
        bool write = record.write != 0;
        VkPipelineStageFlags shader = record.stages ? shaderStages(record.stages) : defaultStages(defaultStage);
        switch (record.access) {
            case access_type::VERTEX:
            case access_type::INDEX:
                result.stages = VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
                break;
            case access_type::INDIRECT:
                result.stages = VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT;
                break;
            case access_type::COLOR_ATTACHMENT:
                result.stages = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                break;
            case access_type::DEPTH_ATTACHMENT:
                result.stages = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                break;
            case access_type::TRANSFER:
                result.stages = VK_PIPELINE_STAGE_TRANSFER_BIT;
                break;
            default:
                result.stages = shader;
                break;
        }
        switch (record.access) {
            case access_type::SHADER:
                result.access = write ? VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT : VK_ACCESS_SHADER_READ_BIT;
                break;
            case access_type::UNIFORM:
                result.access = VK_ACCESS_UNIFORM_READ_BIT;
                break;
            case access_type::VERTEX:
                result.access = VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
                break;
            case access_type::INDEX:
                result.access = VK_ACCESS_INDEX_READ_BIT;
                break;
            case access_type::INDIRECT:
                result.access = VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
                break;
            case access_type::COLOR_ATTACHMENT:
                result.access = write
                                    ? VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                                    : VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
                break;
            case access_type::DEPTH_ATTACHMENT:
                result.access = write
                                    ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT |
                                      VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                                    : VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;
                break;
            case access_type::TRANSFER:
                result.access = write ? VK_ACCESS_TRANSFER_WRITE_BIT : VK_ACCESS_TRANSFER_READ_BIT;
                break;
            default:
                fail("unknown synchronization access " + std::to_string(record.access));
        }
        result.write = write;
        return result;
    }

    SyncAccess Synchronization::shaderImage(VkImage image, VkPipelineStageFlags stages, bool write) {
        return {
            {true, reinterpret_cast<u64>(image)}, stages,
            VK_ACCESS_SHADER_READ_BIT | (write ? VK_ACCESS_SHADER_WRITE_BIT : 0u), write
        };
    }

    void Synchronization::before(VkCommandBuffer commands, const SyncAccess *accesses, std::size_t count) {
        if (!started) {
            memoryBarrier(commands);
            started = true;
        }
        std::vector<SyncAccess> merged;
        merged.reserve(count);
        for (std::size_t i = 0; i < count; i++) {
            const SyncAccess &access = accesses[i];
            bool found = false;
            for (SyncAccess &existing: merged) {
                if (existing.resource == access.resource) {
                    existing.stages |= access.stages;
                    existing.access |= access.access;
                    existing.write = existing.write || access.write;
                    found = true;
                    break;
                }
            }
            if (!found) merged.push_back(access);
        }
        VkPipelineStageFlags sourceStages = 0;
        VkAccessFlags sourceAccess = 0;
        VkPipelineStageFlags destinationStages = 0;
        VkAccessFlags destinationAccess = 0;
        for (const SyncAccess &access: merged) {
            for (const State &state: states) {
                const SyncResource &resource = state.resource;
                if (resource.image != access.resource.image ||
                    (resource.handle != 0 && access.resource.handle != 0 && !(resource == access.resource))) {
                    continue;
                }
                if (access.write) {
                    sourceStages |= state.writerStages | state.readerStages;
                    sourceAccess |= state.writerAccess;
                    destinationStages |= access.stages;
                    destinationAccess |= access.access;
                } else if ((state.visibleStages & access.stages) != access.stages ||
                           (state.visibleAccess & access.access) != access.access) {
                    sourceStages |= state.writerStages;
                    sourceAccess |= state.writerAccess;
                    destinationStages |= access.stages;
                    destinationAccess |= access.access;
                }
            }
        }
        for (const SyncAccess &access: merged) {
            State *state = nullptr;
            for (State &candidate: states) {
                if (candidate.resource == access.resource) {
                    state = &candidate;
                    break;
                }
            }
            if (!state) {
                states.push_back(State{.resource = access.resource});
                state = &states.back();
            }
            if (access.write) {
                state->writerStages = access.stages;
                state->writerAccess = access.access;
                state->readerStages = 0;
                state->visibleStages = 0;
                state->visibleAccess = 0;
            } else {
                state->readerStages |= access.stages;
                state->visibleStages |= access.stages;
                state->visibleAccess |= access.access;
            }
        }
        if (sourceStages != 0) {
            barrier(commands, sourceStages, sourceAccess, destinationStages ? destinationStages : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    destinationAccess);
        }
    }

    void Synchronization::afterWorldPass(VkCommandBuffer commands) {
        barrier(commands, WORLD_SOURCE_STAGES, MEMORY_ACCESS, WORLD_DESTINATION_STAGES, MEMORY_ACCESS);
        reset();
    }

    void Synchronization::after(VkCommandBuffer commands) {
        memoryBarrier(commands);
        reset();
    }

    void Synchronization::reset() {
        states.clear();
        started = false;
    }
} // namespace at::backend
