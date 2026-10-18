package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.backend.IrisImage;
import net.irisshaders.iris.gl.texture.TextureType;
import yt.ren.astatine.backend.AstatineTextureView;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;

import java.nio.ByteBuffer;

public class AstatineImage implements IrisImage {
	private final TextureType type;
	private final int width;
	private final int height;
	private final int depth;
	private long texture;
	private long view;

	public AstatineImage(String name, TextureType type, GpuFormat format, int width, int height, int depth, boolean storage) {
		if (width < 1 || height < 0 || depth < 0) {
			throw new IllegalArgumentException("invalid custom image extent: " + name);
		}

		this.type = type;
		this.width = width;
		this.height = Math.max(1, height);
		this.depth = Math.max(1, depth);
		int dimension = switch (type) {
			case TEXTURE_1D -> 1;
			case TEXTURE_2D, TEXTURE_RECTANGLE -> 2;
			case TEXTURE_3D -> 3;
		};
		this.texture = BackendNatives.imageCreate(dimension, format.ordinal(), width, this.height, this.depth, storage, name);
		this.view = BackendNatives.imageView(this.texture);
	}

	public static long view(GpuTextureView texture, IrisImage image) {
		return image == null ? ((AstatineTextureView) texture).handle() : image.getView();
	}

	public static long texture(GpuTextureView texture, IrisImage image) {
		return image == null ? ((AstatineTextureView) texture).texture().handle() : image.getImage();
	}

	@Override
	public TextureType getType() {
		return this.type;
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public int getDepth() {
		return this.depth;
	}

	@Override
	public long getView() {
		if (this.view == 0L) {
			throw new IllegalStateException("image is closed");
		}

		return this.view;
	}

	@Override
	public long getImage() {
		this.getView();
		return this.texture;
	}

	@Override
	public void upload(ByteBuffer pixels) {
		var scratch = new Scratch(pixels.remaining()).putBytes(pixels);
		BackendNatives.imageUpload(this.getImage(), scratch.address(), pixels.remaining());
	}

	@Override
	public void clear() {
		BackendNatives.imageClear(this.getImage());
	}

	@Override
	public void close() {
		if (this.texture == 0L) {
			return;
		}

		BackendNatives.textureViewRelease(this.view);
		BackendNatives.textureRelease(this.texture);
		this.view = this.texture = 0L;
	}
}
