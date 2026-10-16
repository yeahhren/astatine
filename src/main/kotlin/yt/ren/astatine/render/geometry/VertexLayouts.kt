package yt.ren.astatine.render.geometry

import net.minecraft.client.render.vertex.VertexFormat
import net.minecraft.client.render.vertex.VertexFormatElement
import yt.ren.astatine.Native
import yt.ren.astatine.extension.LayoutHolder
import yt.ren.astatine.memory.Memory

object VertexLayouts {
    private const val MAX_ELEMENTS = 32
    private val scratch = Memory.allocate(MAX_ELEMENTS * 5 * 4L)

    @JvmStatic
    fun of(format: VertexFormat): Int {
        val holder = format as LayoutHolder
        val cached = holder.layout
        if (cached >= 0) return cached
        val elements = format.elements
        val count = minOf(elements.size, MAX_ELEMENTS)
        for (i in 0 until count) {
            val element = elements[i]
            val at = scratch + i * 20L
            Memory.putInt(at, usage(element.usage))
            Memory.putInt(at + 4, type(element.type))
            Memory.putInt(at + 8, element.count)
            Memory.putInt(at + 12, element.index)
            Memory.putInt(at + 16, format.getOffset(i))
        }
        val layout = Native.registerLayout(scratch, count, format.vertexSize)
        holder.layout = layout
        return layout
    }

    private fun usage(usage: VertexFormatElement.Usage): Int = when (usage) {
        VertexFormatElement.Usage.POSITION -> 0
        VertexFormatElement.Usage.COLOR -> 1
        VertexFormatElement.Usage.UV -> 2
        VertexFormatElement.Usage.NORMAL -> 3
        else -> 4
    }

    private fun type(type: VertexFormatElement.Type): Int = when (type) {
        VertexFormatElement.Type.FLOAT -> 0
        VertexFormatElement.Type.UBYTE -> 1
        VertexFormatElement.Type.BYTE -> 2
        VertexFormatElement.Type.USHORT -> 3
        VertexFormatElement.Type.SHORT -> 4
        VertexFormatElement.Type.UINT -> 5
        VertexFormatElement.Type.INT -> 6
    }
}
