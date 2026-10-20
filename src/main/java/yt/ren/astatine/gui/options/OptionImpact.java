package yt.ren.astatine.gui.options;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;

public enum OptionImpact {
	LOW(Formatting.GREEN, "astatine.option_impact.low"),
	MEDIUM(Formatting.YELLOW, "astatine.option_impact.medium"),
	HIGH(Formatting.GOLD, "astatine.option_impact.high"),
	VARIES(Formatting.WHITE, "astatine.option_impact.varies");

	private final Formatting color;
	private final String key;

	OptionImpact(Formatting color, String key) {
		this.color = color;
		this.key = key;
	}

	public String getLocalizedName() {
		return this.color + I18n.translate(this.key);
	}
}
