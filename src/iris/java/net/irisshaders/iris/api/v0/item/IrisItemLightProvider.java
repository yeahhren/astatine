package net.irisshaders.iris.api.v0.item;

import net.minecraft.block.Block;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.joml.Vector3f;

public interface IrisItemLightProvider {

	Vector3f DEFAULT_LIGHT_COLOR = new Vector3f(1, 1, 1);

	default int getLightEmission(PlayerEntity player, ItemStack stack) {
		Block block = Block.byItem(stack.getItem());

		if (block != null) {
			return block.getLight();
		}

		return 0;
	}

	default Vector3f getLightColor(PlayerEntity player, ItemStack stack) {
		return DEFAULT_LIGHT_COLOR;
	}
}
