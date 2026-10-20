package yt.ren.astatine.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yt.ren.astatine.gui.AstatineOptionsScreen;

@Mixin(Minecraft.class)
public abstract class OpenVideoOptionsMixin {
	@ModifyVariable(method = "openScreen", at = @At("HEAD"), argsOnly = true)
	private Screen astatine$replaceVideoOptions(Screen screen) {
		if (screen instanceof VideoOptionsScreen) {
			return new AstatineOptionsScreen(((VideoOptionsScreenAccessor) screen).astatine$getParent());
		}

		return screen;
	}
}
