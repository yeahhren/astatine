package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleTexture.class)
public interface SimpleTextureAccessor {
	@Accessor("location")
	Identifier astatine$location();
}
