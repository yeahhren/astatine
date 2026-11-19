package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("getNightVisionScale")
	float astatine$getNightVisionScale(LivingEntity entity, float tickDelta);

	@Accessor("lightmapTexture")
	DynamicTexture astatine$lightmapTexture();

	@Accessor("thiccFog")
	boolean astatine$hasThickFog();
}
