package yt.ren.astatine.render

import yt.ren.astatine.memory.Memory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GLState {
    private const val MODELVIEW = 0x1700
    private const val PROJECTION = 0x1701
    private const val MODELVIEW_MATRIX = 0x0BA6
    private const val PROJECTION_MATRIX = 0x0BA7
    private const val TEXTURE_MATRIX = 0x0BA8
    private const val UNITS = 8

    private class Stack(private val depth: Int) {
        val data = FloatArray(depth * 16)
        var offset = 0

        init {
            identity()
        }

        fun identity() {
            data.fill(0f, offset, offset + 16)
            data[offset] = 1f
            data[offset + 5] = 1f
            data[offset + 10] = 1f
            data[offset + 15] = 1f
        }

        fun push() {
            if (offset + 16 >= depth * 16) return
            System.arraycopy(data, offset, data, offset + 16, 16)
            offset += 16
        }

        fun pop() {
            if (offset > 0) offset -= 16
        }
    }

    private val modelView = Stack(32)
    private val projection = Stack(4)
    private val textures = Array(UNITS) { Stack(10) }
    private var mode = MODELVIEW
    private var unit = 0
    private val viewport = IntArray(4)
    private val operand = FloatArray(16)
    private val result = FloatArray(16)

    private fun current(): Stack = when (mode) {
        MODELVIEW -> modelView
        PROJECTION -> projection
        else -> textures[unit]
    }

    private fun multiply(stack: Stack, r: FloatArray) {
        val m = stack.data
        val o = stack.offset
        for (row in 0 until 4) {
            val m0 = m[o + row]
            val m1 = m[o + 4 + row]
            val m2 = m[o + 8 + row]
            val m3 = m[o + 12 + row]
            for (column in 0 until 4) {
                val c = column * 4
                result[c + row] = m0 * r[c] + m1 * r[c + 1] + m2 * r[c + 2] + m3 * r[c + 3]
            }
        }
        System.arraycopy(result, 0, m, o, 16)
    }

    fun matrixMode(value: Int) {
        mode = value
    }

    fun activeTexture(value: Int) {
        unit = value and (UNITS - 1)
    }

    fun loadIdentity() = current().identity()

    fun pushMatrix() = current().push()

    fun popMatrix() = current().pop()

    fun translate(x: Float, y: Float, z: Float) {
        val stack = current()
        val m = stack.data
        val o = stack.offset
        for (row in 0 until 4) m[o + 12 + row] += m[o + row] * x + m[o + 4 + row] * y + m[o + 8 + row] * z
    }

    fun scale(x: Float, y: Float, z: Float) {
        val stack = current()
        val m = stack.data
        val o = stack.offset
        for (row in 0 until 4) {
            m[o + row] *= x
            m[o + 4 + row] *= y
            m[o + 8 + row] *= z
        }
    }

    fun rotate(angle: Float, x: Float, y: Float, z: Float) {
        val length = sqrt(x * x + y * y + z * z)
        if (length == 0f) return
        val nx = x / length
        val ny = y / length
        val nz = z / length
        val radians = angle * (Math.PI / 180.0)
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val k = 1f - c
        val r = operand
        r.fill(0f)
        r[0] = nx * nx * k + c
        r[1] = ny * nx * k + nz * s
        r[2] = nx * nz * k - ny * s
        r[4] = nx * ny * k - nz * s
        r[5] = ny * ny * k + c
        r[6] = ny * nz * k + nx * s
        r[8] = nx * nz * k + ny * s
        r[9] = ny * nz * k - nx * s
        r[10] = nz * nz * k + c
        r[15] = 1f
        multiply(current(), r)
    }

    fun ortho(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        if (left == right || bottom == top || near == far) return
        val r = operand
        r.fill(0f)
        r[0] = 2f / (right - left)
        r[5] = 2f / (top - bottom)
        r[10] = -2f / (far - near)
        r[12] = -(right + left) / (right - left)
        r[13] = -(top + bottom) / (top - bottom)
        r[14] = -(far + near) / (far - near)
        r[15] = 1f
        multiply(current(), r)
    }

    fun perspective(fovy: Float, aspect: Float, near: Float, far: Float) {
        val radians = fovy / 2 * Math.PI.toFloat() / 180
        val deltaZ = far - near
        val sine = sin(radians.toDouble()).toFloat()
        if (deltaZ == 0f || sine == 0f || aspect == 0f) return
        val cotangent = cos(radians.toDouble()).toFloat() / sine
        val r = operand
        r.fill(0f)
        r[0] = cotangent / aspect
        r[5] = cotangent
        r[10] = -(far + near) / deltaZ
        r[11] = -1f
        r[14] = -2 * near * far / deltaZ
        multiply(current(), r)
    }

    fun multMatrix(address: Long) {
        for (i in 0 until 16) operand[i] = Memory.getFloat(address + i * 4L)
        multiply(current(), operand)
    }

    fun viewport(x: Int, y: Int, width: Int, height: Int) {
        viewport[0] = x
        viewport[1] = y
        viewport[2] = width
        viewport[3] = height
    }

    @JvmStatic
    fun readMatrix(name: Int, destination: Long) {
        val stack = when (name) {
            MODELVIEW_MATRIX -> modelView
            PROJECTION_MATRIX -> projection
            TEXTURE_MATRIX -> textures[unit]
            else -> return
        }
        for (i in 0 until 16) Memory.putFloat(destination + i * 4L, stack.data[stack.offset + i])
    }

    @JvmStatic
    fun copyMatrix(name: Int, destination: FloatArray) {
        val stack = when (name) {
            MODELVIEW_MATRIX -> modelView
            PROJECTION_MATRIX -> projection
            TEXTURE_MATRIX -> textures[unit]
            else -> return
        }
        System.arraycopy(stack.data, stack.offset, destination, 0, 16)
    }

    @JvmStatic
    fun readViewport(destination: Long) {
        for (i in 0 until 4) Memory.putInt(destination + i * 4L, viewport[i])
    }
}
