package yt.ren.astatine.gui;

import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.minecraft.client.resource.language.I18n;
import yt.ren.astatine.config.AstatineOptions;
import yt.ren.astatine.config.ConfigOption;
import yt.ren.astatine.config.VanillaOptions;
import yt.ren.astatine.gui.options.Option;
import yt.ren.astatine.gui.options.OptionFlag;
import yt.ren.astatine.gui.options.OptionGroup;
import yt.ren.astatine.gui.options.OptionImpact;
import yt.ren.astatine.gui.options.OptionPage;
import yt.ren.astatine.gui.options.control.ControlValueFormatter;
import yt.ren.astatine.gui.options.control.CyclingControl;
import yt.ren.astatine.gui.options.control.SliderControl;
import yt.ren.astatine.gui.options.control.TickBoxControl;
import yt.ren.astatine.shaderpack.Shaderpacks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AstatineOptionPages {
	private AstatineOptionPages() {
	}

	public static List<OptionPage> create() {
		VanillaOptions.init();

		List<OptionPage> pages = new ArrayList<>();
		pages.add(general());
		pages.add(quality());
		pages.add(performance());

		if (Shaderpacks.isInitialized()) {
			pages.add(shaders());
		}

		return pages;
	}

	private static OptionPage general() {
		List<OptionGroup> groups = new ArrayList<>();

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.RENDER_DISTANCE)
				.setName("options.renderDistance")
				.setTooltip("astatine.options.view_distance.tooltip")
				.setControl(option -> new SliderControl(option, 2, VanillaOptions.RENDER_DISTANCE.max(), 1, ControlValueFormatter.translateVariable("astatine.options.chunks.value")))
				.setImpact(OptionImpact.HIGH)
				.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
				.build())
			.add(Option.createBuilder(VanillaOptions.BRIGHTNESS)
				.setName("options.gamma")
				.setTooltip("astatine.options.brightness.tooltip")
				.setControl(option -> new SliderControl(option, 0, 100, 1, ControlValueFormatter.brightness()))
				.build())
			.build());

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.GUI_SCALE)
				.setName("options.guiScale")
				.setTooltip("astatine.options.gui_scale.tooltip")
				.setControl(option -> new SliderControl(option, 0, 3, 1, ControlValueFormatter.guiScale()))
				.build())
			.add(Option.createBuilder(VanillaOptions.FULLSCREEN)
				.setName("options.fullscreen")
				.setTooltip("astatine.options.fullscreen.tooltip")
				.setControl(TickBoxControl::new)
				.build())
			.add(Option.createBuilder(VanillaOptions.VSYNC)
				.setName("options.vsync")
				.setTooltip("astatine.options.v_sync.tooltip")
				.setControl(TickBoxControl::new)
				.setImpact(OptionImpact.VARIES)
				.build())
			.add(Option.createBuilder(VanillaOptions.MAX_FRAMERATE)
				.setName("options.framerateLimit")
				.setTooltip("astatine.options.fps_limit.tooltip")
				.setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
				.build())
			.build());

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.VIEW_BOBBING)
				.setName("options.viewBobbing")
				.setTooltip("astatine.options.view_bobbing.tooltip")
				.setControl(TickBoxControl::new)
				.build())
			.add(Option.createBuilder(VanillaOptions.ANAGLYPH)
				.setName("options.anaglyph")
				.setTooltip("astatine.options.anaglyph.tooltip")
				.setControl(TickBoxControl::new)
				.setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
				.build())
			.build());

		return new OptionPage(I18n.translate("stat.generalButton"), groups);
	}

	private static OptionPage quality() {
		List<OptionGroup> groups = new ArrayList<>();

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.GRAPHICS)
				.setName("options.graphics")
				.setTooltip("astatine.options.graphics_quality.tooltip")
				.setControl(option -> new CyclingControl<>(option, VanillaOptions.Graphics.class,
					names("options.graphics.fast", "options.graphics.fancy")))
				.setImpact(OptionImpact.HIGH)
				.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
				.build())
			.build());

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.CLOUDS)
				.setName("options.renderClouds")
				.setTooltip("astatine.options.clouds_quality.tooltip")
				.setControl(option -> new CyclingControl<>(option, VanillaOptions.Clouds.class,
					names("options.off", "options.graphics.fast", "options.graphics.fancy")))
				.setImpact(OptionImpact.LOW)
				.build())
			.add(Option.createBuilder(VanillaOptions.PARTICLES)
				.setName("options.particles")
				.setTooltip("astatine.options.particle_quality.tooltip")
				.setControl(option -> new CyclingControl<>(option, VanillaOptions.Particles.class,
					names("options.particles.all", "options.particles.decreased", "options.particles.minimal")))
				.setImpact(OptionImpact.MEDIUM)
				.build())
			.add(Option.createBuilder(VanillaOptions.SMOOTH_LIGHTING)
				.setName("options.ao")
				.setTooltip("astatine.options.smooth_lighting.tooltip")
				.setControl(option -> new CyclingControl<>(option, VanillaOptions.SmoothLighting.class,
					names("options.ao.off", "options.ao.min", "options.ao.max")))
				.setImpact(OptionImpact.LOW)
				.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
				.build())
			.add(Option.createBuilder(VanillaOptions.ENTITY_SHADOWS)
				.setName("options.entityShadows")
				.setTooltip("astatine.options.entity_shadows.tooltip")
				.setControl(TickBoxControl::new)
				.setImpact(OptionImpact.MEDIUM)
				.build())
			.add(Option.createBuilder(VanillaOptions.ALTERNATE_BLOCKS)
				.setName("options.blockAlternatives")
				.setTooltip("astatine.options.alternate_blocks.tooltip")
				.setControl(TickBoxControl::new)
				.setImpact(OptionImpact.LOW)
				.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
				.build())
			.build());

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.MIPMAP_LEVELS)
				.setName("options.mipmapLevels")
				.setTooltip("astatine.options.mipmap_levels.tooltip")
				.setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
				.setImpact(OptionImpact.MEDIUM)
				.build())
			.build());

		return new OptionPage(I18n.translate("astatine.options.pages.quality"), groups);
	}

	private static OptionPage performance() {
		List<OptionGroup> groups = new ArrayList<>();

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(AstatineOptions.CHUNK_UPDATE_THREADS)
				.setName("astatine.options.chunk_update_threads.name")
				.setTooltip("astatine.options.chunk_update_threads.tooltip")
				.setControl(option -> new SliderControl(option, 0, AstatineOptions.MAX_CHUNK_THREADS, 1,
					ControlValueFormatter.quantityOrDisabled(I18n.translate("astatine.options.threads"), I18n.translate("astatine.options.default"))))
				.setImpact(OptionImpact.HIGH)
				.build())
			.build());

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(VanillaOptions.USE_VBO)
				.setName("options.vbo")
				.setTooltip("astatine.options.vbo.tooltip")
				.setControl(TickBoxControl::new)
				.setImpact(OptionImpact.LOW)
				.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
				.build())
			.build());

		return new OptionPage(I18n.translate("astatine.options.pages.performance"), groups);
	}

	private static OptionPage shaders() {
		List<OptionGroup> groups = new ArrayList<>();

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(AstatineOptions.SHADERS_ENABLED)
				.setName("astatine.options.shaders_enabled.name")
				.setTooltip("astatine.options.shaders_enabled.tooltip")
				.setControl(TickBoxControl::new)
				.setImpact(OptionImpact.HIGH)
				.setFlags(OptionFlag.REQUIRES_SHADER_RELOAD)
				.build())
			.add(Option.createBuilder(AstatineOptions.SHADOW_DISTANCE)
				.setName("options.iris.shadowDistance")
				.setTooltip(IrisVideoSettings.isShadowDistanceSliderEnabled()
					? "options.iris.shadowDistance.sodium_tooltip" : "options.iris.shadowDistance.disabled")
				.setControl(option -> new SliderControl(option, 0, 32, 1,
					ControlValueFormatter.quantityOrDisabled(I18n.translate("astatine.options.chunks"), I18n.translate("options.off"))))
				.setImpact(OptionImpact.HIGH)
				.setEnabled(IrisVideoSettings::isShadowDistanceSliderEnabled)
				.build())
			.add(colorSpace())
			.build());

		groups.add(OptionGroup.createBuilder()
			.add(Option.createBuilder(AstatineOptions.ALLOW_UNKNOWN_SHADERS)
				.setName("astatine.options.allow_unknown_shaders.name")
				.setTooltip("astatine.options.allow_unknown_shaders.tooltip")
				.setControl(TickBoxControl::new)
				.setFlags(OptionFlag.REQUIRES_SHADER_RELOAD)
				.build())
			.add(Option.createBuilder(AstatineOptions.DEBUG_OPTIONS)
				.setName("astatine.options.shader_debug.name")
				.setTooltip("astatine.options.shader_debug.tooltip")
				.setControl(TickBoxControl::new)
				.setImpact(OptionImpact.MEDIUM)
				.setFlags(OptionFlag.REQUIRES_SHADER_RELOAD)
				.build())
			.build());

		return new OptionPage(I18n.translate("astatine.options.pages.shaders"), groups);
	}

	private static Option<ColorSpace> colorSpace() {
		ConfigOption.Choice<ColorSpace> config = AstatineOptions.COLOR_SPACE;

		return Option.createBuilder(config)
			.setName("options.iris.colorSpace")
			.setTooltip("options.iris.colorSpace.sodium_tooltip")
			.setControl(option -> new CyclingControl<>(option, ColorSpace.class,
				Arrays.stream(ColorSpace.values()).map(ColorSpace::getName).toArray(String[]::new)))
			.setImpact(OptionImpact.LOW)
			.build();
	}

	private static String[] names(String... keys) {
		return Arrays.stream(keys).map(I18n::translate).toArray(String[]::new);
	}
}
