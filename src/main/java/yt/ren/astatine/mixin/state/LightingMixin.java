package yt.ren.astatine.mixin.state;

import java.nio.FloatBuffer;
import net.minecraft.client.render.platform.Lighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin(Lighting.class)
public abstract class LightingMixin {
	@Redirect(method = "turnOn", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLight(IILjava/nio/FloatBuffer;)V"))
	private static void astatine$light(int light, int name, FloatBuffer values) {
		int at = values.position();
		GL.light(light, name, values.get(at), values.get(at + 1), values.get(at + 2), values.get(at + 3));
	}

	@Redirect(method = "turnOn", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLightModel(ILjava/nio/FloatBuffer;)V"))
	private static void astatine$lightModel(int name, FloatBuffer values) {
		int at = values.position();
		GL.lightModelAmbient(values.get(at), values.get(at + 1), values.get(at + 2), values.get(at + 3));
	}
}
