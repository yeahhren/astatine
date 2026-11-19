package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.irisshaders.iris.shaderpack.option.ProfileSet;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuProfileElement;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.gui.IrisGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;

import java.util.Optional;

public class ProfileElementWidget extends BaseOptionElementWidget<OptionMenuProfileElement> {
	private static final Text PROFILE_LABEL = GuiUtil.translatable("options.iris.profile");
	private static final Text PROFILE_CUSTOM = GuiUtil.withStyle(GuiUtil.translatable("options.iris.profile.custom"), Formatting.YELLOW);

	private Profile next;
	private Profile previous;
	private Text profileLabel;

	public ProfileElementWidget(OptionMenuProfileElement element) {
		super(element);
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		super.init(screen, navigation);
		this.setLabel(PROFILE_LABEL);

		ProfileSet profiles = this.element.profiles;
		OptionSet options = this.element.options;
		OptionValues pendingValues = this.element.getPendingOptionValues();

		ProfileSet.ProfileResult result = profiles.scan(options, pendingValues);

		this.next = result.next;
		this.previous = result.previous;
		Optional<String> profileName = result.current.map(p -> p.name);

		this.profileLabel = profileName.map(name -> GuiUtil.translateOrDefault(GuiUtil.literal(name), "profile." + name))
			.orElse(PROFILE_CUSTOM);
	}

	@Override
	public void render(IrisGraphics guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		this.updateRenderParams(bounds.width() - (GuiUtil.width(PROFILE_LABEL) + 16));

		this.renderOptionWithValue(guiGraphics, hovered || isFocused());
	}

	@Override
	protected Text createValueLabel() {
		return this.profileLabel;
	}

	@Override
	public Optional<Text> getCommentTitle() {
		return Optional.of(PROFILE_LABEL);
	}

	@Override
	public String getCommentKey() {
		return "profile.comment";
	}

	@Override
	public boolean applyNextValue() {
		if (this.next == null) {
			return false;
		}

		Iris.queueShaderPackOptionsFromProfile(this.next);

		return true;
	}

	@Override
	public boolean applyPreviousValue() {
		if (this.previous == null) {
			return false;
		}

		Iris.queueShaderPackOptionsFromProfile(this.previous);

		return true;
	}

	@Override
	public boolean applyOriginalValue() {
		return false; // Resetting options is the way to return to the "default profile"
	}

	@Override
	public boolean isValueModified() {
		return false;
	}
}
