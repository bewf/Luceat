package me.bewf.luceat.client.shader;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.LightSpread;

public enum ShaderVariant {
	OFF(false, false),
	LEAN(false, false),
	FULL(true, false),
	LEAN_SPREAD(false, true),
	FULL_SPREAD(true, true);

	private final boolean rays;
	private final boolean spread;

	ShaderVariant(boolean rays, boolean spread) {
		this.rays = rays;
		this.spread = spread;
	}

	public boolean rays() {
		return rays;
	}

	public boolean spread() {
		return spread;
	}

	public boolean usesVolume() {
		return rays || spread;
	}

	public static ShaderVariant wanted() {
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || !RenderBackend.terrainPathAvailable()) {
			return OFF;
		}
		boolean wantRays = config.shadows && config.shadowedLights > 0 || config.lightBounce > 0.0F || config.shine > 0.0F || config.sunShadows || config.caustics;
		boolean wantSpread = config.lightSpread && LightSpread.supported();
		if (wantSpread) {
			return wantRays ? FULL_SPREAD : LEAN_SPREAD;
		}
		return wantRays ? FULL : LEAN;
	}
}
