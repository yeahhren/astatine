#pragma once

#include "core/common.h"

#include <string>
#include <vector>

namespace at::shaderpack {
    // Same order as Iris' PatchShaderType.
    enum class ShaderStage : u32 {
        VERTEX,
        GEOMETRY,
        TESS_CONTROL,
        TESS_EVAL,
        FRAGMENT,
        COMPUTE,
    };

    struct ShaderSource {
        ShaderStage stage;
        std::string name;
        std::string source;
    };

    struct CompiledShader {
        std::vector<u32> code;
        std::string log;

        [[nodiscard]] bool ok() const { return !code.empty(); }
    };

    class ShaderCompiler {
    public:
        // vulkanVersion is a VK_API_VERSION_* value, it picks the SPIR-V version.
        explicit ShaderCompiler(u32 vulkanVersion);

        ~ShaderCompiler();

        ShaderCompiler(const ShaderCompiler &) = delete;

        ShaderCompiler &operator=(const ShaderCompiler &) = delete;

        [[nodiscard]] CompiledShader compile(ShaderStage stage, const std::string &name, const std::string &source) const;

        // Compiles every source on background threads and returns results in the same order.
        [[nodiscard]] std::vector<CompiledShader> compileAll(const std::vector<ShaderSource> &sources) const;

    private:
        u32 vulkanVersion;
    };
} // namespace at::shaderpack
