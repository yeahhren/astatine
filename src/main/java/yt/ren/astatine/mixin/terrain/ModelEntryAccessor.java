package yt.ren.astatine.mixin.terrain;

import net.minecraft.client.render.model.block.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.render.model.block.WeightedBakedModel$ModelEntry")
public interface ModelEntryAccessor {
	@Accessor("model")
	BakedModel getAstatineModel();
}
