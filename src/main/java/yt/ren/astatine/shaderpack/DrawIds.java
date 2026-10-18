package yt.ren.astatine.shaderpack;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import yt.ren.astatine.render.GL;

import java.util.Locale;
import java.util.Map;

public class DrawIds {
	private static final Map<String, String> RENAMED = Map.ofEntries(
		Map.entry("XPOrb", "experience_orb"),
		Map.entry("ThrownEgg", "egg"),
		Map.entry("ThrownEnderpearl", "ender_pearl"),
		Map.entry("EyeOfEnderSignal", "eye_of_ender"),
		Map.entry("ThrownPotion", "potion"),
		Map.entry("ThrownExpBottle", "experience_bottle"),
		Map.entry("PrimedTnt", "tnt"),
		Map.entry("FallingSand", "falling_block"),
		Map.entry("FireworksRocketEntity", "firework_rocket"),
		Map.entry("MinecartRideable", "minecart"),
		Map.entry("MinecartChest", "chest_minecart"),
		Map.entry("MinecartFurnace", "furnace_minecart"),
		Map.entry("MinecartTNT", "tnt_minecart"),
		Map.entry("MinecartHopper", "hopper_minecart"),
		Map.entry("MinecartSpawner", "spawner_minecart"),
		Map.entry("MinecartCommandBlock", "command_block_minecart"),
		Map.entry("PigZombie", "zombified_piglin"),
		Map.entry("LavaSlime", "magma_cube"),
		Map.entry("WitherBoss", "wither"),
		Map.entry("MushroomCow", "mooshroom"),
		Map.entry("SnowMan", "snow_golem"),
		Map.entry("Ozelot", "ocelot"),
		Map.entry("VillagerGolem", "iron_golem"),
		Map.entry("EntityHorse", "horse"),
		Map.entry("EnderCrystal", "end_crystal"));

	private static int entity = -1;
	private static int blockEntity = -1;
	private static int item = -1;

	private DrawIds() {
	}

	public static String entityName(Entity value) {
		if (value instanceof PlayerEntity) {
			return "player";
		}

		if (value instanceof LightningBoltEntity) {
			return "lightning_bolt";
		}

		String key = Entities.getKey(value);

		if (key == null) {
			return null;
		}

		String renamed = RENAMED.get(key);

		if (renamed != null) {
			return renamed;
		}

		return key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

	public static int entityId(Entity value) {
		Object2IntFunction<NamespacedId> ids = WorldRenderingSettings.INSTANCE.getEntityIds();

		if (ids == null || value == null) {
			return -1;
		}

		String name = entityName(value);

		if (name == null) {
			return -1;
		}

		int id = ids.applyAsInt(new NamespacedId("minecraft", name));

		if (id == -1 && Entities.getKey(value) != null) {
			id = ids.applyAsInt(new NamespacedId("minecraft", Entities.getKey(value)));
		}

		return id;
	}

	public static int blockEntityId(BlockEntity value) {
		if (value == null || value.getWorld() == null) {
			return -1;
		}

		BlockState state = value.getWorld().getBlockState(value.getPos());
		var ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
		return ids == null || state == null ? -1 : ids.getOrDefault(state, -1);
	}

	public static int itemId(ItemStack stack) {
		Object2IntFunction<NamespacedId> ids = WorldRenderingSettings.INSTANCE.getItemIds();

		if (ids == null || stack == null || stack.getItem() == null) {
			return -1;
		}

		Identifier key = Item.REGISTRY.getKey(stack.getItem());
		return key == null ? -1 : ids.applyAsInt(new NamespacedId(key.getNamespace(), key.getPath()));
	}

	public static void setEntity(int id) {
		CapturedRenderingState.INSTANCE.setCurrentEntity(id);

		if (entity != id) {
			entity = id;
			send();
		}
	}

	public static void setBlockEntity(int id) {
		CapturedRenderingState.INSTANCE.setCurrentBlockEntity(id);

		if (blockEntity != id) {
			blockEntity = id;
			send();
		}
	}

	public static void setItem(int id) {
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);

		if (item != id) {
			item = id;
			send();
		}
	}

	public static void reset() {
		entity = -1;
		blockEntity = -1;
		item = -1;
		send();
	}

	private static void send() {
		if (Shaderpacks.isWorldActive()) {
			GL.drawIds(entity, blockEntity, item);
		}
	}
}
