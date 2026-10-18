package net.irisshaders.iris.gui.option;

import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import yt.ren.astatine.shaderpack.Shaderpacks;

public class IrisVideoSettings {
	public static int shadowDistance = 32;
	public static ColorSpace colorSpace = ColorSpace.SRGB;

	public static String getTooltip() {
		if (Shaderpacks.forcedShadowRenderDistanceChunks().isPresent()) {
			return GuiUtil.translatable("options.iris.shadowDistance.disabled").getFormattedString();
		} else {
			return GuiUtil.translatable("options.iris.shadowDistance.enabled").getFormattedString();
		}
	}

	public static String getMessage(int d) {
		d = Shaderpacks.forcedShadowRenderDistanceChunks().orElse(d);
		String caption = GuiUtil.translatable("options.iris.shadowDistance").getFormattedString();

		if (d <= 0.0) {
			return caption + ": 0 (disabled)";
		} else {
			return caption + ": " + GuiUtil.translatable("options.chunks", d).getFormattedString();
		}
	}

	public static int getOverriddenShadowDistance(int base) {
		return Shaderpacks.forcedShadowRenderDistanceChunks().orElse(base);
	}

	public static boolean isShadowDistanceSliderEnabled() {
		return Shaderpacks.forcedShadowRenderDistanceChunks().isEmpty();
	}
}
