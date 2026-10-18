package com.mojang.renderpearl.backend.api;

import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;

public interface CommandEncoderBackend {
	TransientMemory transientMemory();

	RenderPassBackend createRenderPass(RenderPassDescriptor descriptor);

	void submitRenderPass();
}
