#include "shaderpack/compiler.h"

#include "core/threads.h"
#include "core/vk.h"

#include <glslang/Public/ResourceLimits.h>
#include <glslang/Public/ShaderLang.h>
#include <SPIRV/GlslangToSpv.h>

#include <algorithm>
#include <atomic>
#include <exception>
#include <thread>

namespace at::shaderpack {
    namespace {
        EShLanguage language(ShaderStage stage) {
            switch (stage) {
                case ShaderStage::VERTEX: return EShLangVertex;
                case ShaderStage::GEOMETRY: return EShLangGeometry;
                case ShaderStage::TESS_CONTROL: return EShLangTessControl;
                case ShaderStage::TESS_EVAL: return EShLangTessEvaluation;
                case ShaderStage::FRAGMENT: return EShLangFragment;
                case ShaderStage::COMPUTE: return EShLangCompute;
            }
            return EShLangVertex;
        }

        struct Targets {
            glslang::EShTargetClientVersion client;
            glslang::EShTargetLanguageVersion spirv;
        };

        Targets targets(u32 vulkanVersion) {
            u32 minor = VK_API_VERSION_MAJOR(vulkanVersion) > 1 ? 3 : VK_API_VERSION_MINOR(vulkanVersion);
            switch (minor) {
                case 0: return {glslang::EShTargetVulkan_1_0, glslang::EShTargetSpv_1_0};
                case 1: return {glslang::EShTargetVulkan_1_1, glslang::EShTargetSpv_1_3};
                case 2: return {glslang::EShTargetVulkan_1_2, glslang::EShTargetSpv_1_5};
                default: return {glslang::EShTargetVulkan_1_3, glslang::EShTargetSpv_1_6};
            }
        }

        void appendLog(std::string &log, const char *text) {
            if (!text || !*text) return;
            if (!log.empty() && log.back() != '\n') log += '\n';
            log += text;
        }
    } // namespace

    ShaderCompiler::ShaderCompiler(u32 vulkanVersion) : vulkanVersion(vulkanVersion) {
        glslang::InitializeProcess();
    }

    ShaderCompiler::~ShaderCompiler() {
        glslang::FinalizeProcess();
    }

    CompiledShader ShaderCompiler::compile(ShaderStage stage, const std::string &name,
                                           const std::string &source) const {
        CompiledShader result;
        EShLanguage lang = language(stage);
        Targets target = targets(vulkanVersion);

        glslang::TShader shader(lang);
        const char *text = source.c_str();
        int length = static_cast<int>(source.size());
        const char *label = name.c_str();
        shader.setStringsWithLengthsAndNames(&text, &length, &label, 1);
        shader.setEnvInput(glslang::EShSourceGlsl, lang, glslang::EShClientVulkan, 100);
        shader.setEnvClient(glslang::EShClientVulkan, target.client);
        shader.setEnvTarget(glslang::EShTargetSpv, target.spirv);
        shader.setEntryPoint("main");

        auto messages = static_cast<EShMessages>(EShMsgSpvRules | EShMsgVulkanRules);
        if (!shader.parse(GetDefaultResources(), 460, ENoProfile, false, false, messages)) {
            appendLog(result.log, shader.getInfoLog());
            appendLog(result.log, shader.getInfoDebugLog());
            return result;
        }
        appendLog(result.log, shader.getInfoLog());

        glslang::TProgram program;
        program.addShader(&shader);
        if (!program.link(messages)) {
            appendLog(result.log, program.getInfoLog());
            appendLog(result.log, program.getInfoDebugLog());
            return result;
        }

        glslang::SpvOptions options;
        options.disableOptimizer = true;
        options.validate = false;
        spv::SpvBuildLogger logger;
        glslang::GlslangToSpv(*program.getIntermediate(lang), result.code, &logger, &options);
        appendLog(result.log, logger.getAllMessages().c_str());
        return result;
    }

    std::vector<CompiledShader> ShaderCompiler::compileAll(const std::vector<ShaderSource> &sources) const {
        std::vector<CompiledShader> results(sources.size());
        if (sources.empty()) return results;
        std::atomic<std::size_t> next{0};
        auto work = [&] {
            for (std::size_t index; (index = next.fetch_add(1, std::memory_order_relaxed)) < sources.size();) {
                const ShaderSource &source = sources[index];
                try {
                    results[index] = compile(source.stage, source.name, source.source);
                } catch (const std::exception &error) {
                    results[index].log = error.what();
                }
            }
        };
        u32 hardware = std::max(std::thread::hardware_concurrency(), 1u);
        auto count = static_cast<u32>(std::min<std::size_t>(sources.size(), std::clamp(hardware / 2, 1u, 8u)));
        std::vector<std::thread> threads;
        threads.reserve(count);
        for (u32 i = 0; i < count; i++) {
            threads.emplace_back(work);
            lowerPriority(threads.back());
        }
        for (std::thread &thread: threads) thread.join();
        return results;
    }
} // namespace at::shaderpack
