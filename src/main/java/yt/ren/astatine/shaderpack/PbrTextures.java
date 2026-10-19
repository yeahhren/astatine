package yt.ren.astatine.shaderpack;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pbr.format.TextureFormat;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import net.irisshaders.iris.pbr.mipmap.ChannelMipmapGenerator;
import net.irisshaders.iris.pbr.mipmap.CustomMipmapGenerator;
import net.irisshaders.iris.pbr.mipmap.LinearBlendFunction;
import net.irisshaders.iris.pbr.texture.PBRType;
import net.irisshaders.iris.pbr.util.ImageManipulationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.mixin.shaderpack.SimpleTextureAccessor;
import yt.ren.astatine.mixin.shaderpack.TextureAtlasAccessor;
import yt.ren.astatine.mixin.shaderpack.TextureManagerAccessor;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.render.TextureBinding;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL12.*;

public class PbrTextures {
	private static final int UPLOAD_TEXELS = 1 << 20;
	private static final CustomMipmapGenerator LINEAR_MIPMAP_GENERATOR = new ChannelMipmapGenerator(LinearBlendFunction.INSTANCE,
		LinearBlendFunction.INSTANCE, LinearBlendFunction.INSTANCE, LinearBlendFunction.INSTANCE);

	private static final Map<Texture, long[]> holders = new IdentityHashMap<>();
	private static final Map<Long, long[]> companions = new HashMap<>();
	private static final Map<TextureAtlasSprite, List<AnimatedSprite>> animated = new IdentityHashMap<>();
	private static final List<Long> spare = new ArrayList<>();
	private static long defaultNormals;
	private static long defaultSpecular;

	private PbrTextures() {
	}

