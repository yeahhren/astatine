package net.irisshaders.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.mixinterface.ExtendedBiome;
import net.irisshaders.iris.parsing.BiomeCategories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BeachBiome;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.DesertBiome;
import net.minecraft.world.biome.ExtremeHillsBiome;
import net.minecraft.world.biome.ForestBiome;
import net.minecraft.world.biome.HellBiome;
import net.minecraft.world.biome.IceBiome;
import net.minecraft.world.biome.JungleBiome;
import net.minecraft.world.biome.MesaBiome;
import net.minecraft.world.biome.MushroomBiome;
import net.minecraft.world.biome.OceanBiome;
import net.minecraft.world.biome.RiverBiome;
import net.minecraft.world.biome.SavannaBiome;
import net.minecraft.world.biome.StoneBeachBiome;
import net.minecraft.world.biome.SwampBiome;
import net.minecraft.world.biome.TaigaBiome;
import net.minecraft.world.biome.TheEndBiome;

import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

public class BiomeUniforms {
	private static final Object2IntMap<Biome> biomeMap = new Object2IntOpenHashMap<>();

	public static Object2IntMap<Biome> getBiomeMap() {
		return biomeMap;
	}

	public static void addBiomeUniforms(UniformHolder uniforms) {
		uniforms.uniform1i(PER_TICK, "biome",
				playerI(player -> {
					Biome biome = getBiome(player);
					return biomeMap.getOrDefault(biome, biome.id);
				}))
			.uniform1i(PER_TICK, "biome_category", playerI(player -> {
				Biome holder = getBiome(player);
				ExtendedBiome extendedBiome = ((ExtendedBiome) (Object) holder);
				if (extendedBiome.getBiomeCategory() == -1) {
					extendedBiome.setBiomeCategory(getBiomeCategory(holder).ordinal());
					return extendedBiome.getBiomeCategory();
				} else {
					return extendedBiome.getBiomeCategory();
				}
			})).uniform1i(PER_TICK, "biome_precipitation", playerI(player -> {
				Biome biome = getBiome(player);

				return biome.isSnowy() ? 2 : biome.isRainy() ? 1 : 0;
			})).uniform1f(PER_TICK, "rainfall",
				playerF(player -> ((ExtendedBiome) (Object) getBiome(player)).getDownfall()))
			.uniform1f(PER_TICK, "temperature",
				playerF(player -> getBiome(player).temperature));
	}

	private static Biome getBiome(LocalClientPlayerEntity player) {
		return player.world.getBiome(new BlockPos(player));
	}

	private static BiomeCategories getBiomeCategory(Biome holder) {
		if (holder.id >= 128 && Biome.byId(holder.id - 128, null) != null) {
			holder = Biome.byId(holder.id - 128, null);
		}

		if (holder == Biome.DEFAULT) {
			// Literally only the void has this...
			return BiomeCategories.NONE;
		} else if (holder instanceof IceBiome || holder == Biome.FROZEN_OCEAN || holder == Biome.FROZEN_RIVER || holder == Biome.COLD_BEACH) {
			return BiomeCategories.ICY;
		} else if (holder instanceof ExtremeHillsBiome) {
			return BiomeCategories.EXTREME_HILLS;
		} else if (holder instanceof TaigaBiome) {
			return BiomeCategories.TAIGA;
		} else if (holder instanceof OceanBiome) {
			return BiomeCategories.OCEAN;
		} else if (holder instanceof JungleBiome) {
			return BiomeCategories.JUNGLE;
		} else if (holder instanceof ForestBiome) {
			return BiomeCategories.FOREST;
		} else if (holder instanceof MesaBiome) {
			return BiomeCategories.MESA;
		} else if (holder instanceof HellBiome) {
			return BiomeCategories.NETHER;
		} else if (holder instanceof TheEndBiome) {
			return BiomeCategories.THE_END;
		} else if (holder instanceof BeachBiome || holder instanceof StoneBeachBiome) {
			return BiomeCategories.BEACH;
		} else if (holder instanceof DesertBiome) {
			return BiomeCategories.DESERT;
		} else if (holder instanceof RiverBiome) {
			return BiomeCategories.RIVER;
		} else if (holder instanceof SwampBiome) {
			return BiomeCategories.SWAMP;
		} else if (holder instanceof MushroomBiome) {
			return BiomeCategories.MUSHROOM;
		} else if (holder instanceof SavannaBiome) {
			return BiomeCategories.SAVANNA;
		} else {
			return BiomeCategories.PLAINS;
		}
	}

	static IntSupplier playerI(ToIntFunction<LocalClientPlayerEntity> function) {
		return () -> {
			LocalClientPlayerEntity player = Minecraft.getInstance().player;
			if (player == null) {
				return 0; // TODO: I'm not sure what I'm supposed to do here?
			} else {
				return function.applyAsInt(player);
			}
		};
	}

	static FloatSupplier playerF(ToFloatFunction<LocalClientPlayerEntity> function) {
		return () -> {
			LocalClientPlayerEntity player = Minecraft.getInstance().player;
			if (player == null) {
				return 0.0f; // TODO: I'm not sure what I'm supposed to do here?
			} else {
				return function.applyAsFloat(player);
			}
		};
	}

	@FunctionalInterface
	public interface ToFloatFunction<T> {
		/**
		 * Applies this function to the given argument.
		 *
		 * @param value the function argument
		 * @return the function result
		 */
		float applyAsFloat(T value);
	}
}
