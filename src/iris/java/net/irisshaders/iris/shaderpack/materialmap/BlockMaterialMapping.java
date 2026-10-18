package net.irisshaders.iris.shaderpack.materialmap;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.resource.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockMaterialMapping {
	public static Object2IntMap<BlockState> createBlockStateIdMap(Int2ObjectLinkedOpenHashMap<List<BlockEntry>> blockPropertiesMap,
		Int2ObjectLinkedOpenHashMap<List<TagEntry>> tagPropertiesMap) {
		Object2IntMap<BlockState> blockStateIds = new Object2IntLinkedOpenHashMap<>();
		blockStateIds.defaultReturnValue(-1);
		blockPropertiesMap.forEach((intId, entries) -> {
			for (BlockEntry entry : entries) {
				addBlockStates(entry, blockStateIds, intId);
			}
		});

		tagPropertiesMap.forEach((intId, entries) -> {
			for (TagEntry entry : entries) {
				addTag(entry, blockStateIds, intId);
			}
		});

		return blockStateIds;
	}

	private static void addTag(TagEntry tagEntry, Object2IntMap<BlockState> idMap, int intId) {
		if (IrisPlatformHelpers.getInstance().isDevelopmentEnvironment()) {
			Iris.logger.warn("Failed to find the tag " + tagEntry.id());
		}
	}

	public static Map<Block, BlockRenderType> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
		if (blockPropertiesMap.isEmpty())
			return Object2ObjectMaps.emptyMap();

		Map<Block, BlockRenderType> blockTypeIds = new Reference2ReferenceOpenHashMap<>();

		blockPropertiesMap.forEach((id, blockType) -> {
			Identifier identifier = new Identifier(id.namespace(), id.name());

			Block block = Block.REGISTRY.get(identifier);

			blockTypeIds.put(block, blockType);
		});

		return blockTypeIds;
	}

	public static BlockLayer convertBlockToRenderType(BlockRenderType type) {
		if (type == null) {
			return null;
		}

		return switch (type) {
			case SOLID -> BlockLayer.SOLID;
			case CUTOUT -> BlockLayer.CUTOUT;
			case CUTOUT_MIPPED -> BlockLayer.CUTOUT_MIPPED;
			case TRANSLUCENT -> BlockLayer.TRANSLUCENT;
		};
	}

	private static void addBlockStates(BlockEntry entry, Object2IntMap<BlockState> idMap, int intId) {
		NamespacedId id = entry.id();
		Identifier identifier;
		try {
			identifier = new Identifier(id.namespace(), id.name());
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to get entry for " + intId, exception);
		}

		Block block = Block.REGISTRY.get(identifier);

		if (block == null || block == Blocks.AIR) {
			return;
		}

		Map<String, String> propertyPredicates = entry.propertyPredicates();

		if (propertyPredicates.isEmpty()) {
			// Just add all the states if there aren't any predicates
			for (BlockState state : block.stateDefinition().all()) {
				// NB: Using putIfAbsent means that the first successful mapping takes precedence
				//     Needed for OptiFine parity:
				//     https://github.com/IrisShaders/Iris/issues/1327
				idMap.putIfAbsent(state, intId);
			}

			return;
		}

		// As a result, we first collect each key=value pair in order to determine what properties we need to filter on.
		// We already get this from BlockEntry, but we convert the keys to `Property`s to ensure they exist and to avoid
		// string comparisons later.
		Map<Property<?>, String> properties = new LinkedHashMap<>();
		StateDefinition stateManager = block.stateDefinition();

		propertyPredicates.forEach((key, value) -> {
			Property<?> property = findProperty(stateManager, key);

			if (property == null) {
				Iris.logger.warn("Error while parsing the block ID map entry for \"" + "block." + intId + "\":");
				Iris.logger.warn("- The block " + identifier + " has no property with the name " + key + ", ignoring!");

				return;
			}

			properties.put(property, value);
		});

		// Once we have a list of properties and their expected values, we iterate over every possible state of this
		// block and check for ones that match the filters. This isn't particularly efficient, but it works!
		for (BlockState state : stateManager.all()) {
			if (checkState(state, properties)) {
				// NB: Using putIfAbsent means that the first successful mapping takes precedence
				//     Needed for OptiFine parity:
				//     https://github.com/IrisShaders/Iris/issues/1327
				idMap.putIfAbsent(state, intId);
			}
		}
	}

	private static Property<?> findProperty(StateDefinition stateManager, String name) {
		for (Property<?> property : stateManager.properties()) {
			if (property.getName().equals(name)) {
				return property;
			}
		}

		return null;
	}

	// We ignore generics here, the actual types don't matter because we just convert
	// them to strings anyways, and the compiler checks just get in the way.
	//
	// If you're able to rewrite this function without SuppressWarnings, feel free.
	// But otherwise it works fine.
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean checkState(BlockState state, Map<Property<?>, String> expectedValues) {
		for (Map.Entry<Property<?>, String> condition : expectedValues.entrySet()) {
			Property property = condition.getKey();
			String expectedValue = condition.getValue();

			String actualValue = property.getName(state.get(property));

			if (!expectedValue.equals(actualValue)) {
				return false;
			}
		}

		return true;
	}
}
