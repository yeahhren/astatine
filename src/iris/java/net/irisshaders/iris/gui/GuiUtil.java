package net.irisshaders.iris.gui;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Map;

/**
 * Class serving as abstraction and
 * centralization for common GUI
 * rendering/other code calls.
 * <p>
 * Helps allow for easier portability
 * to Minecraft 1.17 by abstracting
 * some code that will be changed.
 */
public final class GuiUtil {
	public static final Identifier IRIS_WIDGETS_TEX = new Identifier("iris", "textures/gui/widgets.png");
	private static final Text ELLIPSIS = new LiteralText("...");
	private static final Formatting[] COLORS = {Formatting.BLACK, Formatting.DARK_BLUE, Formatting.DARK_GREEN, Formatting.DARK_AQUA,
		Formatting.DARK_RED, Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY, Formatting.DARK_GRAY, Formatting.BLUE,
		Formatting.GREEN, Formatting.AQUA, Formatting.RED, Formatting.LIGHT_PURPLE, Formatting.YELLOW, Formatting.WHITE};
	private static final int[] COLOR_VALUES = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555,
		0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private GuiUtil() {
	}

	private static Minecraft client() {
		return Minecraft.getInstance();
	}

	public static TextRenderer font() {
		return client().textRenderer;
	}

	public static Text literal(String text) {
		return new LiteralText(text);
	}

	public static Text empty() {
		return new LiteralText("");
	}

	public static Text translatable(String key, Object... format) {
		String packTranslation = packTranslation(key);

		if (packTranslation != null) {
			return new LiteralText(format.length == 0 ? packTranslation : String.format(packTranslation, format));
		}

		return new LiteralText(I18n.translate(key, format));
	}

	public static Text withStyle(Text text, Formatting... formats) {
		Style style = text.getStyle();

		for (Formatting format : formats) {
			if (format.isColor()) {
				style.setColor(format);
			} else if (format == Formatting.BOLD) {
				style.setBold(true);
			} else if (format == Formatting.ITALIC) {
				style.setItalic(true);
			} else if (format == Formatting.UNDERLINE) {
				style.setUnderlined(true);
			} else if (format == Formatting.STRIKETHROUGH) {
				style.setStrikethrough(true);
			} else if (format == Formatting.OBFUSCATED) {
				style.setObfuscated(true);
			}
		}

		return text;
	}

	public static Text withColor(Text text, int rgb) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;

		for (int i = 0; i < COLOR_VALUES.length; i++) {
			long dr = ((rgb >> 16) & 0xFF) - ((COLOR_VALUES[i] >> 16) & 0xFF);
			long dg = ((rgb >> 8) & 0xFF) - ((COLOR_VALUES[i] >> 8) & 0xFF);
			long db = (rgb & 0xFF) - (COLOR_VALUES[i] & 0xFF);
			long distance = dr * dr + dg * dg + db * db;

			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}

