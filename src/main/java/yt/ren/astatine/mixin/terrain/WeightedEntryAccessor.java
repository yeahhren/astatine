package yt.ren.astatine.mixin.terrain;

import net.minecraft.util.WeightedPicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeightedPicker.Entry.class)
public interface WeightedEntryAccessor {
	@Accessor("weight")
	int getAstatineWeight();
}
