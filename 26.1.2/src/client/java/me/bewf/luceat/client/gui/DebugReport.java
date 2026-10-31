package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.glass.Glass;
import me.bewf.luceat.client.gui.presets.HardwareHint;
import me.bewf.luceat.client.light.Governor;
import me.bewf.luceat.client.light.LightGrid;
import me.bewf.luceat.client.light.LightSpread;
import me.bewf.luceat.client.light.Panic;
import me.bewf.luceat.client.light.PlacedLights;
import me.bewf.luceat.client.light.Stats;
import me.bewf.luceat.client.shader.ShaderVariant;
import me.bewf.luceat.client.shader.TerrainShaderPatch;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowVolume;
import me.bewf.luceat.client.shadow.ShapeMasks;

import net.fabricmc.loader.api.FabricLoader;

public final class DebugReport {
	public record Row(String label, String value, int colour) {
	}

	public record Group(String title, List<Row> rows) {
	}

	private DebugReport() {
	}

	private static String modVersion(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	public static boolean doubleLighting() {
		return FabricLoader.getInstance().isModLoaded("lambdynlights");
	}

	public static List<Group> left() {
		List<Group> groups = new ArrayList<>();
		boolean compute = LightSpread.supported();
		List<Row> graphics = new ArrayList<>();
		graphics.add(new Row("GPU", HardwareHint.gpuName(), Glass.TEXT));
		graphics.add(new Row("OpenGL", HardwareHint.glVersion(), Glass.TEXT));
		graphics.add(new Row("Renderer", RenderBackend.kind().label(), Glass.TEXT));
		graphics.add(new Row("Light around corners", compute ? "available" : "needs OpenGL 4.3 or the compute extension", compute ? Glass.GOOD : Glass.WARN));
		groups.add(new Group("Graphics", graphics));

		ShaderVariant built = TerrainShaderPatch.built();
		ShaderVariant wanted = ShaderVariant.wanted();
		List<Row> shader = new ArrayList<>();
		shader.add(new Row("Built", built == null ? "not built yet" : built.name().toLowerCase(Locale.ROOT), Glass.TEXT));
		shader.add(new Row("Wanted", wanted.name().toLowerCase(Locale.ROOT), built == wanted ? Glass.TEXT : Glass.WARN));
		shader.add(new Row("Shader pack", IrisCompat.installed() ? IrisCompat.packOn() ? "on" : "installed, none active" : "Iris not installed", IrisCompat.standDown() ? Glass.WARN : Glass.TEXT));
		groups.add(new Group("Shader", shader));

		List<Row> mods = new ArrayList<>();
		String luceat = modVersion("luceat");
		mods.add(new Row("Luceat", luceat == null ? "unknown" : luceat, Glass.TEXT));
		String sodium = modVersion("sodium");
		mods.add(new Row("Sodium", sodium == null ? "missing" : sodium, sodium == null ? Glass.BAD : Glass.TEXT));
		String iris = modVersion("iris");
		mods.add(new Row("Iris", iris == null ? "not installed" : iris, Glass.TEXT));
		String lamb = modVersion("lambdynlights");
		mods.add(new Row("LambDynamicLights", lamb == null ? "not installed" : lamb + ", lights will double up", lamb == null ? Glass.TEXT : Glass.WARN));
		groups.add(new Group("Mods", mods));
		return groups;
	}

	public static List<Group> right() {
		LuceatConfig config = LuceatConfig.get();
		List<Group> groups = new ArrayList<>();
		List<Row> lights = new ArrayList<>();
		lights.add(new Row("Lights this frame", Integer.toString(Stats.lights()), Glass.TEXT));
		lights.add(new Row("Light grid cells", Integer.toString(LightGrid.cellsUsed()), Glass.TEXT));
		lights.add(new Row("Scanned sections", Integer.toString(PlacedLights.sectionCount()), Glass.TEXT));
		lights.add(new Row("Shadow map cells", config.shadows ? Integer.toString(ShadowVolume.cellsFilled()) : "shadows off", Glass.TEXT));
		long bytes = ShadowVolume.bytes() + PatternAtlas.bytes() + ShapeMasks.bytes() + LightGrid.bytes();
		lights.add(new Row("Video memory", String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0), Glass.TEXT));
		groups.add(new Group("Lights", lights));

		List<Row> frame = new ArrayList<>();
		frame.add(new Row("Frame time", Math.round(Stats.frameMillis()) + " ms", Glass.TEXT));
		frame.add(new Row("Adaptive quality", Math.round(Governor.quality() * 100.0F) + "%", Governor.quality() < 0.99F ? Glass.WARN : Glass.TEXT));
		frame.add(new Row("Lights paused", Panic.paused() ? "yes, /luceat resume" : "no", Panic.paused() ? Glass.BAD : Glass.TEXT));
		for (int i = 0; i < Stats.NAMES.length; i++) {
			frame.add(new Row(capital(Stats.NAMES[i]), String.format(Locale.ROOT, "%.2f ms", Stats.stage(i)), Glass.TEXT));
		}
		groups.add(new Group("Per frame", frame));
		return groups;
	}

	private static String capital(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	public static String text() {
		StringBuilder out = new StringBuilder("Luceat debug report\n");
		List<Group> all = new ArrayList<>(left());
		all.addAll(right());
		for (Group group : all) {
			out.append('\n').append(group.title()).append('\n');
			for (Row row : group.rows()) {
				out.append(row.label()).append(": ").append(row.value()).append('\n');
			}
		}
		LuceatConfig config = LuceatConfig.get();
		out.append("\nSettings\npreset: ").append(config.preset).append('\n');
		out.append("shadows: ").append(config.shadows).append(", lights with shadows: ").append(config.shadowedLights).append('\n');
		out.append("max lights: ").append(config.maxLights).append(", max brightness: ").append(config.maxBrightness).append('\n');
		return out.toString();
	}
}
