@file:Suppress("NOTHING_TO_INLINE")

package yt.ren.astatine.render

import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory

// I wanted to avoid GL emulation, but seems like that's effectively impossible
object GL {
    private const val HEADER = 16
    private const val CAPACITY = (4 shl 20) - 64

    private const val ENABLE = 1
    private const val DISABLE = 2
    private const val ALPHA_FUNC = 3
    private const val COLOR_MATERIAL = 4
    private const val DEPTH_FUNC = 5
    private const val DEPTH_MASK = 6
    private const val BLEND_FUNC = 7
    private const val FOG_MODE = 8
    private const val FOG_DENSITY = 9
    private const val FOG_START = 10
    private const val FOG_END = 11
    private const val FOG_COLOR = 12
    private const val FOG_RADIAL = 13
    private const val CULL_FACE = 14
    private const val POLYGON_OFFSET = 15
    private const val LOGIC_OP = 16
    private const val COLOR_MASK = 17
    private const val TEXGEN_MODE = 18
    private const val TEXGEN_PLANE = 19
    private const val ACTIVE_TEXTURE = 20
    private const val BIND_TEXTURE = 21
    private const val SHADE_MODEL = 22
    private const val VIEWPORT = 23
    private const val LINE_WIDTH = 24
    private const val CLEAR_COLOR = 25
    private const val CLEAR_DEPTH = 26
    private const val COLOR = 27
    private const val NORMAL = 28
    private const val TEXCOORD = 29
    private const val OVERLAY = 30
    private const val CLEAR_OVERLAY = 31
    private const val PUSH_ATTRIB = 32
    private const val POP_ATTRIB = 33
    private const val MATRIX_MODE = 34
    private const val LOAD_IDENTITY = 35
    private const val PUSH_MATRIX = 36
    private const val POP_MATRIX = 37
    private const val ORTHO = 38
    private const val ROTATE = 39
    private const val SCALE = 40
    private const val TRANSLATE = 41
    private const val MULT_MATRIX = 42
    private const val PERSPECTIVE = 43
    private const val LIGHT = 44
    private const val LIGHT_MODEL_AMBIENT = 45
    private const val TEXTURE_PARAMETER = 46
    private const val CLEAR = 47
    private const val BIND_FRAMEBUFFER = 48
    private const val RELEASE_MESH = 49
    private const val DRAW_IDS = 50

    private var cell = 0L

    fun attach() {
        cell = Native.streamAddress()
    }

    private inline fun begin(code: Int, slots: Int): Long {
        val bytes = 4 + slots * 4
        var base = Memory.getLong(cell)
        var used = Memory.getInt(base)
        if (used + bytes > CAPACITY) {
            Native.flush()
            base = Memory.getLong(cell)
            used = Memory.getInt(base)
        }
        Memory.putInt(base, used + bytes)
        val at = base + HEADER + used
        Memory.putInt(at, code)
        return at + 4
    }

    private inline fun op(code: Int) {
        begin(code, 0)
    }

    private inline fun op(code: Int, a: Int) {
        Memory.putInt(begin(code, 1), a)
    }

    private inline fun op(code: Int, a: Float) {
        Memory.putFloat(begin(code, 1), a)
    }

    private inline fun op(code: Int, a: Long) {
        Memory.putLong(begin(code, 2), a)
    }

    private inline fun op(code: Int, a: Int, b: Int) {
        val at = begin(code, 2)
        Memory.putInt(at, a)
        Memory.putInt(at + 4, b)
    }

    private inline fun op(code: Int, a: Float, b: Float, c: Float) {
        val at = begin(code, 3)
        Memory.putFloat(at, a)
        Memory.putFloat(at + 4, b)
        Memory.putFloat(at + 8, c)
    }

    private inline fun op(code: Int, a: Float, b: Float, c: Float, d: Float) {
        val at = begin(code, 4)
        Memory.putFloat(at, a)
        Memory.putFloat(at + 4, b)
        Memory.putFloat(at + 8, c)
        Memory.putFloat(at + 12, d)
    }

    private inline fun op(code: Int, a: Int, b: Int, c: Float, d: Float, e: Float, f: Float) {
        val at = begin(code, 6)
        Memory.putInt(at, a)
        Memory.putInt(at + 4, b)
        Memory.putFloat(at + 8, c)
        Memory.putFloat(at + 12, d)
        Memory.putFloat(at + 16, e)
        Memory.putFloat(at + 20, f)
    }

