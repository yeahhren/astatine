package yt.ren.astatine.mixin.geometry;

import java.nio.ByteBuffer;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.MeshHolder;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.render.geometry.VertexLayouts;

@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin implements MeshHolder {
	@Shadow
	@Final
	private VertexFormat format;

	@Unique
	private long astatine$mesh;

	@Override
	public long astatine$mesh() {
		return this.astatine$mesh;
	}

	@Override
	public void astatine$mesh(long mesh) {
		this.astatine$mesh = mesh;
	}

	@Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;bufferData(ILjava/nio/ByteBuffer;I)V"))
	private void astatine$upload(int target, ByteBuffer buffer, int usage) {
		this.astatine$release();
		int count = buffer.limit() / this.format.getVertexSize();
		if (count > 0) {
			this.astatine$mesh = Native.uploadMesh(VertexLayouts.of(this.format), Memory.INSTANCE.address(buffer) + buffer.position(), count);
		}
	}

	@Redirect(method = "draw", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDrawArrays(III)V"))
	private void astatine$draw(int mode, int first, int count) {
		if (this.astatine$mesh != 0L) {
			Native.drawMesh(this.astatine$mesh, mode);
		}
	}

	@Redirect(method = "delete", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;deleteBuffers(I)V"))
	private void astatine$delete(int buffer) {
		this.astatine$release();
	}

	@Unique
	private void astatine$release() {
		if (this.astatine$mesh != 0L) {
			GL.releaseMesh(this.astatine$mesh);
			this.astatine$mesh = 0L;
		}
	}
}
