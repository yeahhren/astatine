package yt.ren.astatine.render

import net.minecraft.client.render.platform.GlStateManager

object TextureBinding {
    const val OBJECT = -2

    @JvmStatic
    fun bind(texture: Long) {
        GlStateManager.bindTexture(OBJECT)
        GL.bindTexture(texture)
    }
}
