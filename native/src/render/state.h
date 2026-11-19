#pragma once

#include "core/common.h"
#include "render/matrix.h"

#include <memory>
#include <vector>

namespace at {
    class Texture;

    namespace gl {
        constexpr i32 NEVER = 0x0200, LESS = 0x0201, ALWAYS = 0x0207;
        constexpr i32 FRONT = 0x0404, BACK = 0x0405, FRONT_AND_BACK = 0x0408;
        constexpr i32 ALPHA_TEST = 0x0BC0, LIGHTING = 0x0B50, COLOR_MATERIAL = 0x0B57, NORMALIZE = 0x0BA1,
                RESCALE_NORMAL = 0x803A;
        constexpr i32 FOG = 0x0B60, DEPTH_TEST = 0x0B71, BLEND = 0x0BE2, CULL_FACE = 0x0B44, POLYGON_OFFSET_FILL =
                        0x8037;
        constexpr i32 POLYGON_OFFSET_LINE = 0x2A02, COLOR_LOGIC_OP = 0x0BF2, TEXTURE_2D = 0x0DE1, LIGHT0 = 0x4000,
                LIGHT7 = 0x4007;
        constexpr i32 TEXTURE_GEN_S = 0x0C60, TEXTURE_GEN_Q = 0x0C63;
        constexpr i32 AMBIENT_AND_DIFFUSE = 0x1602, AMBIENT = 0x1200, DIFFUSE = 0x1201, SPECULAR = 0x1202, EMISSION =
                        0x1600;
        constexpr i32 POSITION = 0x1203;
        constexpr i32 EXP = 0x0800, LINEAR = 0x2601;
        constexpr i32 EYE_LINEAR = 0x2400, OBJECT_PLANE = 0x2501;
        constexpr i32 FLAT = 0x1D00, SMOOTH = 0x1D01;
        constexpr i32 CLEAR = 0x1500, COPY = 0x1503;
        constexpr i32 ZERO = 0, ONE = 1, SRC_COLOR = 0x0300, ONE_MINUS_SRC_COLOR = 0x0301, SRC_ALPHA = 0x0302;
        constexpr i32 ONE_MINUS_SRC_ALPHA = 0x0303, DST_ALPHA = 0x0304, ONE_MINUS_DST_ALPHA = 0x0305, DST_COLOR =
                        0x0306;
        constexpr i32 ONE_MINUS_DST_COLOR = 0x0307, SRC_ALPHA_SATURATE = 0x0308;
        constexpr i32 CONSTANT_COLOR = 0x8001, ONE_MINUS_CONSTANT_COLOR = 0x8002, CONSTANT_ALPHA = 0x8003,
                ONE_MINUS_CONSTANT_ALPHA = 0x8004;
        constexpr i32 MODELVIEW = 0x1700, PROJECTION = 0x1701;
        constexpr i32 COLOR_BUFFER_BIT = 0x4000, DEPTH_BUFFER_BIT = 0x0100;
        constexpr i32 POINTS = 0, LINES = 1, LINE_LOOP = 2, LINE_STRIP = 3, TRIANGLES = 4, TRIANGLE_STRIP = 5,
                TRIANGLE_FAN = 6;
        constexpr i32 QUADS = 7, QUAD_STRIP = 8, POLYGON = 9;
    } // namespace gl

    class RenderState {
    public:
        static constexpr u32 PIPELINE = 1;
        static constexpr u32 TRANSFORM = 2;
        static constexpr u32 ENVIRONMENT = 4;
        static constexpr u32 TEXTURES = 8;
        static constexpr u32 VIEWPORT = 16;
        static constexpr u32 LINE_WIDTH = 32;
        static constexpr u32 DEPTH_BIAS = 64;
        static constexpr u32 ALL = 127;
        static constexpr int UNITS = 8;

        RenderState();

        void setCapability(i32 capability, bool enabled);

        void alphaFunction(i32 function, float reference);

        void colorMaterial(i32 mode);

        void light(i32 light, i32 parameter, float x, float y, float z, float w);

        void lightModelAmbient(float r, float g, float b, float a);

        void depthFunction(i32 function);

        void depthMask(bool mask);

        void blendFunction(i32 sourceColor, i32 destinationColor, i32 sourceAlpha, i32 destinationAlpha);

        void cullFace(i32 face);

