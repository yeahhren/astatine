#include "render/commands.h"

#include "core/error.h"
#include "render/geometry.h"
#include "render/renderer.h"
#include "render/targets.h"
#include "render/texture.h"

namespace at {
    void Renderer::drain() {
        u32 used = load<u32>(commandBuffer);
        if (used == 0) return;
        store<u32>(commandBuffer, 0);
        execute(commandBuffer + op::HEADER, commandBuffer + op::HEADER + used);
    }

    void Renderer::execute(const u8 *cursor, const u8 *end) {
        RenderState &s = state;
        auto i = [&](int slot) { return load<i32>(cursor + 4 + slot * 4); };
        auto f = [&](int slot) { return load<float>(cursor + 4 + slot * 4); };
        auto p = [&](int slot) { return load<u64>(cursor + 4 + slot * 4); };
        while (cursor < end) {
            u32 code = load<u32>(cursor);
            u32 slots = 0;
            switch (code) {
                case op::ENABLE: s.setCapability(i(0), true);
                    slots = 1;
                    break;
                case op::DISABLE: s.setCapability(i(0), false);
                    slots = 1;
                    break;
                case op::ALPHA_FUNC: s.alphaFunction(i(0), f(1));
                    slots = 2;
                    break;
                case op::COLOR_MATERIAL: s.colorMaterial(i(0));
                    slots = 1;
                    break;
                case op::DEPTH_FUNC: s.depthFunction(i(0));
                    slots = 1;
                    break;
                case op::DEPTH_MASK: s.depthMask(i(0) != 0);
                    slots = 1;
                    break;
                case op::BLEND_FUNC: s.blendFunction(i(0), i(1), i(2), i(3));
                    slots = 4;
                    break;
                case op::FOG_MODE: s.fogMode(i(0));
                    slots = 1;
                    break;
                case op::FOG_DENSITY: s.fogDensity(f(0));
                    slots = 1;
                    break;
                case op::FOG_START: s.fogStart(f(0));
                    slots = 1;
                    break;
                case op::FOG_END: s.fogEnd(f(0));
                    slots = 1;
                    break;
                case op::FOG_COLOR: s.fogColor(f(0), f(1), f(2), f(3));
                    slots = 4;
                    break;
                case op::FOG_RADIAL: s.fogRadial(i(0) != 0);
                    slots = 1;
                    break;
                case op::CULL_FACE: s.cullFace(i(0));
                    slots = 1;
                    break;
                case op::POLYGON_OFFSET: s.polygonOffset(f(0), f(1));
                    slots = 2;
                    break;
                case op::LOGIC_OP: s.logicOp(i(0));
                    slots = 1;
                    break;
                case op::COLOR_MASK: s.colorMask(static_cast<u32>(i(0)));
                    slots = 1;
                    break;
                case op::TEXGEN_MODE: s.texGenMode(i(0), i(1));
                    slots = 2;
                    break;
                case op::TEXGEN_PLANE: s.texGenPlane(i(0), i(1), f(2), f(3), f(4), f(5));
                    slots = 6;
                    break;
                case op::ACTIVE_TEXTURE: s.activeTexture(i(0));
                    slots = 1;
                    break;
                case op::BIND_TEXTURE: s.bindTexture(reinterpret_cast<Texture *>(p(0)));
                    slots = 2;
                    break;
                case op::SHADE_MODEL: s.shadeModel(i(0));
                    slots = 1;
                    break;
                case op::VIEWPORT: s.viewport(i(0), i(1), i(2), i(3));
                    slots = 4;
                    break;
                case op::LINE_WIDTH: s.lineWidth(f(0));
                    slots = 1;
                    break;
                case op::CLEAR_COLOR: s.clearColor(f(0), f(1), f(2), f(3));
                    slots = 4;
                    break;
                case op::CLEAR_DEPTH: s.clearDepth(f(0));
                    slots = 1;
                    break;
                case op::COLOR: s.color(f(0), f(1), f(2), f(3));
                    slots = 4;
                    break;
                case op::NORMAL: s.normal(f(0), f(1), f(2));
                    slots = 3;
                    break;
                case op::TEXCOORD: s.texCoord(i(0), f(1), f(2));
                    slots = 3;
                    break;
                case op::OVERLAY: s.overlay(f(0), f(1), f(2), f(3));
                    slots = 4;
                    break;
                case op::CLEAR_OVERLAY: s.clearOverlay();
                    break;
                case op::PUSH_ATTRIB: s.pushAttributes();
                    break;
                case op::POP_ATTRIB: s.popAttributes();
                    break;
                case op::MATRIX_MODE: s.matrixMode(i(0));
                    slots = 1;
                    break;
                case op::LOAD_IDENTITY:
                    s.current().identity();
                    s.transformChanged();
                    break;
                case op::PUSH_MATRIX: s.current().push();
                    break;
                case op::POP_MATRIX:
                    if (s.current().pop()) s.transformChanged();
                    break;
                case op::ORTHO:
                    mat4::ortho(s.current().top(), f(0), f(1), f(2), f(3), f(4), f(5));
                    s.transformChanged();
                    slots = 6;
                    break;
                case op::ROTATE:
                    mat4::rotate(s.current().top(), f(0), f(1), f(2), f(3));
                    s.transformChanged();
                    slots = 4;
                    break;
                case op::SCALE:
                    mat4::scale(s.current().top(), f(0), f(1), f(2));
                    s.transformChanged();
                    slots = 3;
                    break;
                case op::TRANSLATE:
                    mat4::translate(s.current().top(), f(0), f(1), f(2));
                    s.transformChanged();
                    slots = 3;
                    break;
                case op::MULT_MATRIX: {
                    float matrix[16];
                    std::memcpy(matrix, cursor + 4, 64);
                    mat4::multiply(s.current().top(), matrix);
                    s.transformChanged();
                    slots = 16;
                    break;
                }
                case op::PERSPECTIVE:
                    mat4::perspective(s.current().top(), f(0), f(1), f(2), f(3));
                    s.transformChanged();
                    slots = 4;
                    break;
                case op::LIGHT: s.light(i(0), i(1), f(2), f(3), f(4), f(5));
                    slots = 6;
                    break;
                case op::LIGHT_MODEL_AMBIENT: s.lightModelAmbient(f(0), f(1), f(2), f(3));
                    slots = 4;
                    break;
                case op::TEXTURE_PARAMETER: {
                    Texture *texture = s.activeBoundTexture();
                    if (texture && texture->parameter(i(0), f(1))) s.texturesChanged();
                    slots = 2;
                    break;
                }
                case op::CLEAR: clear(i(0));
                    slots = 1;
                    break;
                case op::BIND_FRAMEBUFFER: {
                    if (auto *framebuffer = reinterpret_cast<Framebuffer *>(p(0))) bindTarget(framebuffer);
                    else bindSurface();
                    slots = 2;
                    break;
                }
                case op::RELEASE_MESH: releaseMesh(reinterpret_cast<Mesh *>(p(0)));
                    slots = 2;
                    break;
                default: fail("Corrupt command stream, opcode " + std::to_string(code));
            }
            cursor += 4 + slots * 4;
        }
    }
} // namespace at
