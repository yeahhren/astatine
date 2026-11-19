package yt.ren.astatine.mixin.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VideoOptionsScreen.class)
public interface VideoOptionsScreenAccessor {
	@Accessor("parent")
	Screen astatine$getParent();
}
