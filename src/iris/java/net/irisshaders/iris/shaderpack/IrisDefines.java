package net.irisshaders.iris.shaderpack;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.parsing.BiomeCategories;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class IrisDefines {
	private static final Pattern SEMVER_PATTERN = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.*(?<bugfix>\\d*)(.*)");

	private static void define(List<StringPair> defines, String key) {
		defines.add(new StringPair(key, ""));
	}

	private static void define(List<StringPair> defines, String key, String value) {
		defines.add(new StringPair(key, value));
	}

	public static ImmutableList<StringPair> createIrisReplacements() {
		ArrayList<StringPair> s = new ArrayList<>(StandardMacros.createStandardEnvironmentDefines());

		for (Biome biome : Biome.getAll()) {
			if (biome != null && biome.name != null) {
				define(s, "BIOME_" + biome.name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"), String.valueOf(biome.id));
			}
		}

		Map<String, Biome> modernBiomes = Map.ofEntries(
			Map.entry("NETHER_WASTES", Biome.HELL),
			Map.entry("CRIMSON_FOREST", Biome.HELL),
			Map.entry("WARPED_FOREST", Biome.HELL),
			Map.entry("SOUL_SAND_VALLEY", Biome.HELL),
			Map.entry("BASALT_DELTAS", Biome.HELL),
			Map.entry("BADLANDS", Biome.MESA),
			Map.entry("WOODED_BADLANDS", Biome.MESA_PLATEAU_F),
			Map.entry("ERODED_BADLANDS", Biome.MESA),
			Map.entry("SNOWY_PLAINS", Biome.ICE_PLAINS),
			Map.entry("ICE_SPIKES", Biome.ICE_PLAINS),
			Map.entry("MUSHROOM_FIELDS", Biome.MUSHROOM_ISLAND),
			Map.entry("SWAMP", Biome.SWAMPLAND),
			Map.entry("DARK_FOREST", Biome.ROOFED_FOREST),
			Map.entry("SNOWY_TAIGA", Biome.COLD_TAIGA),
			Map.entry("OLD_GROWTH_PINE_TAIGA", Biome.MEGA_TAIGA),
			Map.entry("OLD_GROWTH_SPRUCE_TAIGA", Biome.MEGA_TAIGA),
			Map.entry("WINDSWEPT_HILLS", Biome.EXTREME_HILLS),
			Map.entry("WINDSWEPT_FOREST", Biome.EXTREME_HILLS_PLUS),
			Map.entry("WINDSWEPT_SAVANNA", Biome.SAVANNA),
			Map.entry("SNOWY_BEACH", Biome.COLD_BEACH),
			Map.entry("STONY_SHORE", Biome.STONE_BEACH),
			Map.entry("SPARSE_JUNGLE", Biome.JUNGLE_EDGE),
			Map.entry("END_HIGHLANDS", Biome.THE_END),
			Map.entry("END_MIDLANDS", Biome.THE_END),
			Map.entry("SMALL_END_ISLANDS", Biome.THE_END),
			Map.entry("END_BARRENS", Biome.THE_END),
			Map.entry("THE_VOID", Biome.DEFAULT),
			Map.entry("WARM_OCEAN", Biome.OCEAN),
			Map.entry("LUKEWARM_OCEAN", Biome.OCEAN),
			Map.entry("COLD_OCEAN", Biome.OCEAN),
			Map.entry("DEEP_LUKEWARM_OCEAN", Biome.DEEP_OCEAN),
			Map.entry("DEEP_COLD_OCEAN", Biome.DEEP_OCEAN),
			Map.entry("DEEP_FROZEN_OCEAN", Biome.DEEP_OCEAN),
			Map.entry("SUNFLOWER_PLAINS", Biome.PLAINS),
			Map.entry("FLOWER_FOREST", Biome.FOREST),
			Map.entry("OLD_GROWTH_BIRCH_FOREST", Biome.BIRCH_FOREST),
			Map.entry("SAVANNA_PLATEAU", Biome.SAVANNA_PLATEAU),
			Map.entry("BAMBOO_JUNGLE", Biome.JUNGLE));
		Set<String> defined = new HashSet<>();
		s.forEach(pair -> defined.add(pair.key()));
		modernBiomes.forEach((name, biome) -> {
			if (defined.add("BIOME_" + name)) {
				define(s, "BIOME_" + name, String.valueOf(biome.id));
			}
		});
		for (String name : List.of("MEADOW", "CHERRY_GROVE", "GROVE", "SNOWY_SLOPES", "FROZEN_PEAKS", "JAGGED_PEAKS", "STONY_PEAKS", "DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK", "MANGROVE_SWAMP", "PALE_GARDEN", "SULFUR_CAVES")) {
			if (defined.add("BIOME_" + name)) {
				define(s, "BIOME_" + name, "-1");
			}
		}

		BiomeCategories[] categories = BiomeCategories.values();
		for (int i = 0; i < categories.length; i++) {
			define(s, "CAT_" + categories[i].name().toUpperCase(Locale.ROOT), String.valueOf(i));
		}

		define(s, "PPT_NONE", "0");
		define(s, "PPT_RAIN", "1");
		define(s, "PPT_SNOW", "2");

		return ImmutableList.copyOf(s);
	}
}
