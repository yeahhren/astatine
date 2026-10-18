package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
	@Accessor("stitchedSprites")
	Map<String, TextureAtlasSprite> astatine$stitchedSprites();

	@Accessor("maxMipLevel")
	int astatine$maxMipLevel();
}
