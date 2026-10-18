package com.mojang.renderpearl.backend.opengl;

import com.mojang.renderpearl.api.pipeline.BlendFactor;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;

public final class GlConst {
	private GlConst() {
	}

	public static int toGl(BlendFactor factor) {
		return switch (factor) {
			case CONSTANT_ALPHA -> GL14C.GL_CONSTANT_ALPHA;
			case CONSTANT_COLOR -> GL14C.GL_CONSTANT_COLOR;
			case DST_ALPHA -> GL11C.GL_DST_ALPHA;
			case DST_COLOR -> GL11C.GL_DST_COLOR;
			case ONE -> GL11C.GL_ONE;
			case ONE_MINUS_CONSTANT_ALPHA -> GL14C.GL_ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> GL14C.GL_ONE_MINUS_CONSTANT_COLOR;
			case ONE_MINUS_DST_ALPHA -> GL11C.GL_ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> GL11C.GL_ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> GL11C.GL_ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> GL11C.GL_ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> GL11C.GL_SRC_ALPHA;
			case SRC_ALPHA_SATURATE -> GL11C.GL_SRC_ALPHA_SATURATE;
			case SRC_COLOR -> GL11C.GL_SRC_COLOR;
			case ZERO -> GL11C.GL_ZERO;
		};
	}
}