		text.getStyle().setColor(COLORS[best]);
		return text;
	}

	public static int width(Text text) {
		return font().getWidth(text.getFormattedString());
	}

	/**
	 * Binds Iris's widgets texture to be
	 * used for succeeding draw calls.
	 */
	public static void bindIrisWidgetsTexture() {
		client().getTextureManager().bind(IRIS_WIDGETS_TEX);
	}

	/**
	 * Draws a button. Button textures must be mapped with the
	 * same coordinates as those on the vanilla widgets texture.
	 *
	 * @param x        X position of the left of the button
	 * @param y        Y position of the top of the button
	 * @param width    Width of the button, maximum 398
	 * @param height   Height of the button, maximum 20
	 * @param hovered  Whether the button is being hovered over with the mouse
	 * @param disabled Whether the button should use the "disabled" texture
	 */
	public static void drawButton(IrisGraphics guiGraphics, int x, int y, int width, int height, boolean hovered, boolean disabled) {
		// Create variables for half of the width and height.
		// Will not be exact when width and height are odd, but
		// that case is handled within the draw calls.
		int halfWidth = width / 2;
		int halfHeight = height / 2;

		// V offset for which button texture to use
		int vOffset = disabled ? 46 : hovered ? 86 : 66;

		// Top left section
		guiGraphics.blit(IRIS_WIDGETS_TEX, x, y, 0, vOffset, halfWidth, halfHeight, 256, 256);
		// Top right section
		guiGraphics.blit(IRIS_WIDGETS_TEX, x + halfWidth, y, 200 - (width - halfWidth), vOffset, width - halfWidth, halfHeight, 256, 256);
		// Bottom left section
		guiGraphics.blit(IRIS_WIDGETS_TEX, x, y + halfHeight, 0, vOffset + (20 - (height - halfHeight)), halfWidth, height - halfHeight,
			256, 256);
		// Bottom right section
		guiGraphics.blit(IRIS_WIDGETS_TEX, x + halfWidth, y + halfHeight, 200 - (width - halfWidth), vOffset + (20 - (height - halfHeight)),
			width - halfWidth, height - halfHeight, 256, 256);
	}

	/**
	 * Draws a translucent black panel
	 * with a light border.
	 *
	 * @param x      The x position of the panel
	 * @param y      The y position of the panel
	 * @param width  The width of the panel
	 * @param height The height of the panel
	 */
	public static void drawPanel(IrisGraphics guiGraphics, int x, int y, int width, int height) {
		int borderColor = 0xDEDEDEDE;
		int innerColor = 0xDE000000;

		// Top border section
		guiGraphics.fill(x, y, x + width, y + 1, borderColor);
		// Bottom border section
		guiGraphics.fill(x, (y + height) - 1, x + width, y + height, borderColor);
		// Left border section
		guiGraphics.fill(x, y + 1, x + 1, (y + height) - 1, borderColor);
		// Right border section
		guiGraphics.fill((x + width) - 1, y + 1, x + width, (y + height) - 1, borderColor);
		// Inner section
		guiGraphics.fill(x + 1, y + 1, (x + width) - 1, (y + height) - 1, innerColor);
	}

	/**
	 * Draws a text with a panel behind it.
	 *
	 * @param text The text component to draw
	 * @param x    The x position of the panel
	 * @param y    The y position of the panel
	 */
	public static void drawTextPanel(TextRenderer font, IrisGraphics guiGraphics, Text text, int x, int y) {
		drawPanel(guiGraphics, x, y, width(text) + 8, 16);
		guiGraphics.text(font, text, x + 4, y + 4, 0xFFFFFFFF);
	}

	/**
	 * Shorten a text to a specific length, adding an ellipsis (...)
	 * to the end if shortened.
	 * <p>
	 * Text may lose formatting.
	 *
	 * @param font  Font to use for determining the width of text
	 * @param text  Text to shorten
	 * @param width Width to shorten text to
	 * @return a shortened text
	 */
	public static Text shortenText(TextRenderer font, Text text, int width) {
		if (width(text) > width) {
			Text shortened = new LiteralText(font.trim(text.getString(), width - width(ELLIPSIS))).append(ELLIPSIS.copy());
			shortened.setStyle(text.getStyle().copy());
			return shortened;
		}
		return text;
	}

	/**
	 * Creates a new translated text, if a translation
	 * is present. If not, will return the default text
	 * component passed.
	 *
	 * @param defaultText     Default text to use if no translation is found
	 * @param translationDesc Translation key to try and use
	 * @param format          Formatting arguments for the translated text, if created
	 * @return the translated text if found, otherwise the default provided
	 */
	public static Text translateOrDefault(Text defaultText, String translationDesc, Object... format) {
		if (hasTranslation(translationDesc)) {
			return translatable(translationDesc, format);
		}
		return defaultText;
	}

	public static boolean hasTranslation(String key) {
		return packTranslation(key) != null || !I18n.translate(key).equals(key);
	}

	public static String translationOrEmpty(String key) {
		String packTranslation = packTranslation(key);

		if (packTranslation != null) {
			return packTranslation;
		}

		String translated = I18n.translate(key);
		return translated.equals(key) ? "" : translated;
	}

	private static String packTranslation(String key) {
		var pack = Iris.getCurrentPack();

		if (pack.isEmpty()) {
			return null;
		}

		var languages = pack.get().getLanguageMap();
		String code = client().getLanguageManager().getLanguage().getCode().toLowerCase(Locale.ROOT);
		Map<String, String> translations = languages.getTranslations(code);
		String value = translations == null ? null : translations.get(key);

		if (value == null) {
			Map<String, String> fallback = languages.getTranslations("en_us");
			value = fallback == null ? null : fallback.get(key);
		}

		return value;
	}

	/**
	 * Plays the {@code UI_BUTTON_CLICK} sound event as a
	 * master sound effect.
	 * <p>
	 * Used in non-{@code ButtonWidget} UI elements upon click
	 * or other action.
	 */
	public static void playButtonClickSound() {
		client().getSoundManager().play(SimpleSoundInstance.of(new Identifier("gui.button.press"), 1.0F));
	}

	/**
	 * A class representing a section of a
	 * texture, to be easily drawn in GUIs.
	 */
	public static class Icon {
		public static final Icon SEARCH = new Icon(0, 0, 7, 8);
		public static final Icon CLOSE = new Icon(7, 0, 5, 6);
		public static final Icon REFRESH = new Icon(12, 0, 10, 10);
		public static final Icon EXPORT = new Icon(22, 0, 7, 8);
		public static final Icon EXPORT_COLORED = new Icon(29, 0, 7, 8);
		public static final Icon IMPORT = new Icon(22, 8, 7, 8);
		public static final Icon IMPORT_COLORED = new Icon(29, 8, 7, 8);

		private final int u;
		private final int v;
		private final int width;
		private final int height;

		public Icon(int u, int v, int width, int height) {
			this.u = u;
			this.v = v;
			this.width = width;
			this.height = height;
		}

		/**
		 * Draws this icon to the screen at the specified coordinates.
		 *
		 * @param x The x position to draw the icon at (left)
		 * @param y The y position to draw the icon at (top)
		 */
		public void draw(IrisGraphics guiGraphics, int x, int y) {
			// Draw the texture to the screen
			guiGraphics.blit(IRIS_WIDGETS_TEX, x, y, u, v, width, height, 256, 256);
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}
	}
}
