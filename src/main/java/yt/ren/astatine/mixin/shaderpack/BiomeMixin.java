package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.mixinterface.ExtendedBiome;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Biome.class)
public abstract class BiomeMixin implements ExtendedBiome {
	@Shadow
	public float downfall;

	@Unique
	private int astatine$biomeCategory = -1;

	@Override
	public int getBiomeCategory() {
		return this.astatine$biomeCategory;
	}

	@Override
	public void setBiomeCategory(int biomeCategory) {
		this.astatine$biomeCategory = biomeCategory;
	}

	@Override
	public float getDownfall() {
		return this.downfall;
	}
}
