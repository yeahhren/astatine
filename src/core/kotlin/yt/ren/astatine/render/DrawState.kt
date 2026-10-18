package yt.ren.astatine.render

object DrawState {
    const val BLEND = 0x0BE2
    const val DEPTH_TEST = 0x0B71
    const val CULL_FACE = 0x0B44
    const val ALPHA_TEST = 0x0BC0
    const val TEXTURE_2D = 0x0DE1
    const val FOG = 0x0B60
    const val LIGHTING = 0x0B50
    const val POLYGON_OFFSET_FILL = 0x8037
    const val COLOR_LOGIC_OP = 0x0BF2
    private const val UNITS = 8

    private class Values {
        var blend = false
        var depthTest = false
        var cull = false
        var alphaTest = false
        var fog = false
        var lighting = false
        var polygonOffset = false
        var logicOp = false
        val textures = BooleanArray(UNITS)
        var alphaFunction = 0x0207
        var alphaReference = 0f
        var depthFunction = 0x0201
        var depthMask = true
        var sourceColor = 1
        var destinationColor = 0
        var sourceAlpha = 1
        var destinationAlpha = 0
        var cullFace = 0x0405
        var offsetFactor = 0f
        var offsetUnits = 0f
        var colorMask = 15
        var logicMode = 0x1503

        fun copy(): Values {
            val copy = Values()
            copy.blend = blend
            copy.depthTest = depthTest
            copy.cull = cull
            copy.alphaTest = alphaTest
            copy.fog = fog
            copy.lighting = lighting
            copy.polygonOffset = polygonOffset
            copy.logicOp = logicOp
            System.arraycopy(textures, 0, copy.textures, 0, UNITS)
            copy.alphaFunction = alphaFunction
            copy.alphaReference = alphaReference
            copy.depthFunction = depthFunction
            copy.depthMask = depthMask
            copy.sourceColor = sourceColor
            copy.destinationColor = destinationColor
            copy.sourceAlpha = sourceAlpha
            copy.destinationAlpha = destinationAlpha
            copy.cullFace = cullFace
            copy.offsetFactor = offsetFactor
            copy.offsetUnits = offsetUnits
            copy.colorMask = colorMask
            copy.logicMode = logicMode
            return copy
        }
    }

    private var values = Values()
    private val stack = ArrayDeque<Values>()
    private var unit = 0
    private val bound = LongArray(UNITS)

    @JvmStatic
    var version = 0
        private set

    private fun changed() {
        version++
    }

    fun enable(capability: Int, enabled: Boolean) {
        when (capability) {
            BLEND -> values.blend = enabled
            DEPTH_TEST -> values.depthTest = enabled
            CULL_FACE -> values.cull = enabled
            ALPHA_TEST -> values.alphaTest = enabled
            TEXTURE_2D -> values.textures[unit] = enabled
            FOG -> values.fog = enabled
            LIGHTING -> values.lighting = enabled
            POLYGON_OFFSET_FILL -> values.polygonOffset = enabled
            COLOR_LOGIC_OP -> values.logicOp = enabled
            else -> return
        }
        changed()
    }

    fun alphaFunc(function: Int, reference: Float) {
        values.alphaFunction = function
        values.alphaReference = reference
        changed()
    }

    fun depthFunc(function: Int) {
        values.depthFunction = function
        changed()
    }

    fun depthMask(mask: Boolean) {
        values.depthMask = mask
        changed()
    }

    fun blendFunc(sourceColor: Int, destinationColor: Int, sourceAlpha: Int, destinationAlpha: Int) {
        values.sourceColor = sourceColor
        values.destinationColor = destinationColor
        values.sourceAlpha = sourceAlpha
        values.destinationAlpha = destinationAlpha
        changed()
    }

    fun cullFace(face: Int) {
        values.cullFace = face
        changed()
    }

    fun polygonOffset(factor: Float, units: Float) {
        values.offsetFactor = factor
        values.offsetUnits = units
        changed()
    }

    fun colorMask(mask: Int) {
        values.colorMask = mask
        changed()
    }

    fun logicOp(mode: Int) {
        values.logicMode = mode
        changed()
    }

    fun activeTexture(value: Int) {
        unit = value and (UNITS - 1)
    }

    fun bindTexture(texture: Long) {
        bound[unit] = texture
        changed()
    }

    fun push() {
        stack.addLast(values.copy())
    }

    fun pop() {
        val previous = stack.removeLastOrNull() ?: return
        values.blend = previous.blend
        values.depthTest = previous.depthTest
        values.cull = previous.cull
        values.alphaTest = previous.alphaTest
        values.fog = previous.fog
        values.lighting = previous.lighting
        values.polygonOffset = previous.polygonOffset
        values.logicOp = previous.logicOp
        System.arraycopy(previous.textures, 0, values.textures, 0, UNITS)
        changed()
    }

    @JvmStatic
    fun blend() = values.blend

    @JvmStatic
    fun depthTest() = values.depthTest

    @JvmStatic
    fun cull() = values.cull

    @JvmStatic
    fun alphaTest() = values.alphaTest

    @JvmStatic
    fun fog() = values.fog

    @JvmStatic
    fun lighting() = values.lighting

    @JvmStatic
    fun polygonOffset() = values.polygonOffset

    @JvmStatic
    fun logicOpEnabled() = values.logicOp

    @JvmStatic
    fun logicMode() = values.logicMode

    @JvmStatic
    fun textureEnabled(unit: Int) = values.textures[unit and (UNITS - 1)]

    @JvmStatic
    fun boundTexture(unit: Int) = bound[unit and (UNITS - 1)]

    @JvmStatic
    fun alphaFunction() = values.alphaFunction

    @JvmStatic
    fun alphaReference() = values.alphaReference

    @JvmStatic
    fun depthFunction() = values.depthFunction

    @JvmStatic
    fun depthMask() = values.depthMask

    @JvmStatic
    fun sourceColor() = values.sourceColor

    @JvmStatic
    fun destinationColor() = values.destinationColor

    @JvmStatic
    fun sourceAlpha() = values.sourceAlpha

    @JvmStatic
    fun destinationAlpha() = values.destinationAlpha

    @JvmStatic
    fun cullFace() = values.cullFace

    @JvmStatic
    fun offsetFactor() = values.offsetFactor

    @JvmStatic
    fun offsetUnits() = values.offsetUnits

    @JvmStatic
    fun colorMask() = values.colorMask
}
