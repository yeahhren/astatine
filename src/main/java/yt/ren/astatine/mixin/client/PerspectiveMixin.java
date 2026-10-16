package yt.ren.astatine.mixin.client;

import net.minecraft.client.gui.screen.game.inventory.EnchantingTableScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin({GameRenderer.class, TitleScreen.class, EnchantingTableScreen.class})
public abstract class PerspectiveMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"), require = 0)
	private void astatine$perspective(float fovy, float aspect, float near, float far) {
		GL.perspective(fovy, aspect, near, far);
	}
}
