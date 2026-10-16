#include "render/commands.h"

#include "core/error.h"
#include "render/geometry.h"
#include "render/renderer.h"
#include "render/targets.h"
#include "render/texture.h"
#include "terrain/terrain.h"

#include <algorithm>
#include <string>

namespace at {
    void Renderer::drain() {
        waitIdle();
    }

    Renderer::Chunk Renderer::takeChunk() {
        {
            std::lock_guard guard(queueLock);
            if (!freeChunks.empty()) {
                Chunk chunk = freeChunks.back();
                freeChunks.pop_back();
                return chunk;
            }
        }
        Chunk chunk{static_cast<u8 *>(std::calloc(1, CHUNK_SIZE)), CHUNK_SIZE};
        if (!chunk.data) fail("Out of memory for a command chunk");
        return chunk;
    }

    void Renderer::recycle(const Chunk &chunk) {
        if (chunk.capacity != CHUNK_SIZE) {
            std::free(chunk.data);
            return;
        }
        store<u32>(chunk.data, 0);
        freeChunks.push_back(chunk);
    }

    void Renderer::enqueue(const Chunk &chunk) {
        {
            std::lock_guard guard(queueLock);
            queue.push_back(chunk);
        }
        queueReady.notify_one();
    }

    void Renderer::rethrow() {
        std::string message;
        {
            std::lock_guard guard(queueLock);
            message.swap(threadError);
        }
        if (!message.empty()) fail("Render thread failed: " + message);
    }

    void Renderer::threadLoop() {
        while (true) {
            Chunk chunk;
            {
                std::unique_lock guard(queueLock);
                queueReady.wait(guard, [this] { return stopping || !queue.empty(); });
                if (queue.empty()) return;
                chunk = queue.front();
                queue.pop_front();
                executing = true;
            }
            try {
                executeChunk(chunk);
            } catch (const std::exception &error) {
                std::lock_guard guard(queueLock);
                if (threadError.empty()) threadError = error.what();
            } catch (...) {
                std::lock_guard guard(queueLock);
                if (threadError.empty()) threadError = "unknown native error";
            }
            {
                std::lock_guard guard(queueLock);
                executing = false;
                recycle(chunk);
            }
            queueIdle.notify_all();
        }
    }

    void Renderer::startThread() {
        if (threaded) return;
        stopping = false;
        threaded = true;
        renderThread = std::thread([this] { threadLoop(); });
    }

    void Renderer::stopThread() {
        if (!threaded) return;
        publish();
        {
            std::lock_guard guard(queueLock);
            stopping = true;
        }
        queueReady.notify_all();
        renderThread.join();
        threaded = false;
    }

    void Renderer::waitIdle() {
        publish();
        if (threaded) {
            std::unique_lock guard(queueLock);
            queueIdle.wait(guard, [this] { return (queue.empty() && !executing) || !threadError.empty(); });
        }
        rethrow();
    }

    void Renderer::finishFrame() {
        framesSubmitted++;
        publish();
        if (threaded) {
            std::unique_lock guard(queueLock);
            queueIdle.wait(guard, [this] {
                return framesSubmitted - framesPresented <= FRAMES_AHEAD || !threadError.empty() ||
                       (queue.empty() && !executing);
            });
        }
        rethrow();
    }

    void Renderer::executeChunk(const Chunk &chunk) {
        u32 used = load<u32>(chunk.data);
        if (used == 0) return;
        store<u32>(chunk.data, 0);
        execute(chunk.data + op::HEADER, chunk.data + op::HEADER + used);
    }

    void Renderer::publish() {
        if (pendingDedicated.data) flushDedicated();
        if (!threaded) {
            executeChunk(writing);
            return;
        }
        if (load<u32>(writing.data) == 0) return;
        enqueue(writing);
        writing = takeChunk();
        streamCellValue = static_cast<u64>(reinterpret_cast<std::uintptr_t>(writing.data));
    }

    u8 *Renderer::append(u32 code, u32 bytes) {
        u32 padded = (bytes + 3u) & ~3u;
        u32 total = 8 + padded;
        if (op::HEADER + total > writing.capacity) {
            publish();
            Chunk dedicated{static_cast<u8 *>(std::calloc(1, op::HEADER + total)), op::HEADER + total};
            if (!dedicated.data) fail("Out of memory for a command of " + std::to_string(bytes) + " bytes");
            store<u32>(dedicated.data, total);
            u8 *at = dedicated.data + op::HEADER;
            store<u32>(at, code);
            store<u32>(at + 4, padded);
            pendingDedicated = dedicated;
            return at + 8;
        }
        if (pendingDedicated.data) flushDedicated();
        u32 used = load<u32>(writing.data);
        if (op::HEADER + used + total > writing.capacity) {
            publish();
            used = 0;
        }
        u8 *at = writing.data + op::HEADER + used;
        store<u32>(at, code);
        store<u32>(at + 4, padded);
        store<u32>(writing.data, used + total);
        return at + 8;
    }

