package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.option.ShadowDistanceOption;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixin extends Screen {
	@Unique
	private static final int SHADER_PACKS_BUTTON = 7030;

	@Unique
	private static final int SHADOW_DISTANCE_SLIDER = 7031;

	@Unique
	private ShadowDistanceOption astatine$shadowDistance;

	@Inject(method = "init", at = @At("TAIL"))
	private void astatine$addShaderPackButton(CallbackInfo callback) {
		if (!Shaderpacks.isInitialized()) {
			return;
		}

		for (ButtonWidget button : this.buttons) {
			if (button.id == 200) {
				button.x = this.width / 2 + 55;
				button.setWidth(100);
			}
		}

		this.buttons.add(new ButtonWidget(SHADER_PACKS_BUTTON, this.width / 2 - 155, this.height - 27, 100, 20,
			GuiUtil.translatable("options.iris.shaderPackSelection").getFormattedString()));
		this.astatine$shadowDistance = new ShadowDistanceOption(SHADOW_DISTANCE_SLIDER, this.width / 2 - 50, this.height - 27, 100);
		this.buttons.add(this.astatine$shadowDistance);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void astatine$renderShadowDistanceTooltip(int mouseX, int mouseY, float tickDelta, CallbackInfo callback) {
		ShadowDistanceOption slider = this.astatine$shadowDistance;

		if (slider != null && slider.visible && slider.isHovered()) {
			this.renderTooltip(this.textRenderer.split(slider.getTooltip(), 200), mouseX, mouseY);
		}
	}

	@Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
	private void astatine$openShaderPackScreen(ButtonWidget button, CallbackInfo callback) {
		if (button.active && button.id == SHADER_PACKS_BUTTON) {
			this.minecraft.options.save();
			this.minecraft.openScreen(new ShaderPackScreen(this));
			callback.cancel();
		}
	}
}
