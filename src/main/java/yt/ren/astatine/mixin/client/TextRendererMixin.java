package yt.ren.astatine.mixin.client;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import java.util.Locale;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
	@Unique
	private static final String FORMATTING_CODES = "0123456789abcdefklmnor";

	@Unique
	private final BufferBuilder astatine$glyphs = new BufferBuilder(16384);

	@Unique
	private final BufferUploader astatine$uploader = new BufferUploader();

	@Unique
	private final float[] astatine$corners = new float[20];

	@Unique
	private int astatine$corner;

	@Unique
	private boolean astatine$open;

	@Unique
	private Identifier astatine$texture;

	@Unique
	private void astatine$flush() {
		if (this.astatine$open) {
			this.astatine$open = false;
			this.astatine$glyphs.end();
			this.astatine$uploader.end(this.astatine$glyphs);
		}
	}

	@Redirect(method = {"drawBasicGlyph", "bindFontPageTexture"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V"))
	private void astatine$bindFont(TextureManager textureManager, Identifier texture) {
		if (texture != this.astatine$texture) {
			this.astatine$flush();
			textureManager.bind(texture);
			this.astatine$texture = texture;
		}
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V"))
	private void astatine$begin(int mode) {
		this.astatine$corner = 0;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexCoord2f(FF)V"))
	private void astatine$texCoord(float u, float v) {
		int at = this.astatine$corner * 5;
		this.astatine$corners[at + 3] = u;
		this.astatine$corners[at + 4] = v;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertex3f(FFF)V"))
	private void astatine$vertex(float x, float y, float z) {
		int at = this.astatine$corner++ * 5;
		this.astatine$corners[at] = x;
		this.astatine$corners[at + 1] = y;
		this.astatine$corners[at + 2] = z;
	}

	@Redirect(method = {"drawBasicGlyph", "drawUnicodeGlyph"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnd()V"))
	private void astatine$end() {
		if (!this.astatine$open) {
			this.astatine$glyphs.begin(7, DefaultVertexFormat.POSITION_TEX);
			this.astatine$open = true;
		}
		this.astatine$emit(0);
		this.astatine$emit(1);
		this.astatine$emit(3);
		this.astatine$emit(2);
	}

	@Unique
	private void astatine$emit(int corner) {
		float[] corners = this.astatine$corners;
		int at = corner * 5;
		this.astatine$glyphs.vertex(corners[at], corners[at + 1], corners[at + 2]).texture(corners[at + 3], corners[at + 4]).nextVertex();
	}

	@Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V"))
	private void astatine$color(float r, float g, float b, float a) {
		this.astatine$flush();
		GlStateManager.color4f(r, g, b, a);
	}

	@Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;disableTexture()V"))
	private void astatine$disableTexture() {
		this.astatine$flush();
		GlStateManager.disableTexture();
	}

	@Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("RETURN"))
	private void astatine$finish(String text, boolean shadow, CallbackInfo callback) {
		this.astatine$flush();
		this.astatine$texture = null;
	}

	@Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase(Ljava/util/Locale;)Ljava/lang/String;"))
	private String astatine$skipLowerCase(String text, Locale locale) {
		return text;
	}

	@Redirect(method = "drawLayer(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Ljava/lang/String;indexOf(I)I"))
	private int astatine$indexOf(String table, int character) {
		if (table == FORMATTING_CODES) {
			return table.indexOf(Character.toLowerCase((char) character));
		}
		return table.indexOf(character);
	}
}
