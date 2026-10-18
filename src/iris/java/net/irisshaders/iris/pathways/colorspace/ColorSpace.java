package net.irisshaders.iris.pathways.colorspace;

public enum ColorSpace {
	SRGB("sRGB"),
	DCI_P3("DCI-P3"),
	DISPLAY_P3("Display P3"),
	REC2020("REC2020"),
	ADOBE_RGB("Adobe RGB");

	private final String name;

	ColorSpace(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