	public static void loadAll() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.getTextureManager() == null) {
			return;
		}

		createDefaults();

		for (Texture texture : List.copyOf(((TextureManagerAccessor) minecraft.getTextureManager()).astatine$textures().values())) {
			load(texture);
		}
	}

	public static void clear() {
		for (Map.Entry<Texture, long[]> entry : holders.entrySet()) {
			if (entry.getKey() instanceof TextureHolder holder) {
				Native.textureCompanions(holder.astatine$texture(), 0L, 0L);
			}

			for (long handle : entry.getValue()) {
				if (handle != 0L) {
					Native.textureDetach(handle);
					spare.add(handle);
				}
			}
		}

		holders.clear();
		companions.clear();
		animated.clear();
	}

	public static long[] companions(long albedo) {
		return companions.get(albedo);
	}

	public static long defaultNormals() {
		createDefaults();
		return defaultNormals;
	}

	public static long defaultSpecular() {
		createDefaults();
		return defaultSpecular;
	}

	public static void onSpriteTick(TextureAtlasSprite sprite, int activeFrame) {
		List<AnimatedSprite> sprites = animated.get(sprite);

		if (sprites == null) {
			return;
		}

		for (AnimatedSprite entry : sprites) {
			int frame = Math.floorMod(activeFrame, entry.frames.length);

			if (frame != entry.shownFrame) {
				entry.shownFrame = frame;
				uploadFrame(entry, frame);
			}
		}
	}

	private static void uploadFrame(AnimatedSprite entry, int frame) {
		int[][] levels = entry.frames[frame];
		long buffer = Memory.INSTANCE.allocate((long) levels[0].length * 4L);

		try {
			for (int level = 0; level < levels.length; level++) {
				int levelWidth = Math.max(entry.width >> level, 1);
				int levelHeight = Math.max(entry.height >> level, 1);
				int[] pixels = levels[level];

				for (int i = 0; i < levelWidth * levelHeight; i++) {
					Memory.INSTANCE.putInt(buffer + i * 4L, pixels[i]);
				}

				Native.textureUpload(entry.handle, level, entry.x >> level, entry.y >> level, levelWidth, levelHeight, buffer);
			}
		} finally {
			Memory.INSTANCE.free(buffer);
		}
	}

	public static void onRegistered(Texture texture) {
		if (!Shaderpacks.isActive()) {
			return;
		}

		createDefaults();
		release(texture);
		load(texture);
	}

	private static void release(Texture texture) {
		long[] previous = holders.remove(texture);

		if (texture instanceof TextureHolder holder) {
			companions.remove(holder.astatine$texture());
		}

		if (previous != null) {
			animated.values().forEach(list -> list.removeIf(entry -> Arrays.stream(previous).anyMatch(handle -> handle == entry.handle)));
			animated.values().removeIf(List::isEmpty);
		}

		if (previous == null) {
			return;
		}

		for (long handle : previous) {
			if (handle != 0L) {
				Native.textureDetach(handle);
				spare.add(handle);
			}
		}
	}

	private static void createDefaults() {
		if (defaultNormals != 0L) {
			return;
		}

		defaultNormals = solid(argb(PBRType.NORMAL.getDefaultValue()));
		defaultSpecular = solid(argb(PBRType.SPECULAR.getDefaultValue()));
		Native.textureCompanions(0L, defaultNormals, defaultSpecular);
	}

	private static int argb(int rgba) {
		return (rgba & 0xFF) << 24 | rgba >>> 8;
	}

	private static long solid(int color) {
		long handle = Native.textureCreate();
		upload(handle, new int[][]{{color}}, 1, 1, false);
		return handle;
	}

	private static void load(Texture texture) {
		if (!(texture instanceof TextureHolder holder) || holders.containsKey(texture)) {
			return;
		}

		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		long normals = 0L;
		long specular = 0L;

		try {
			if (texture instanceof TextureAtlas atlas) {
				normals = loadAtlas(resources, atlas, holder.astatine$texture(), PBRType.NORMAL);
				specular = loadAtlas(resources, atlas, holder.astatine$texture(), PBRType.SPECULAR);
			} else if (texture instanceof SimpleTexture simple) {
				Identifier location = ((SimpleTextureAccessor) simple).astatine$location();
				normals = loadSimple(resources, location, PBRType.NORMAL);
				specular = loadSimple(resources, location, PBRType.SPECULAR);
			}
		} catch (RuntimeException e) {
			Iris.logger.warn("Failed to load PBR textures", e);
		}

		holders.put(texture, new long[]{normals, specular});
		companions.put(holder.astatine$texture(), new long[]{normals, specular});

		if (normals != 0L || specular != 0L) {
			Native.textureCompanions(holder.astatine$texture(), normals, specular);
		}
	}

	private static BufferedImage read(ResourceManager resources, Identifier location) {
		try (InputStream stream = resources.getResource(location).asStream()) {
			return ImageIO.read(stream);
		} catch (IOException e) {
			return null;
		}
	}

	private static boolean animated(ResourceManager resources, Identifier location) {
		try (InputStream stream = resources.getResource(new Identifier(location.getNamespace(), location.getPath() + ".mcmeta")).asStream()) {
			return stream != null;
		} catch (IOException e) {
			return false;
		}
	}

	private static Identifier suffixed(Identifier location, PBRType type) {
		return new Identifier(location.getNamespace(), type.appendSuffix(location.getPath()));
	}

	private static long loadSimple(ResourceManager resources, Identifier location, PBRType type) {
		BufferedImage image = read(resources, suffixed(location, type));

		if (image == null) {
			return 0L;
		}

		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		return create(new int[][]{pixels}, width, height, type, 0);
	}

	private static long loadAtlas(ResourceManager resources, TextureAtlas atlas, long albedo, PBRType type) {
		TextureAtlasAccessor accessor = (TextureAtlasAccessor) atlas;
		int width = Native.textureWidth(albedo);
		int height = Native.textureHeight(albedo);

		if (width <= 0 || height <= 0) {
			return 0L;
		}

		int mipLevel = Math.max(accessor.astatine$maxMipLevel(), 0);
		CustomMipmapGenerator generator = generator(type);
		int[][] levels = null;
		List<AnimatedSprite> frames = new ArrayList<>();

		for (TextureAtlasSprite sprite : accessor.astatine$stitchedSprites().values()) {
			Identifier name = new Identifier(sprite.getName());
			Identifier location = new Identifier(name.getNamespace(), "textures/" + name.getPath() + ".png");
			BufferedImage image = read(resources, suffixed(location, type));

			if (image == null) {
				continue;
			}

			if (levels == null) {
				levels = new int[mipLevel + 1][];

				for (int level = 0; level <= mipLevel; level++) {
					levels[level] = new int[Math.max(width >> level, 1) * Math.max(height >> level, 1)];
					Arrays.fill(levels[level], argb(type.getDefaultValue()));
				}
			}

			int spriteWidth = sprite.getWidth();
			int spriteHeight = sprite.getHeight();
			int frameWidth = image.getWidth();
			int frameHeight = animated(resources, suffixed(location, type)) ? Math.min(image.getWidth(), image.getHeight()) : image.getHeight();
			int frameCount = sprite.isAnimated() ? Math.max(image.getHeight() / frameHeight, 1) : 1;
			int[][][] spriteFrames = new int[frameCount][][];

			for (int frame = 0; frame < frameCount; frame++) {
				int[] framePixels = image.getRGB(0, frame * frameHeight, frameWidth, frameHeight, null, 0, frameWidth);

				if (frameWidth != spriteWidth || frameHeight != spriteHeight) {
					framePixels = spriteWidth % frameWidth == 0 && spriteHeight % frameHeight == 0
						? ImageManipulationUtil.scaleNearestNeighbor(framePixels, frameWidth, frameHeight, spriteWidth, spriteHeight)
						: ImageManipulationUtil.scaleBilinear(framePixels, frameWidth, frameHeight, spriteWidth, spriteHeight);
				}

				spriteFrames[frame] = mipLevel > 0 ? generator.generateMipLevels(new int[][]{framePixels}, spriteWidth, spriteHeight, mipLevel) :
					new int[][]{framePixels};
			}

			if (frameCount > 1) {
				frames.add(new AnimatedSprite(sprite, spriteFrames, sprite.getX(), sprite.getY(), spriteWidth, spriteHeight));
			}

			int[][] spriteLevels = spriteFrames[0];

			for (int level = 0; level <= mipLevel; level++) {
				int levelWidth = Math.max(width >> level, 1);
				int levelHeight = Math.max(height >> level, 1);
				int spriteLevelWidth = Math.max(spriteWidth >> level, 1);
				int spriteLevelHeight = Math.max(spriteHeight >> level, 1);
				int originX = sprite.getX() >> level;
				int originY = sprite.getY() >> level;
				int[] target = levels[level];
				int[] from = spriteLevels[level];

				for (int y = 0; y < spriteLevelHeight; y++) {
					int targetY = originY + y;

					if (targetY < 0 || targetY >= levelHeight) {
						continue;
					}

					for (int x = 0; x < spriteLevelWidth; x++) {
						int targetX = originX + x;

						if (targetX >= 0 && targetX < levelWidth) {
							target[targetY * levelWidth + targetX] = from[y * spriteLevelWidth + x];
						}
					}
				}
			}
		}

		if (levels == null) {
			return 0L;
		}

		long handle = create(levels, width, height, type, 0);

		for (AnimatedSprite entry : frames) {
			entry.handle = handle;
			animated.computeIfAbsent(entry.sprite, ignored -> new ArrayList<>()).add(entry);
		}

		return handle;
	}

	private static CustomMipmapGenerator generator(PBRType type) {
		TextureFormat format = TextureFormatLoader.getFormat();
		CustomMipmapGenerator generator = format == null ? null : format.getMipmapGenerator(type);
		return generator == null ? LINEAR_MIPMAP_GENERATOR : generator;
	}

	private static long create(int[][] levels, int width, int height, PBRType type, int mipLevel) {
		TextureFormat format = TextureFormatLoader.getFormat();
		int[][] mipmaps = mipLevel > 0 ? generator(type).generateMipLevels(levels, width, height, mipLevel) : levels;
		boolean interpolate = format == null || format.canInterpolateValues(type);
		long handle = spare.isEmpty() ? Native.textureCreate() : spare.remove(spare.size() - 1);
		upload(handle, mipmaps, width, height, interpolate);
		return handle;
	}

	private static void upload(long handle, int[][] levels, int width, int height, boolean interpolate) {
		TextureBinding.bind(handle);
		int maxLevel = levels.length - 1;
		GL.textureParameter(GL_TEXTURE_MAX_LEVEL, maxLevel);
		GL.textureParameter(GL_TEXTURE_MIN_FILTER,
			maxLevel > 0 ? (interpolate ? GL_NEAREST_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST) : GL_NEAREST);
		GL.textureParameter(GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		Native.textureAllocate(handle, width, height);
		long buffer = Memory.INSTANCE.allocate((long) Math.min(UPLOAD_TEXELS, width * height) * 4L);

		try {
			for (int level = 0; level < levels.length; level++) {
				int levelWidth = Math.max(width >> level, 1);
				int levelHeight = Math.max(height >> level, 1);
				int rows = Math.max(1, Math.min(levelHeight, UPLOAD_TEXELS / levelWidth));
				int[] pixels = levels[level];

				for (int y = 0; y < levelHeight; y += rows) {
					int count = Math.min(rows, levelHeight - y);

					for (int i = 0; i < count * levelWidth; i++) {
						Memory.INSTANCE.putInt(buffer + i * 4L, pixels[y * levelWidth + i]);
					}

					Native.textureUpload(handle, level, 0, y, levelWidth, count, buffer);
				}
			}
		} finally {
			Memory.INSTANCE.free(buffer);
		}
	}

	private static final class AnimatedSprite {
		final TextureAtlasSprite sprite;
		final int[][][] frames;
		final int x;
		final int y;
		final int width;
		final int height;
		long handle;
		int shownFrame;

		AnimatedSprite(TextureAtlasSprite sprite, int[][][] frames, int x, int y, int width, int height) {
			this.sprite = sprite;
			this.frames = frames;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
}