        void polygonOffset(float factor, float units);

        void logicOp(i32 mode);

        void colorMask(u32 mask);

        void shadeModel(i32 mode);

        void fogMode(i32 mode) {
            fogModeValue = mode;
            dirty |= ENVIRONMENT;
        }

        void fogDensity(float density) {
            fogDensityValue = density;
            dirty |= ENVIRONMENT;
        }

        void fogStart(float start) {
            fogStartValue = start;
            dirty |= ENVIRONMENT;
        }

        void fogEnd(float end) {
            fogEndValue = end;
            dirty |= ENVIRONMENT;
        }

        void fogColor(float r, float g, float b, float a) {
            set(fogColorValue, r, g, b, a);
            dirty |= ENVIRONMENT;
        }

        void fogRadial(bool radial) {
            fogRadialValue = radial;
            dirty |= ENVIRONMENT;
        }

        void texGenMode(i32 coordinate, i32 mode) {
            texGenModes[coordinate & 3] = mode;
            dirty |= ENVIRONMENT;
        }

        void texGenPlane(i32 coordinate, i32 parameter, float x, float y, float z, float w);

        void activeTexture(i32 unit) { activeUnit = unit & (UNITS - 1); }

        void bindTexture(Texture *texture) {
            if (bound[activeUnit] == texture) return;
            bound[activeUnit] = texture;
            dirty |= TEXTURES;
        }

        void texturesChanged() { dirty |= TEXTURES; }

        void viewport(i32 x, i32 y, i32 width, i32 height) {
            viewportX = x;
            viewportY = y;
            viewportWidth = width;
            viewportHeight = height;
            dirty |= VIEWPORT;
        }

        void lineWidth(float width) {
            lineWidthValue = width;
            dirty |= LINE_WIDTH;
        }

        void clearColor(float r, float g, float b, float a) {
            set(clearColorValue, clamp(r, 0.0f, 1.0f), clamp(g, 0.0f, 1.0f), clamp(b, 0.0f, 1.0f),
                clamp(a, 0.0f, 1.0f));
        }

        void clearDepth(float depth) { clearDepthValue = clamp(depth, 0.0f, 1.0f); }

        void color(float r, float g, float b, float a) {
            set(colorValue, r, g, b, a);
            dirty |= TRANSFORM;
        }

        void normal(float x, float y, float z) {
            normalValue[0] = x;
            normalValue[1] = y;
            normalValue[2] = z;
            dirty |= TRANSFORM;
        }

        void drawIds(i32 entity, i32 blockEntity, i32 item) {
            drawIdValues[0] = entity;
            drawIdValues[1] = blockEntity;
            drawIdValues[2] = item;
            dirty |= TRANSFORM;
        }

        void texCoord(i32 unit, float s, float t) {
            set(texCoords + (unit & (UNITS - 1)) * 4, s, t, 0.0f, 1.0f);
            dirty |= TRANSFORM;
        }

        void overlay(float r, float g, float b, float a) {
            set(overlayColor, r, g, b, a);
            overlayEnabled = true;
            dirty |= ENVIRONMENT;
        }

        void clearOverlay() {
            overlayEnabled = false;
            dirty |= ENVIRONMENT;
        }

        void pushAttributes();

        void popAttributes();

        void matrixMode(i32 mode) { matrixModeValue = mode; }

        MatrixStack &current() {
            if (matrixModeValue == gl::MODELVIEW) return modelView;
            if (matrixModeValue == gl::PROJECTION) return projection;
            return *textureStacks[activeUnit];
        }

        void transformChanged() {
            if (matrixModeValue == gl::MODELVIEW) dirty |= TRANSFORM;
            else if (matrixModeValue == gl::PROJECTION) dirty |= ENVIRONMENT;
            else if (activeUnit < 2) dirty |= ENVIRONMENT;
        }

        [[nodiscard]] MatrixStack &texture(int unit) const { return *textureStacks[unit]; }

        u64 pipelineState() {
            if (!pipelineStateValid) {
                pipelineStateCache = computePipelineState();
                pipelineStateValid = true;
            }
            return pipelineStateCache;
        }

        [[nodiscard]] bool textureEnabled(int unit) const { return textureEnabledValue[unit]; }
        [[nodiscard]] bool cullsBack() const { return cull && cullFaceValue == gl::BACK; }
        [[nodiscard]] bool blending() const { return blend; }