    @JvmStatic
    fun enable(capability: Int) {
        DrawState.enable(capability, true)
        op(ENABLE, capability)
    }

    @JvmStatic
    fun disable(capability: Int) {
        DrawState.enable(capability, false)
        op(DISABLE, capability)
    }

    @JvmStatic
    fun alphaFunc(function: Int, reference: Float) {
        DrawState.alphaFunc(function, reference)
        val at = begin(ALPHA_FUNC, 2)
        Memory.putInt(at, function)
        Memory.putFloat(at + 4, reference)
    }

    @JvmStatic
    fun colorMaterial(mode: Int) = op(COLOR_MATERIAL, mode)

    @JvmStatic
    fun depthFunc(function: Int) {
        DrawState.depthFunc(function)
        op(DEPTH_FUNC, function)
    }

    @JvmStatic
    fun depthMask(mask: Boolean) {
        DrawState.depthMask(mask)
        op(DEPTH_MASK, if (mask) 1 else 0)
    }

    @JvmStatic
    fun blendFunc(sourceColor: Int, destinationColor: Int, sourceAlpha: Int, destinationAlpha: Int) {
        DrawState.blendFunc(sourceColor, destinationColor, sourceAlpha, destinationAlpha)
        val at = begin(BLEND_FUNC, 4)
        Memory.putInt(at, sourceColor)
        Memory.putInt(at + 4, destinationColor)
        Memory.putInt(at + 8, sourceAlpha)
        Memory.putInt(at + 12, destinationAlpha)
    }

    @JvmStatic
    fun fogMode(mode: Int) = op(FOG_MODE, mode)

    @JvmStatic
    fun fogDensity(density: Float) = op(FOG_DENSITY, density)

    @JvmStatic
    fun fogStart(start: Float) = op(FOG_START, start)

    @JvmStatic
    fun fogEnd(end: Float) = op(FOG_END, end)

    @JvmStatic
    fun fogColor(r: Float, g: Float, b: Float, a: Float) = op(FOG_COLOR, r, g, b, a)

    @JvmStatic
    fun fogRadial(radial: Boolean) = op(FOG_RADIAL, if (radial) 1 else 0)

    @JvmStatic
    fun cullFace(face: Int) {
        DrawState.cullFace(face)
        op(CULL_FACE, face)
    }

    @JvmStatic
    fun polygonOffset(factor: Float, units: Float) {
        DrawState.polygonOffset(factor, units)
        val at = begin(POLYGON_OFFSET, 2)
        Memory.putFloat(at, factor)
        Memory.putFloat(at + 4, units)
    }

    @JvmStatic
    fun logicOp(mode: Int) {
        DrawState.logicOp(mode)
        op(LOGIC_OP, mode)
    }

