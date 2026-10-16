#include "render/state.h"

#include "render/pipeline_key.h"

#include "core/vk.h"

namespace at {
    RenderState::RenderState() {
        lightDiffuse[0] = 1.0f;
        lightDiffuse[1] = 1.0f;
        lightDiffuse[2] = 1.0f;
        for (int light = 0; light < 8; light++) {
            lightPositions[light * 4 + 2] = 1.0f;
            lightDiffuse[light * 4 + 3] = 1.0f;
            lightAmbient[light * 4 + 3] = 1.0f;
        }
        for (int unit = 0; unit < UNITS; unit++) {
            texCoords[unit * 4 + 3] = 1.0f;
            textureStacks[unit] = std::make_unique<MatrixStack>(10);
        }
    }

    void RenderState::setCapability(i32 capability, bool enabled) {
        auto pipeline = [&](bool &current) {
            if (current != enabled) invalidatePipeline();
            current = enabled;
        };
        auto environment = [&](bool &current, u32 extra = 0) {
            if (current != enabled) dirty |= ENVIRONMENT | extra;
            current = enabled;
        };
        switch (capability) {
            case gl::ALPHA_TEST: environment(alphaTest);
                return;
            case gl::LIGHTING: environment(lighting, TRANSFORM);
                return;
            case gl::COLOR_MATERIAL: environment(colorMaterialEnabled);
                return;
            case gl::NORMALIZE: environment(normalizeEnabled);
                return;
            case gl::RESCALE_NORMAL: environment(rescaleNormal, TRANSFORM);
                return;
            case gl::FOG: environment(fogEnabled);
                return;
            case gl::DEPTH_TEST: pipeline(depthTest);
                return;
            case gl::BLEND: pipeline(blend);
                return;
            case gl::CULL_FACE: pipeline(cull);
                return;
            case gl::POLYGON_OFFSET_FILL: pipeline(offsetFill);
                return;
            case gl::POLYGON_OFFSET_LINE: pipeline(offsetLine);
                return;
            case gl::COLOR_LOGIC_OP: pipeline(colorLogicOp);
                return;
            case gl::TEXTURE_2D:
                if (textureEnabledValue[activeUnit] != enabled) {
                    textureEnabledValue[activeUnit] = enabled;
                    dirty |= TEXTURES | ENVIRONMENT;
                }
                return;
            default: break;
        }
        if (capability >= gl::LIGHT0 && capability <= gl::LIGHT7) {
            environment(lights[capability - gl::LIGHT0]);
        } else if (capability >= gl::TEXTURE_GEN_S && capability <= gl::TEXTURE_GEN_Q) {
            environment(texGenEnabled[capability - gl::TEXTURE_GEN_S]);
        }
    }

    void RenderState::alphaFunction(i32 function, float reference) {
        alphaFunctionValue = function;
        alphaReference = clamp(reference, 0.0f, 1.0f);
        dirty |= ENVIRONMENT;
    }

    void RenderState::colorMaterial(i32 mode) {
        colorMaterialMode = mode;
        dirty |= ENVIRONMENT;
    }

    void RenderState::light(i32 light, i32 parameter, float x, float y, float z, float w) {
        int index = ((light - gl::LIGHT0) & 7) * 4;
        switch (parameter) {
            case gl::POSITION: mat4::transformPoint(modelView.top(), x, y, z, w, lightPositions + index);
                break;
            case gl::DIFFUSE: set(lightDiffuse + index, x, y, z, w);
                break;
            case gl::AMBIENT: set(lightAmbient + index, x, y, z, w);
                break;
            default: break;
        }
        dirty |= ENVIRONMENT;
    }

    void RenderState::lightModelAmbient(float r, float g, float b, float a) {
        set(lightModelAmbientValue, r, g, b, a);
        dirty |= ENVIRONMENT;
    }

    void RenderState::depthFunction(i32 function) {
        if (depthFunctionValue == function) return;
        depthFunctionValue = function;
        invalidatePipeline();
    }

    void RenderState::depthMask(bool mask) {
        if (depthMaskValue == mask) return;
        depthMaskValue = mask;
        invalidatePipeline();
    }

