package me.bewf.luceat.client.light;

import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.LuceatConfig;

// lets things drawn outside the terrain shader, like particles and mobs, pick up the same lights
public final class LightSampler {
	private LightSampler() {
	}

	public static int particle(double x, double y, double z, int lightCoords) {
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || !config.particleLights || IrisCompat.standDown()) {
			return lightCoords;
		}
		return sample(x, y, z, lightCoords);
	}

	public static int entity(double x, double y, double z, int lightCoords) {
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || !config.entityLights || IrisCompat.standDown()) {
			return lightCoords;
		}
		return sample(x, y, z, lightCoords);
	}

	private static int sample(double x, double y, double z, int lightCoords) {
		float level = LightGrid.levelAt(x, y, z);
		if (level < 1.0F) {
			return lightCoords;
		}
		return HeldLights.brighten(Math.min(15, Math.round(level)), lightCoords);
	}
}
