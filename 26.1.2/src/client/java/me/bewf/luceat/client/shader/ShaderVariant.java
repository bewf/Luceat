package me.bewf.luceat.client.shader;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;

public enum ShaderVariant {
	OFF,
	LEAN,
	FULL;

	public static ShaderVariant wanted() {
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || !RenderBackend.terrainPathAvailable()) {
			return OFF;
		}
		boolean rays = config.shadows && config.shadowedLights > 0 || config.lightBounce > 0.0F || config.shine > 0.0F;
		return rays ? FULL : LEAN;
	}
}