    void RenderState::blendFunction(i32 sourceColor, i32 destinationColor, i32 sourceAlpha, i32 destinationAlpha) {
        blendSourceColor = sourceColor;
        blendDestinationColor = destinationColor;
        blendSourceAlpha = sourceAlpha;
        blendDestinationAlpha = destinationAlpha;
        invalidatePipeline();
    }

    void RenderState::cullFace(i32 face) {
        if (cullFaceValue == face) return;
        cullFaceValue = face;
        invalidatePipeline();
    }

    void RenderState::polygonOffset(float factor, float units) {
        offsetFactor = factor;
        offsetUnits = units;
        dirty |= DEPTH_BIAS;
    }

    void RenderState::logicOp(i32 mode) {
        if (logicOpMode == mode) return;
        logicOpMode = mode;
        invalidatePipeline();
    }

    void RenderState::colorMask(u32 mask) {
        if (colorMaskValue == mask) return;
        colorMaskValue = mask;
        invalidatePipeline();
    }

    void RenderState::shadeModel(i32 mode) {
        bool flat = mode == gl::FLAT;
        if (flatShading == flat) return;
        flatShading = flat;
        invalidatePipeline();
    }

    void RenderState::texGenPlane(i32 coordinate, i32 parameter, float x, float y, float z, float w) {
        int index = (coordinate & 3) * 4;
        if (parameter == gl::OBJECT_PLANE) {
            set(objectPlanes + index, x, y, z, w);
        } else {
            float inverse[16];
            if (!mat4::invert(modelView.top(), inverse)) return;
            float plane[4] = {x, y, z, w};
            mat4::transformPlane(plane, inverse, eyePlanes + index);
        }
        dirty |= ENVIRONMENT;
    }

    void RenderState::pushAttributes() {
        auto &attributes = saved.emplace_back();
        attributes.lighting = lighting;
        std::memcpy(attributes.lights, lights, sizeof(lights));
        attributes.colorMaterial = colorMaterialEnabled;
        attributes.colorMaterialMode = colorMaterialMode;
        std::memcpy(attributes.lightModelAmbient, lightModelAmbientValue, sizeof(lightModelAmbientValue));
        std::memcpy(attributes.lightPositions, lightPositions, sizeof(lightPositions));
        std::memcpy(attributes.lightDiffuse, lightDiffuse, sizeof(lightDiffuse));
        std::memcpy(attributes.lightAmbient, lightAmbient, sizeof(lightAmbient));
        attributes.flatShading = flatShading;
        attributes.capabilities = capabilities();
    }

    void RenderState::popAttributes() {
        if (saved.empty()) return;
        // ReSharper disable once CppUseStructuredBinding
        const Attributes attributes = saved.back();
        saved.pop_back();
        lighting = attributes.lighting;
        std::memcpy(lights, attributes.lights, sizeof(lights));
        colorMaterialEnabled = attributes.colorMaterial;
        colorMaterialMode = attributes.colorMaterialMode;
        std::memcpy(lightModelAmbientValue, attributes.lightModelAmbient, sizeof(lightModelAmbientValue));
        std::memcpy(lightPositions, attributes.lightPositions, sizeof(lightPositions));
        std::memcpy(lightDiffuse, attributes.lightDiffuse, sizeof(lightDiffuse));
        std::memcpy(lightAmbient, attributes.lightAmbient, sizeof(lightAmbient));
        flatShading = attributes.flatShading;
        u64 bits = attributes.capabilities;
        alphaTest = bits & 1;
        normalizeEnabled = bits & 2;
        rescaleNormal = bits & 4;
        fogEnabled = bits & 8;
        depthTest = bits & 16;
        blend = bits & 32;
        cull = bits & 64;
        offsetFill = bits & 128;
        offsetLine = bits & 256;
        colorLogicOp = bits & 512;
        for (int coordinate = 0; coordinate < 4; coordinate++)
            texGenEnabled[coordinate] = bits & (1024ull << coordinate);
        for (int unit = 0; unit < UNITS; unit++) textureEnabledValue[unit] = bits & (16384ull << unit);
        invalidatePipeline();
        dirty = ALL;
    }

