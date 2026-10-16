package yt.ren.astatine.mixin.target;

import java.nio.ByteBuffer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.FramebufferHolder;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.render.TextureBinding;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin implements FramebufferHolder {
	@Shadow
	public int width;

	@Shadow
	public int height;

	@Shadow
	public boolean useDepth;

	@Shadow
	public int frameBufferId;

	@Shadow
	public int colorTextureId;

	@Shadow
	public int viewWidth;

	@Shadow
	public int viewHeight;

	@Unique
	private boolean astatine$opaqueDraw;

	@Unique
	private int astatine$drawWidth;

	@Unique
	private int astatine$drawHeight;

	@Unique
	private long astatine$framebuffer;

	@Override
	public long astatine$framebuffer() {
		return this.astatine$framebuffer;
	}

	@Override
	public void astatine$framebuffer(long framebuffer) {
		this.astatine$framebuffer = framebuffer;
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;genFramebuffers()I"))
	private int astatine$create() {
		this.astatine$framebuffer = Native.framebufferCreate(this.width, this.height, this.useDepth);
		return 1;
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"))
	private void astatine$storage(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;genRenderbuffers()I"))
	private int astatine$genRenderbuffers() {
		return 1;
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;bindRenderbuffer(II)V"))
	private void astatine$bindRenderbuffer(int target, int renderbuffer) {
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;renderbufferStorage(IIII)V"))
	private void astatine$renderbufferStorage(int target, int format, int width, int height) {
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;framebufferRenderbuffer(IIII)V"))
	private void astatine$framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
	}

	@Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;framebufferTexture2D(IIIII)V"))
	private void astatine$framebufferTexture(int target, int attachment, int textureTarget, int texture, int level) {
	}

	@Redirect(method = "checkStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;checkFramebufferStatus(I)I"))
	private int astatine$status(int target) {
		return GLX.GL_FRAMEBUFFER_COMPLETE;
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;bindFramebuffer(II)V"))
	private void astatine$bindFramebuffer(int target, int framebuffer) {
		if (framebuffer == this.frameBufferId && framebuffer > 0 && this.astatine$framebuffer != 0L) {
			GL.bindFramebuffer(this.astatine$framebuffer);
		} else {
			GL.bindFramebuffer(0L);
		}
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;bindTexture(I)V"))
	private void astatine$bindTexture(int texture) {
		if (texture == this.colorTextureId && texture > 0 && this.astatine$framebuffer != 0L) {
			TextureBinding.bind(Native.framebufferTexture(this.astatine$framebuffer));
		} else {
			GlStateManager.bindTexture(texture);
		}
	}

	@Inject(method = "draw(IIZ)V", at = @At("HEAD"))
	private void astatine$beginDraw(int width, int height, boolean disableBlend, CallbackInfo callback) {
		this.astatine$opaqueDraw = disableBlend && this.viewWidth == this.width && this.viewHeight == this.height;
		this.astatine$drawWidth = width;
		this.astatine$drawHeight = height;
	}

	@Redirect(method = "draw(IIZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
	private void astatine$blit(Tesselator tesselator) {
		if (this.astatine$opaqueDraw && this.astatine$framebuffer != 0L
			&& Native.framebufferBlit(this.astatine$framebuffer, this.astatine$drawWidth, this.astatine$drawHeight)) {
			BufferBuilder builder = tesselator.getBuffer();
			builder.end();
			builder.clear();
			return;
		}
		tesselator.end();
	}

	@Redirect(method = "destroyBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;deleteRenderbuffers(I)V"))
	private void astatine$deleteRenderbuffers(int renderbuffer) {
	}

	@Redirect(method = "destroyBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;deleteFramebuffers(I)V"))
	private void astatine$deleteFramebuffers(int framebuffer) {
		if (this.astatine$framebuffer != 0L) {
			Native.framebufferRelease(this.astatine$framebuffer);
			this.astatine$framebuffer = 0L;
		}
	}
}