        void blendFactors(i32 (&out)[4]) const {
            out[0] = blendSourceColor;
            out[1] = blendDestinationColor;
            out[2] = blendSourceAlpha;
            out[3] = blendDestinationAlpha;
        }
        [[nodiscard]] Texture *boundTexture(int unit) const { return bound[unit]; }
        [[nodiscard]] Texture *activeBoundTexture() const { return bound[activeUnit]; }

        void forget(const Texture *texture) {
            for (Texture *&slot: bound) {
                if (slot == texture) {
                    slot = nullptr;
                    dirty |= TEXTURES;
                }
            }
        }

        u32 dirty = ALL;

        bool alphaTest = false;
        i32 alphaFunctionValue = gl::ALWAYS;
        float alphaReference = 0.0f;
        bool lighting = false;
        bool lights[8] = {};
        bool colorMaterialEnabled = false;
        i32 colorMaterialMode = gl::AMBIENT_AND_DIFFUSE;
        float lightPositions[8 * 4] = {};
        float lightDiffuse[8 * 4] = {};
        float lightAmbient[8 * 4] = {};
        float lightModelAmbientValue[4] = {0.2f, 0.2f, 0.2f, 1.0f};
        bool normalizeEnabled = false;
        bool rescaleNormal = false;
        bool depthMaskValue = true;
        float offsetFactor = 0.0f;
        float offsetUnits = 0.0f;
        bool fogEnabled = false;
        i32 fogModeValue = gl::EXP;
        float fogDensityValue = 1.0f;
        float fogStartValue = 0.0f;
        float fogEndValue = 1.0f;
        bool fogRadialValue = false;
        float fogColorValue[4] = {};
        bool texGenEnabled[4] = {};
        i32 texGenModes[4] = {gl::EYE_LINEAR, gl::EYE_LINEAR, gl::EYE_LINEAR, gl::EYE_LINEAR};
        float objectPlanes[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        float eyePlanes[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int activeUnit = 0;
        bool flatShading = false;
        i32 viewportX = 0, viewportY = 0, viewportWidth = 0, viewportHeight = 0;
        float lineWidthValue = 1.0f;
        float clearColorValue[4] = {};
        float clearDepthValue = 1.0f;
        float colorValue[4] = {1, 1, 1, 1};
        float normalValue[3] = {0, 0, 1};
        i32 drawIdValues[3] = {-1, -1, -1};
        float texCoords[UNITS * 4] = {};
        bool overlayEnabled = false;
        float overlayColor[4] = {};
        u32 colorMaskValue = 0xF;

        MatrixStack modelView{32};
        MatrixStack projection{4};

    private:
        struct Attributes {
            bool lighting;
            bool lights[8];
            bool colorMaterial;
            i32 colorMaterialMode;
            float lightModelAmbient[4];
            float lightPositions[32];
            float lightDiffuse[32];
            float lightAmbient[32];
            bool flatShading;
            u64 capabilities;
        };

        [[nodiscard]] u64 capabilities() const;

        [[nodiscard]] u64 computePipelineState() const;

        void invalidatePipeline() {
            pipelineStateValid = false;
            dirty |= PIPELINE;
        }

        static void set(float *target, float x, float y, float z, float w) {
            target[0] = x;
            target[1] = y;
            target[2] = z;
            target[3] = w;
        }

        bool depthTest = false;
        i32 depthFunctionValue = gl::LESS;
        bool blend = false;
        i32 blendSourceColor = gl::ONE, blendDestinationColor = gl::ZERO;
        i32 blendSourceAlpha = gl::ONE, blendDestinationAlpha = gl::ZERO;
        bool cull = false;
        i32 cullFaceValue = gl::BACK;
        bool offsetFill = false;
        bool offsetLine = false;
        bool colorLogicOp = false;
        i32 logicOpMode = gl::COPY;
        bool textureEnabledValue[UNITS] = {};
        Texture *bound[UNITS] = {};
        i32 matrixModeValue = gl::MODELVIEW;
        std::unique_ptr<MatrixStack> textureStacks[UNITS];
        u64 pipelineStateCache = 0;
        bool pipelineStateValid = false;
        std::vector<Attributes> saved;
    };
} // namespace at
