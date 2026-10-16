@file:Suppress("NOTHING_TO_INLINE")

package yt.ren.astatine.memory

import sun.misc.Unsafe
import java.nio.Buffer

// lwjgl3 memoryutil is slower
object Memory {
    @PublishedApi
    @JvmField
    internal val UNSAFE: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
        it.isAccessible = true
        it.get(null) as Unsafe
    }

    @PublishedApi
    @JvmField
    internal val BUFFER_ADDRESS: Long = UNSAFE.objectFieldOffset(Buffer::class.java.getDeclaredField("address"))

    @PublishedApi
    @JvmField
    internal val INT_ARRAY_BASE: Long = UNSAFE.arrayBaseOffset(IntArray::class.java).toLong()

    @PublishedApi
    @JvmField
    internal val BYTE_ARRAY_BASE: Long = UNSAFE.arrayBaseOffset(ByteArray::class.java).toLong()

    @PublishedApi
    @JvmField
    internal val FLOAT_ARRAY_BASE: Long = UNSAFE.arrayBaseOffset(FloatArray::class.java).toLong()

    @PublishedApi
    @JvmField
    internal val CHAR_ARRAY_BASE: Long = UNSAFE.arrayBaseOffset(CharArray::class.java).toLong()

    inline fun allocate(bytes: Long): Long = UNSAFE.allocateMemory(bytes)

    inline fun free(address: Long) = UNSAFE.freeMemory(address)

    inline fun address(buffer: Buffer): Long = UNSAFE.getLong(buffer, BUFFER_ADDRESS)

    inline fun getByte(address: Long): Byte = UNSAFE.getByte(address)

    inline fun getInt(address: Long): Int = UNSAFE.getInt(address)

    inline fun getLong(address: Long): Long = UNSAFE.getLong(address)

    inline fun getFloat(address: Long): Float = UNSAFE.getFloat(address)

    inline fun getChar(address: Long): Char = UNSAFE.getChar(address)

    inline fun getShort(address: Long): Short = UNSAFE.getShort(address)

    inline fun putByte(address: Long, value: Byte) = UNSAFE.putByte(address, value)

    inline fun putInt(address: Long, value: Int) = UNSAFE.putInt(address, value)

    inline fun putLong(address: Long, value: Long) = UNSAFE.putLong(address, value)

    inline fun putFloat(address: Long, value: Float) = UNSAFE.putFloat(address, value)

    inline fun copy(source: Long, destination: Long, bytes: Long) = UNSAFE.copyMemory(source, destination, bytes)

    inline fun copy(source: IntArray, index: Int, destination: Long, count: Int) =
        UNSAFE.copyMemory(source, INT_ARRAY_BASE + (index.toLong() shl 2), null, destination, count.toLong() shl 2)

    inline fun copy(source: ByteArray, destination: Long) =
        UNSAFE.copyMemory(source, BYTE_ARRAY_BASE, null, destination, source.size.toLong())

    inline fun copy(source: FloatArray, destination: Long) =
        UNSAFE.copyMemory(source, FLOAT_ARRAY_BASE, null, destination, source.size.toLong() shl 2)

    inline fun copy(source: CharArray, destination: Long) =
        UNSAFE.copyMemory(source, CHAR_ARRAY_BASE, null, destination, source.size.toLong() shl 1)

    inline fun copy(source: Long, destination: ByteArray) =
        UNSAFE.copyMemory(null, source, destination, BYTE_ARRAY_BASE, destination.size.toLong())

    inline fun copy(source: Long, destination: FloatArray, count: Int) =
        UNSAFE.copyMemory(null, source, destination, FLOAT_ARRAY_BASE, count.toLong() shl 2)

    inline fun copy(source: Long, destination: IntArray, count: Int) =
        UNSAFE.copyMemory(null, source, destination, INT_ARRAY_BASE, count.toLong() shl 2)

    inline fun fill(address: Long, bytes: Long, value: Byte) = UNSAFE.setMemory(address, bytes, value)
}