    @JvmStatic
    fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) {
        val mask = (if (r) 1 else 0) or (if (g) 2 else 0) or (if (b) 4 else 0) or (if (a) 8 else 0)
        DrawState.colorMask(mask)
        op(COLOR_MASK, mask)
    }

    @JvmStatic
    fun texGenMode(coordinate: Int, mode: Int) = op(TEXGEN_MODE, coordinate, mode)

    @JvmStatic
    fun texGenPlane(coordinate: Int, parameter: Int, x: Float, y: Float, z: Float, w: Float) =
        op(TEXGEN_PLANE, coordinate, parameter, x, y, z, w)

    @JvmStatic
    fun activeTexture(unit: Int) {
        GLState.activeTexture(unit)
        DrawState.activeTexture(unit)
        op(ACTIVE_TEXTURE, unit)
    }

    @JvmStatic
    fun bindTexture(texture: Long) {
        DrawState.bindTexture(texture)
        op(BIND_TEXTURE, texture)
    }

    @JvmStatic
    fun shadeModel(mode: Int) = op(SHADE_MODEL, mode)

    @JvmStatic
    fun viewport(x: Int, y: Int, width: Int, height: Int) {
        GLState.viewport(x, y, width, height)
        val at = begin(VIEWPORT, 4)
        Memory.putInt(at, x)
        Memory.putInt(at + 4, y)
        Memory.putInt(at + 8, width)
        Memory.putInt(at + 12, height)
    }

    @JvmStatic
    fun lineWidth(width: Float) = op(LINE_WIDTH, width)

    @JvmStatic
    fun clearColor(r: Float, g: Float, b: Float, a: Float) = op(CLEAR_COLOR, r, g, b, a)

    @JvmStatic
    fun clearDepth(depth: Float) = op(CLEAR_DEPTH, depth)

    @JvmStatic
    fun color(r: Float, g: Float, b: Float, a: Float) = op(COLOR, r, g, b, a)

    @JvmStatic
    fun normal(x: Float, y: Float, z: Float) = op(NORMAL, x, y, z)

    @JvmStatic
    fun texCoord(unit: Int, s: Float, t: Float) {
        val at = begin(TEXCOORD, 3)
        Memory.putInt(at, unit)
        Memory.putFloat(at + 4, s)
        Memory.putFloat(at + 8, t)
    }

    @JvmStatic
    fun drawIds(entity: Int, blockEntity: Int, item: Int) {
        val at = begin(DRAW_IDS, 3)
        Memory.putInt(at, entity)
        Memory.putInt(at + 4, blockEntity)
        Memory.putInt(at + 8, item)
    }

    @JvmStatic
    fun overlay(r: Float, g: Float, b: Float, a: Float) = op(OVERLAY, r, g, b, a)

    @JvmStatic
    fun clearOverlay() = op(CLEAR_OVERLAY)

    @JvmStatic
    fun pushAttributes() {
        DrawState.push()
        op(PUSH_ATTRIB)
    }

    @JvmStatic
    fun popAttributes() {
        DrawState.pop()
        op(POP_ATTRIB)
    }

    @JvmStatic
    fun matrixMode(mode: Int) {
        GLState.matrixMode(mode)
        op(MATRIX_MODE, mode)
    }

    @JvmStatic
    fun loadIdentity() {
        GLState.loadIdentity()
        op(LOAD_IDENTITY)
    }

    @JvmStatic
    fun pushMatrix() {
        GLState.pushMatrix()
        op(PUSH_MATRIX)
    }

    @JvmStatic
    fun popMatrix() {
        GLState.popMatrix()
        op(POP_MATRIX)
    }

    @JvmStatic
    fun ortho(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        GLState.ortho(left, right, bottom, top, near, far)
        val at = begin(ORTHO, 6)
        Memory.putFloat(at, left)
        Memory.putFloat(at + 4, right)
        Memory.putFloat(at + 8, bottom)
        Memory.putFloat(at + 12, top)
        Memory.putFloat(at + 16, near)
        Memory.putFloat(at + 20, far)
    }

    @JvmStatic
    fun rotate(angle: Float, x: Float, y: Float, z: Float) {
        GLState.rotate(angle, x, y, z)
        op(ROTATE, angle, x, y, z)
    }

    @JvmStatic
    fun scale(x: Float, y: Float, z: Float) {
        GLState.scale(x, y, z)
        op(SCALE, x, y, z)
    }

    @JvmStatic
    fun translate(x: Float, y: Float, z: Float) {
        GLState.translate(x, y, z)
        op(TRANSLATE, x, y, z)
    }

    @JvmStatic
    fun multMatrix(matrix: Long) {
        GLState.multMatrix(matrix)
        Memory.copy(matrix, begin(MULT_MATRIX, 16), 64)
    }

    @JvmStatic
    fun perspective(fovy: Float, aspect: Float, near: Float, far: Float) {
        GLState.perspective(fovy, aspect, near, far)
        op(PERSPECTIVE, fovy, aspect, near, far)
    }

    @JvmStatic
    fun light(light: Int, parameter: Int, x: Float, y: Float, z: Float, w: Float) = op(LIGHT, light, parameter, x, y, z, w)

    @JvmStatic
    fun lightModelAmbient(r: Float, g: Float, b: Float, a: Float) = op(LIGHT_MODEL_AMBIENT, r, g, b, a)

    @JvmStatic
    fun textureParameter(name: Int, value: Float) {
        val at = begin(TEXTURE_PARAMETER, 2)
        Memory.putInt(at, name)
        Memory.putFloat(at + 4, value)
    }

    @JvmStatic
    fun clear(mask: Int) = op(CLEAR, mask)

    @JvmStatic
    fun bindFramebuffer(framebuffer: Long) = op(BIND_FRAMEBUFFER, framebuffer)

    @JvmStatic
    fun releaseMesh(mesh: Long) = op(RELEASE_MESH, mesh)
}
