package com.mojang.renderpearl.backend.opengl;

import com.mojang.renderpearl.api.textures.GpuTexture;

public interface GlTexture extends GpuTexture {
	int glId();
}
