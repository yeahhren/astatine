package yt.ren.astatine.mixin.model;

import net.minecraft.client.render.model.ModelPart;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.MeshHolder;
import yt.ren.astatine.render.geometry.ModelBaker;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements MeshHolder {
	@Shadow
	private boolean compiled;

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

	// TODO: This is instance-able, could make it GPU-skinned.
	//		 Should be looked into as a fast-path.

	/**
	 * @author Ren
	 * @reason We bake the model into a mesh
	 */
	@Overwrite
	private void compile(float scale) {
		this.astatine$mesh = ModelBaker.bake((ModelPart) (Object) this, scale);
		this.compiled = true;
	}

	@Redirect(method = {"render", "renderForceTransform"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V"))
	private void astatine$draw(int list) {
		if (this.astatine$mesh != 0L) {
			Native.drawMesh(this.astatine$mesh, GL11.GL_QUADS);
		}
	}
}
