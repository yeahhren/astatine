package net.irisshaders.iris.uniforms;

public class EndFlashStorage {
	private float lastEndFlash;
	private float currentEndFlash;

	public void tick() {
		lastEndFlash = currentEndFlash;
		currentEndFlash = 0;
	}

	public float getLastEndFlash() {
		return lastEndFlash;
	}

	public float getCurrentEndFlash() {
		return currentEndFlash;
	}
}
