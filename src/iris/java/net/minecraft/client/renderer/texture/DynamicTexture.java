package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;

import java.util.OptionalDouble;
import java.util.function.Supplier;

public class DynamicTexture extends AbstractTexture {
	private NativeImage pixels;

	public DynamicTexture(Supplier<String> label, NativeImage pixels) {
		this.pixels = pixels;
		var device = RenderSystem.getDevice();
		this.texture = device.createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
			GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
		this.textureView = device.createTextureView(this.texture);
		this.sampler = device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, 1,
			OptionalDouble.empty());
		this.upload();
	}

	public NativeImage getPixels() {
		return this.pixels;
	}

	public void upload() {
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.pixels);
	}

	@Override
	public void close() {
		if (this.pixels != null) {
			this.pixels.close();
			this.pixels = null;
		}

		if (this.sampler != null) {
			this.sampler.close();
			this.sampler = null;
		}

		super.close();
	}
}
