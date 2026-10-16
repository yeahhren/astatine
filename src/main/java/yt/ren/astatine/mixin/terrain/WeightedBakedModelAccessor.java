package yt.ren.astatine.mixin.terrain;

import java.util.List;
import net.minecraft.client.render.model.block.WeightedBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeightedBakedModel.class)
public interface WeightedBakedModelAccessor {
	@Accessor("entries")
	List<?> getAstatineEntries();
}
