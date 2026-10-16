package yt.ren.astatine.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GLCapabilities;

@Mixin(targets = "net.minecraft.client.Minecraft$30387532")
public abstract class CrashReportMixin {
	@Redirect(method = "call", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;"))
	private String astatine$string(int name) {
		return GLCapabilities.string(name);
	}
}
