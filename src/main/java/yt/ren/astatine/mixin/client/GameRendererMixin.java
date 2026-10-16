package yt.ren.astatine.mixin.client;

import java.nio.FloatBuffer;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFog(ILjava/nio/FloatBuffer;)V"))
	private void astatine$fog(int name, FloatBuffer values) {
		if (name == GL11.GL_FOG_COLOR) {
			int at = values.position();
			GL.fogColor(values.get(at), values.get(at + 1), values.get(at + 2), values.get(at + 3));
		}
	}

	@Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V"))
	private void astatine$fogDistance(int name, int mode) {
		GL.fogRadial(mode == 34139);
	}
}
