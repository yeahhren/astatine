package yt.ren.astatine.render.geometry

import net.minecraft.client.render.model.ModelPart
import net.minecraft.client.render.model.Polygon
import net.minecraft.client.render.vertex.BufferBuilder
import net.minecraft.client.render.vertex.DefaultVertexFormat
import org.lwjgl.opengl.GL11.GL_QUADS
import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory
import yt.ren.astatine.mixin.model.BoxAccessor
import yt.ren.astatine.mixin.model.PolygonAccessor

object ModelBaker {
    private val builder = BufferBuilder(4096)

    @JvmStatic
    fun bake(part: ModelPart, scale: Float): Long {
        builder.begin(GL_QUADS, DefaultVertexFormat.ENTITY)
        for (box in part.boxes) {
            for (polygon in (box as BoxAccessor).astatineFaces) emit(polygon, scale)
        }
        builder.end()
        val count = builder.vertexCount
        val mesh = if (count > 0) {
            Native.uploadMesh(VertexLayouts.of(DefaultVertexFormat.ENTITY), Memory.address(builder.buffer), count)
        } else {
            0L
        }
        builder.clear()
        return mesh
    }

    private fun emit(polygon: Polygon, scale: Float) {
        val edge = polygon.vertices[1].pos.subtractFrom(polygon.vertices[0].pos)
        val other = polygon.vertices[1].pos.subtractFrom(polygon.vertices[2].pos)
        val normal = other.cross(edge).normalize()
        val sign = if ((polygon as PolygonAccessor).astatineFlipNormal) -1.0f else 1.0f
        val x = normal.x.toFloat() * sign
        val y = normal.y.toFloat() * sign
        val z = normal.z.toFloat() * sign
        for (i in 0 until 4) {
            val vertex = polygon.vertices[i]
            builder.vertex(vertex.pos.x * scale, vertex.pos.y * scale, vertex.pos.z * scale)
                .texture(vertex.u.toDouble(), vertex.v.toDouble())
                .normal(x, y, z)
                .nextVertex()
        }
    }
}
