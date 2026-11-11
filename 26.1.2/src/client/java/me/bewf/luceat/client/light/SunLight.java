package me.bewf.luceat.client.light;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

public final class SunLight {
	private SunLight() {
	}

	public static float[] values(ClientLevel level, float partialTick, LuceatConfig config) {
		float[] out = new float[8];
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return out;
		}
		double day = level.getOverworldClockTime() / 24000.0 - 0.25;
		day -= Math.floor(day);
		double eased = 0.5 - Math.cos(day * Math.PI) / 2.0;
		double angle = (day + (eased - day) / 3.0) * 2.0 * Math.PI;
		float x = (float) -Math.sin(angle);
		float y = (float) Math.cos(angle);
		float height = Math.max(0.0F, Math.min(1.0F, (y - 0.05F) / 0.3F));
		float rain = level.getRainLevel(partialTick);
		float power = height * height * (3.0F - 2.0F * height) * (1.0F - 0.85F * rain);
		out[0] = x;
		out[1] = y;
		out[2] = 0.0F;
		out[3] = power;
		out[4] = config.sunShadows ? config.sunShadowStrength : 0.0F;
		out[5] = Math.max(16.0F, Math.min(config.shadowDistance, me.bewf.luceat.client.shadow.SunCache.REACH_LIMIT));
		out[6] = config.caustics ? 1.0F : 0.0F;
		return out;
	}
}
