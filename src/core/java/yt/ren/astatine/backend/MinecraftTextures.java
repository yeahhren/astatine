package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import yt.ren.astatine.extension.TextureHolder;

public final class MinecraftTextures {
	private MinecraftTextures() {
	}

	public static long handle(Texture texture) {
		return texture instanceof TextureHolder holder ? holder.astatine$texture() : 0L;
	}

	public static GpuTextureView view(Texture texture) {
		return MinecraftTextureViews.view(handle(texture));
	}

	public static GpuTextureView blockAtlas() {
		return view(Minecraft.getInstance().getTextureManager().get(TextureAtlas.BLOCKS_LOCATION));
	}

	public static Texture load(Identifier location) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		Texture texture = textures.get(location);

		if (texture == null) {
			texture = new SimpleTexture(location);
			textures.register(location, texture);
		}

		return texture;
	}
}
