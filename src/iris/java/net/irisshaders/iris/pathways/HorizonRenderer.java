package net.irisshaders.iris.pathways;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Renders the sky horizon. Vanilla Minecraft simply uses the "clear color" for its horizon, and then draws a plane
 * above the player. This class extends the sky rendering so that an inverted octagonal cone is drawn around the player instead,
 * allowing shaders to perform more advanced sky rendering.
 * <p>
 * However, the horizon rendering is designed so that when sky shaders are not being used, it looks almost exactly the
 * same as vanilla sky rendering, except a few almost entirely imperceptible differences where the walls
 * of the inverted octagonal cone intersect the top plane.
 */
public class HorizonRenderer {
	/**
	 * The Y coordinate of the top skybox plane. Acts as the upper bound for the horizon cone, since the cone lies
	 * between the bottom and top skybox planes.
	 */
	private static final float TOP = 16.0F;

	/**
	 * The Y coordinate of the bottom skybox plane. Acts as the lower bound for the horizon cone, since the cone lies
	 * between the bottom and top skybox planes.
	 */
	private static final float BOTTOM = -16.0F;

	private int currentRenderDistance;

	public HorizonRenderer() {
		currentRenderDistance = Minecraft.getInstance().options.viewDistance;
	}

	private void buildHorizon(int radius, BufferBuilder consumer) {
		if (radius > 256) {
			// Prevent the cone from getting too large, this causes issues on some shader packs that modify the vanilla
			// sky if we don't do this.
			radius = 256;
		}

		consumer.vertex(0.0F, BOTTOM, 0.0F).nextVertex();

		for (int i = 0; i <= 8; i++) {
			float angle = (float) (-i * Math.PI / 4.0);
			float x = (float) (radius * Math.cos(angle));
			float z = (float) (radius * Math.sin(angle));
			consumer.vertex(x, TOP, z).nextVertex();
		}
	}

	public void renderHorizon(Vector4f fogColor) {
		if (currentRenderDistance != Minecraft.getInstance().options.viewDistance) {
			currentRenderDistance = Minecraft.getInstance().options.viewDistance;
		}

		GlStateManager.disableTexture();
		GlStateManager.depthMask(false);
		GlStateManager.color4f(fogColor.x(), fogColor.y(), fogColor.z(), fogColor.w());

		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.getBuffer();
		buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormat.POSITION);
		buildHorizon(currentRenderDistance * 16, buffer);
		tesselator.end();

		GlStateManager.depthMask(true);
		GlStateManager.enableTexture();
	}

	public void destroy() {
	}
}
