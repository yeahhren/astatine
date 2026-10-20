package yt.ren.astatine.gui.options.control;

import net.minecraft.client.resource.language.I18n;

public interface ControlValueFormatter {
	static ControlValueFormatter guiScale() {
		return (v) -> switch (v) {
			case 0 -> I18n.translate("options.guiScale.auto");
			case 1 -> I18n.translate("options.guiScale.small");
			case 2 -> I18n.translate("options.guiScale.normal");
			case 3 -> I18n.translate("options.guiScale.large");
			default -> v + "x";
		};
	}

	static ControlValueFormatter fpsLimit() {
		return (v) -> (v == 260) ? I18n.translate("options.framerateLimit.max") : I18n.translate("astatine.options.framerate", v);
	}

	static ControlValueFormatter brightness() {
		return (v) -> {
			if (v == 0) {
				return I18n.translate("options.gamma.min");
			} else if (v == 100) {
				return I18n.translate("options.gamma.max");
			} else {
				return v + "%";
			}
		};
	}

	String format(int value);

	static ControlValueFormatter translateVariable(String key) {
		return (v) -> I18n.translate(key, v);
	}

	static ControlValueFormatter percentage() {
		return (v) -> v + "%";
	}

	static ControlValueFormatter multiplier() {
		return (v) -> v + "x";
	}

	static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
		return (v) -> v == 0 ? disableText : v + " " + name;
	}

	static ControlValueFormatter number() {
		return String::valueOf;
	}
}
