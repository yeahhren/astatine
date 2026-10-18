package net.irisshaders.iris.shadows.frustum;

import net.minecraft.client.render.Culler;

public class FrustumHolder {
	private Culler frustum;
	private String distanceInfo = "(unavailable)";
	private String cullingInfo = "(unavailable)";

	public FrustumHolder setInfo(Culler frustum, String distanceInfo, String cullingInfo) {
		this.frustum = frustum;
		this.distanceInfo = distanceInfo;
		this.cullingInfo = cullingInfo;
		return this;
	}

	public Culler getFrustum() {
		return frustum;
	}

	public String getDistanceInfo() {
		return distanceInfo;
	}

	public String getCullingInfo() {
		return cullingInfo;
	}
}
