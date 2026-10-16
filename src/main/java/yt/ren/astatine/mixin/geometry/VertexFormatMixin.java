package yt.ren.astatine.mixin.geometry;

import net.minecraft.client.render.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.extension.LayoutHolder;

@Mixin(VertexFormat.class)
public abstract class VertexFormatMixin implements LayoutHolder {
	@Unique
	private int astatine$layout = -1;

	@Override
	public int astatine$layout() {
		return this.astatine$layout;
	}

	@Override
	public void astatine$layout(int layout) {
		this.astatine$layout = layout;
	}

	@Inject(method = "addElement", at = @At("HEAD"))
	private void astatine$invalidate(CallbackInfoReturnable<VertexFormat> callback) {
		this.astatine$layout = -1;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void astatine$invalidate(CallbackInfo callback) {
		this.astatine$layout = -1;
	}
}
