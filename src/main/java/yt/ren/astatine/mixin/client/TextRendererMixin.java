package yt.ren.astatine.mixin.client;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
	@Unique
	private double astatine$u;

	@Unique
	private double astatine$v;

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V"))
	private void astatine$begin(int mode) {
		Tesselator.getInstance().getBuffer().begin(mode, DefaultVertexFormat.POSITION_TEX);
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexCoord2f(FF)V"))
	private void astatine$texCoord(float u, float v) {
		this.astatine$u = u;
		this.astatine$v = v;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex3f(FFF)V"))
	private void astatine$vertex(float x, float y, float z) {
		Tesselator.getInstance().getBuffer().vertex(x, y, z).texture(this.astatine$u, this.astatine$v).nextVertex();
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnd()V"))
	private void astatine$end() {
		Tesselator.getInstance().end();
	}
}
