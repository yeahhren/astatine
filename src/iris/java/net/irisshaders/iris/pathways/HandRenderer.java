package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.backend.MainTarget;

import java.util.Optional;
import java.util.OptionalDouble;

public class HandRenderer {
	public static final float DEPTH = 0.125F;
	private static HandRenderer instance;
	private boolean ACTIVE;
	private boolean renderingSolid;

	public static HandRenderer getInstance() {
		if (instance == null) {
			instance = new HandRenderer();
		}

		return instance;
	}

	public static void closeInstance() {
		instance = null;
	}

	private void setupGlState() {
		GL.matrixMode(GL11.GL_PROJECTION);
		GL.pushMatrix();
		GL.matrixMode(GL11.GL_MODELVIEW);
		GL.pushMatrix();
	}

	private void restoreGlState() {
		GL.matrixMode(GL11.GL_PROJECTION);
		GL.popMatrix();
		GL.matrixMode(GL11.GL_MODELVIEW);
		GL.popMatrix();
	}

	private boolean canRender() {
		Minecraft minecraft = Minecraft.getInstance();
		Entity camera = minecraft.getCamera();
		return !(minecraft.options.perspective != 0 || !(camera instanceof PlayerEntity) || minecraft.options.hideGui || (camera instanceof LivingEntity && ((LivingEntity) camera).isSleeping()) || minecraft.interactionManager.hidesGui());
	}

	public boolean isHandTranslucent(ItemStack itemStack) {
		if (itemStack == null || itemStack.getItem() == null) {
			return false;
		}

		Block block = Block.byItem(itemStack.getItem());

		return block != null && block.getRenderLayer() == BlockLayer.TRANSLUCENT;
	}

	private ItemStack heldItem() {
		var player = Minecraft.getInstance().player;
		return player == null ? null : player.getItemInHand();
	}

	public void renderSolid(float tickDelta, Runnable renderHand, WorldRenderingPipeline pipeline) {
		if (!canRender() || isHandTranslucent(heldItem()) || !Iris.isPackInUseQuick()) {
			return;
		}

		ACTIVE = true;

		setupGlState();

		pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);

		Minecraft.getInstance().profiler.push("iris_hand");

		renderingSolid = true;

		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
			.createRenderPass(() -> "Terrain", MainTarget.get().getColorTextureView(), Optional.empty(), MainTarget.get().getDepthTextureView(),
				OptionalDouble.empty())) {
			renderHand.run();
		}
		Minecraft.getInstance().profiler.pop();

		restoreGlState();
		renderingSolid = false;

		pipeline.setPhase(WorldRenderingPhase.NONE);

		ACTIVE = false;
	}

	public void renderTranslucent(float tickDelta, Runnable renderHand, WorldRenderingPipeline pipeline) {
		if (!canRender() || !isHandTranslucent(heldItem()) || !Iris.isPackInUseQuick()) {
			return;
		}

		ACTIVE = true;

		pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);

		setupGlState();

		Minecraft.getInstance().profiler.push("iris_hand_translucent");

		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
			.createRenderPass(() -> "Terrain", MainTarget.get().getColorTextureView(), Optional.empty(), MainTarget.get().getDepthTextureView(),
				OptionalDouble.empty())) {
			renderHand.run();
		}

		Minecraft.getInstance().profiler.pop();

		restoreGlState();

		pipeline.setPhase(WorldRenderingPhase.NONE);


		ACTIVE = false;
	}

	public boolean isActive() {
		return ACTIVE;
	}

	public boolean isRenderingSolid() {
		return renderingSolid;
	}

	public void destroy() {

	}

	public void endRender() {
	}
}
