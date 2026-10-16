package yt.ren.astatine.mixin.client;

import java.nio.IntBuffer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetInteger(ILjava/nio/IntBuffer;)V"))
	private static void astatine$viewport(int name, IntBuffer values) {
		Native.getViewport(Memory.INSTANCE.address(values) + values.position() * 4L);
	}
}