    u64 RenderState::capabilities() const {
        u64 bits = 0;
        if (alphaTest) bits |= 1;
        if (normalizeEnabled) bits |= 2;
        if (rescaleNormal) bits |= 4;
        if (fogEnabled) bits |= 8;
        if (depthTest) bits |= 16;
        if (blend) bits |= 32;
        if (cull) bits |= 64;
        if (offsetFill) bits |= 128;
        if (offsetLine) bits |= 256;
        if (colorLogicOp) bits |= 512;
        for (int coordinate = 0; coordinate < 4; coordinate++)
            if (texGenEnabled[coordinate])
                bits |= 1024ull << coordinate;
        for (int unit = 0; unit < UNITS; unit++) if (textureEnabledValue[unit]) bits |= 16384ull << unit;
        return bits;
    }

    static u32 blendFactor(i32 factor) {
        switch (factor) {
            case gl::ZERO: return VK_BLEND_FACTOR_ZERO;
            case gl::ONE: return VK_BLEND_FACTOR_ONE;
            case gl::SRC_COLOR: return VK_BLEND_FACTOR_SRC_COLOR;
            case gl::ONE_MINUS_SRC_COLOR: return VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case gl::SRC_ALPHA: return VK_BLEND_FACTOR_SRC_ALPHA;
            case gl::ONE_MINUS_SRC_ALPHA: return VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case gl::DST_ALPHA: return VK_BLEND_FACTOR_DST_ALPHA;
            case gl::ONE_MINUS_DST_ALPHA: return VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case gl::DST_COLOR: return VK_BLEND_FACTOR_DST_COLOR;
            case gl::ONE_MINUS_DST_COLOR: return VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case gl::SRC_ALPHA_SATURATE: return VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case gl::CONSTANT_COLOR: return VK_BLEND_FACTOR_CONSTANT_COLOR;
            case gl::CONSTANT_ALPHA: return VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case gl::ONE_MINUS_CONSTANT_COLOR: return VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case gl::ONE_MINUS_CONSTANT_ALPHA: return VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            default: return VK_BLEND_FACTOR_ONE;
        }
    }

    u64 RenderState::computePipelineState() const {
        u64 state = 0;
        if (blend) {
            state |= key::BLEND | (static_cast<u64>(blendFactor(blendSourceColor)) << key::SOURCE_COLOR_SHIFT) |
                    (static_cast<u64>(blendFactor(blendDestinationColor)) << key::DESTINATION_COLOR_SHIFT) |
                    (static_cast<u64>(blendFactor(blendSourceAlpha)) << key::SOURCE_ALPHA_SHIFT) |
                    (static_cast<u64>(blendFactor(blendDestinationAlpha)) << key::DESTINATION_ALPHA_SHIFT);
        }
        if (depthTest) state |= key::DEPTH_TEST;
        if (depthMaskValue) state |= key::DEPTH_WRITE;
        state |= static_cast<u64>((depthFunctionValue - gl::NEVER) & 7) << key::COMPARE_SHIFT;
        u32 cullMode = VK_CULL_MODE_NONE;
        if (cull) {
            cullMode = cullFaceValue == gl::FRONT
                           ? VK_CULL_MODE_FRONT_BIT
                           : cullFaceValue == gl::FRONT_AND_BACK
                                 ? VK_CULL_MODE_FRONT_AND_BACK
                                 : VK_CULL_MODE_BACK_BIT;
        }
        state |= static_cast<u64>(cullMode) << key::CULL_SHIFT;
        state |= static_cast<u64>(colorMaskValue) << key::COLOR_MASK_SHIFT;
        if (colorLogicOp) state |= key::LOGIC_OP | (
                              static_cast<u64>((logicOpMode - gl::CLEAR) & 15) << key::LOGIC_OP_SHIFT);
        if (flatShading) state |= key::FLAT;
        if (offsetFill) state |= key::OFFSET_FILL;
        if (offsetLine) state |= key::OFFSET_LINE;
        return state;
    }
} // namespace at
