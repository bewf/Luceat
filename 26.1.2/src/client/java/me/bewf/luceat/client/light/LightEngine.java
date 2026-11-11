package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.List;

import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShaderPackMode;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowCache;
import me.bewf.luceat.client.shadow.ShadowVolume;
import me.bewf.luceat.client.shadow.ShapeMasks;
import me.bewf.luceat.client.shadow.SunCache;
import me.bewf.luceat.client.shader.ShaderVariant;
import me.bewf.luceat.client.shader.TerrainShaderPatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class LightEngine {
	private static final LightBuffer BUFFER = new LightBuffer();

	private static List<LightSource> lights = List.of();
	private static long lastTime = Long.MIN_VALUE;
	private static float lastPartial = -1.0F;
	private static double lastX;
	private static double lastY;
	private static double lastZ;
	private static ClientLevel lastLevel;
	private static LuceatConfig lastConfig;
	private static int lastRevision = -1;
	private static boolean built;

	private LightEngine() {
	}

	public static void upload(int program) {
		if (!RenderBackend.terrainPathAvailable() || TerrainShaderPatch.built() == ShaderVariant.OFF) {
			return;
		}
		try {
			uploadNow(program);
		} catch (Throwable error) {
			RenderBackend.glFailed(error);
		}
	}

	private static void uploadNow(int program) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		LuceatConfig config = LuceatConfig.get();
		ClientLevel level = minecraft.level;
		long time = level == null ? Long.MIN_VALUE : level.getGameTime();
		boolean fresh = config != lastConfig || LuceatConfig.revision() != lastRevision || level != lastLevel || time != lastTime || partialTick != lastPartial || camera.x != lastX || camera.y != lastY || camera.z != lastZ;
		if (fresh) {
			built = false;
			FrameClock.frame();
			Panic.frame(FrameClock.millis(), lights.size());
			Stats.frame(FrameClock.millis(), lights.size());
			Governor.frame(minecraft, config);
			lastConfig = config;
			lastRevision = LuceatConfig.revision();
			lastLevel = level;
			lastTime = time;
			lastPartial = partialTick;
			lastX = camera.x;
			lastY = camera.y;
			lastZ = camera.z;
			long gatherStart = System.nanoTime();
			lights = config.enabled && !Panic.paused() ? gather(minecraft, camera, partialTick, config) : List.of();
			Stats.record(Stats.GATHER, System.nanoTime() - gatherStart);
			if (!config.enabled) {
				LightSmoother.clear();
			}
		}
		if (!built) {
			long gridStart = System.nanoTime();
			LightGrid.update(lights, camera);
			Stats.record(Stats.GRID, System.nanoTime() - gridStart);
			ShadowCache.update(lights, camera);
			SunCache.update(minecraft.level, camera);
			long bufferStart = System.nanoTime();
			BUFFER.update(lights, camera);
			Stats.record(Stats.BUFFER, System.nanoTime() - bufferStart);
			built = true;
			ShaderVariant variant = TerrainShaderPatch.built();
			if (variant != null && variant.spread()) {
				long spreadStart = System.nanoTime();
				LightSpread.update(BUFFER, lights, camera);
				Stats.record(Stats.SPREAD, System.nanoTime() - spreadStart);
			}
		}
		BUFFER.bind(program);
		LightGrid.bind(program);
		ShadowVolume.bind(program);
		ShadowCache.bind(program);
		SunCache.bind(program);
		ShaderVariant bound = TerrainShaderPatch.built();
		if (bound != null && bound.spread()) {
			LightSpread.bind(program);
		}
		PatternAtlas.bind(program);
		ShapeMasks.bind(program);
	}

	public static void tickCompatible(Minecraft minecraft) {
		LuceatConfig config = LuceatConfig.get();
		if (minecraft.level == null || !config.enabled || config.shaderPacks != ShaderPackMode.COMPATIBLE || !IrisCompat.packOn()) {
			return;
		}
		try {
			Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
			float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			lights = gather(minecraft, camera, partialTick, config);
			LightGrid.update(lights, camera);
		} catch (RuntimeException ignored) {
		}
	}

	private static List<LightSource> gather(Minecraft minecraft, Vec3 camera, float partialTick, LuceatConfig config) {
		LightCull.setup(minecraft, camera, partialTick);
		List<LightSource> found = new ArrayList<>(EntityLights.collect(minecraft, camera, partialTick));
		found.removeIf(light -> !LightCull.sees(light.x(), light.y(), light.z(), light.radius()));
		found.addAll(TestLoad.lights(camera));
		ShaderVariant built = TerrainShaderPatch.built();
		if (built != null && built.usesVolume()) {
			long volumeStart = System.nanoTime();
			ShadowVolume.update(minecraft.level, camera);
			Stats.record(Stats.VOLUME, System.nanoTime() - volumeStart);
		}
		if (config.placedLights) {
			long placedStart = System.nanoTime();
			PlacedLights.update(minecraft.level, camera);
			PlacedLights.collect(camera, Math.min(config.maxLights, LightBuffer.MAX_LIGHTS) - found.size(), found);
			Stats.record(Stats.PLACED, System.nanoTime() - placedStart);
		}
		return LightSmoother.apply(found);
	}
}
