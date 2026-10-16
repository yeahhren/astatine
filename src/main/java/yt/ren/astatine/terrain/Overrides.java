package yt.ren.astatine.terrain;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

public final class Overrides {
	private interface Resolve extends Serializable {
		BlockState apply(Block block, BlockState state, WorldView world, BlockPos pos);
	}

	private interface Face extends Serializable {
		boolean apply(Block block, WorldView world, BlockPos pos, Direction face);
	}

	private interface Shape extends Serializable {
		void apply(Block block, WorldView world, BlockPos pos);
	}

	private interface Tint extends Serializable {
		int apply(Block block, WorldView world, BlockPos pos, int tint);
	}

	private static final String RESOLVE = name((Resolve) Block::resolveVirtualProperties);
	private static final String FACE = name((Face) Block::shouldRenderFace);
	private static final String SHAPE = name((Shape) Block::updateShape);
	private static final String TINT = name((Tint) Block::getColorTint);

	private Overrides() {
	}

	public static boolean resolvesVirtualProperties(Block block) {
		return overrides(block.getClass(), RESOLVE, BlockState.class, WorldView.class, BlockPos.class);
	}

	public static boolean cullsFaces(Block block) {
		return overrides(block.getClass(), FACE, WorldView.class, BlockPos.class, Direction.class)
			|| overrides(block.getClass(), SHAPE, WorldView.class, BlockPos.class);
	}

	public static boolean tints(Block block) {
		return overrides(block.getClass(), TINT, WorldView.class, BlockPos.class, int.class);
	}

	private static boolean overrides(Class<?> type, String name, Class<?>... parameters) {
		for (Class<?> current = type; current != null && current != Block.class; current = current.getSuperclass()) {
			try {
				current.getDeclaredMethod(name, parameters);
				return true;
			} catch (NoSuchMethodException ignored) {
			}
		}
		return false;
	}

	private static String name(Serializable reference) {
		try {
			Method replace = reference.getClass().getDeclaredMethod("writeReplace");
			replace.setAccessible(true);
			return ((SerializedLambda) replace.invoke(reference)).getImplMethodName();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to read a method reference", exception);
		}
	}
}
