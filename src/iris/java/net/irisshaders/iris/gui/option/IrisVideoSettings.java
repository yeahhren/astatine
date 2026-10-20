package net.irisshaders.iris.gui.option;

import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import yt.ren.astatine.config.AstatineOptions;
import yt.ren.astatine.iris.IrisPlatform;

public class IrisVideoSettings {
	public static int shadowDistance() {
		return AstatineOptions.SHADOW_DISTANCE.getInt();
	}

	public static ColorSpace colorSpace() {
		return AstatineOptions.COLOR_SPACE.get();
	}

	public static int getOverriddenShadowDistance(int base) {
		return IrisPlatform.get().forcedShadowRenderDistanceChunks().orElse(base);
	}

	public static boolean isShadowDistanceSliderEnabled() {
		return IrisPlatform.get().forcedShadowRenderDistanceChunks().isEmpty();
	}
}
