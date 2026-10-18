package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.List;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowVolume;
import me.bewf.luceat.client.shadow.ShapeMasks;

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

	private LightEngine() {
	}

	public static void upload(int program) {
		if (!RenderBackend.terrainPathAvailable()) {
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
		boolean fresh = level != lastLevel || time != lastTime || partialTick != lastPartial || camera.x != lastX || camera.y != lastY || camera.z != lastZ;
		if (fresh) {
			lastLevel = level;
			lastTime = time;
			lastPartial = partialTick;
			lastX = camera.x;
			lastY = camera.y;
			lastZ = camera.z;
			lights = config.enabled ? gather(minecraft, camera, partialTick, config) : List.of();
			if (!config.enabled) {
				LightSmoother.clear();
			}
		}
		BUFFER.update(lights, camera);
		BUFFER.bind(program);
		ShadowVolume.bind(program);
		PatternAtlas.bind(program);
		ShapeMasks.bind(program);
	}

	private static List<LightSource> gather(Minecraft minecraft, Vec3 camera, float partialTick, LuceatConfig config) {
		List<LightSource> found = new ArrayList<>(EntityLights.collect(minecraft, camera, partialTick));
		ShadowVolume.update(minecraft.level, camera);
		if (config.placedLights) {
			PlacedLights.collect(camera, Math.min(config.maxLights, LightBuffer.MAX_LIGHTS) - found.size(), found);
		}
		return LightSmoother.apply(found);
	}
}