    void Renderer::flushDedicated() {
        Chunk dedicated = pendingDedicated;
        pendingDedicated = {};
        if (threaded) {
            enqueue(dedicated);
            return;
        }
        try {
            executeChunk(dedicated);
        } catch (...) {
            std::free(dedicated.data);
            throw;
        }
        std::free(dedicated.data);
    }

    bool Renderer::canBlitSurface() const {
        return presenter && presenter->blitTarget.load(std::memory_order_relaxed);
    }

    void Renderer::executeNative(u32 code, const u8 *data, u32 bytes) {
        switch (code) {
            case op::DRAW: {
                auto command = load<op::Draw>(data);
                draw(command.layout, command.mode, data + sizeof(op::Draw), command.count);
                break;
            }
            case op::DRAW_MESH: {
                auto command = load<op::DrawMesh>(data);
                drawMesh(reinterpret_cast<Mesh *>(command.mesh), command.mode);
                break;
            }
            case op::TEXTURE_ALLOCATE: {
                auto command = load<op::TextureAllocate>(data);
                Texture *target = command.texture ? reinterpret_cast<Texture *>(command.texture) : state.activeBoundTexture();
                if (!target) break;
                u32 levels = static_cast<u32>(std::max(target->maxLevel, 0)) + 1;
                allocateTexture(target, std::min(levels, 17u), command.width, command.height);
                break;
            }
            case op::TEXTURE_UPLOAD: {
                auto command = load<op::TextureUpload>(data);
                Texture *target = command.texture ? reinterpret_cast<Texture *>(command.texture) : state.activeBoundTexture();
                if (!target) break;
                uploadTexture(target, command.level, command.x, command.y, command.width, command.height,
                              data + sizeof(op::TextureUpload), command.width);
                break;
            }
            case op::TEXTURE_DETACH:
                reinterpret_cast<Texture *>(load<op::Handle>(data).value)->detach();
                break;
            case op::COPY_FROM_TARGET: {
                auto command = load<op::CopyFromTarget>(data);
                if (Texture *texture = state.activeBoundTexture()) {
                    copyFromTarget(texture, command.level, command.destinationX, command.destinationY, command.x,
                                   command.y, command.width, command.height);
                }
                break;
            }
            case op::FRAMEBUFFER_RELEASE:
                releaseFramebuffer(reinterpret_cast<Framebuffer *>(load<op::Handle>(data).value));
                break;
            case op::FRAMEBUFFER_BLIT: {
                auto command = load<op::Blit>(data);
                blitToSurface(reinterpret_cast<Framebuffer *>(command.framebuffer), command.width, command.height);
                break;
            }
            case op::PRESENT:
                present();
                {
                    std::lock_guard guard(queueLock);
                    framesPresented++;
                }
                queueIdle.notify_all();
                break;
            case op::SET_VSYNC:
                setVsync(load<op::Flag>(data).value != 0);
                break;
            case op::TERRAIN_SETUP: {
                auto command = load<op::TerrainSetup>(data);
                terrain::SetupParameters parameters{
                    .x = command.x, .y = command.y, .z = command.z, .eyeHeight = command.eyeHeight,
                    .frame = command.frame, .lookFace = command.lookFace, .smartCull = (command.flags & 1) != 0,
                    .spectator = (command.flags & 2) != 0, .cameraInOpaque = (command.flags & 4) != 0
                };
                terrain->setup(parameters);
                terrain->publish(command.scheduleCap);
                break;
            }
            case op::TERRAIN_MARK_DIRTY: {
                auto command = load<op::TerrainBox>(data);
                terrain->markDirty(command.minX, command.minY, command.minZ, command.maxX, command.maxY, command.maxZ,
                                   command.important != 0, command.sequence);
                break;
            }
            case op::TERRAIN_ABORT: {
                auto command = load<op::TerrainAbort>(data);
                terrain->abort(command.x, command.y, command.z, command.version);
                break;
            }
            case op::TERRAIN_UPLOAD: {
                auto command = load<op::TerrainUpload>(data);
                terrain->upload(command.budget, command.nanos);
                break;
            }
            case op::TERRAIN_SORT:
                terrain->sortTranslucent(load<op::Flag>(data).value);
                break;
            case op::TERRAIN_DRAW: {
                u32 layer = load<op::Flag>(data).value & 3;
                terrain->setDrawn(layer, terrain->draw(layer));
                break;
            }
            default:
                fail("Corrupt command stream, native opcode " + std::to_string(code) + " with " +
                     std::to_string(bytes) + " bytes");
        }
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
                default:
                    if (code >= op::FIRST_NATIVE) {
                        u32 bytes = load<u32>(cursor + 4);
                        executeNative(code, cursor + 8, bytes);
                        slots = 1 + bytes / 4;
                        break;
                    }
                    fail("Corrupt command stream, opcode " + std::to_string(code));
            }
            cursor += 4 + slots * 4;
        }
    }
} // namespace at
