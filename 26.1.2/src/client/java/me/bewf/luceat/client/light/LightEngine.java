package me.bewf.luceat.client.light;

import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class LightEngine {
	private static final LightBuffer BUFFER = new LightBuffer();

	private LightEngine() {
	}

	public static void upload(int program) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean enabled = LuceatConfig.get().enabled;
		List<LightSource> lights = enabled ? EntityLights.collect(minecraft, camera, partialTick) : List.of();
		if (enabled) {
			ShadowVolume.update(minecraft.level, camera);
			LuceatConfig config = LuceatConfig.get();
			if (config.placedLights) {
				PlacedLights.collect(camera, Math.min(config.maxLights, LightBuffer.MAX_LIGHTS) - lights.size(), lights);
			}
		}
		BUFFER.update(lights, camera);
		BUFFER.bind(program);
		ShadowVolume.bind(program);
		PatternAtlas.bind(program);
	}
}
