package me.bewf.luceat.client.light;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.Minecraft;

// eases the heavy shader work off while the frame rate is low and brings it back when there is room
public final class Governor {
	private static final double SKIP_MS = 120.0;
	private static final float STEPS = 8.0F;

	private static float quality = 1.0F;
	private static float stepped = 1.0F;
	private static double smooth = 16.0;
	private static long last;

	private Governor() {
	}

	public static float quality() {
		return stepped;
	}

	public static void reset() {
		quality = 1.0F;
		stepped = 1.0F;
		last = 0L;
	}

	// called once per rendered frame
	public static void frame(Minecraft minecraft, LuceatConfig config) {
		long now = System.nanoTime();
		double ms = last == 0L ? 16.0 : (now - last) / 1.0E6;
		last = now;
		if (!config.adaptiveQuality) {
			quality = 1.0F;
			stepped = 1.0F;
			return;
		}
		if (minecraft.screen != null || ms > SKIP_MS) {
			return;
		}
		smooth += (ms - smooth) * 0.04;
		if (smooth > 1000.0 / config.slowBelowFps) {
			quality = Math.max(config.lowestQuality, quality - 0.003F);
		} else if (smooth < 1000.0 / config.fastAboveFps) {
			quality = Math.min(1.0F, quality + 0.0008F);
		}
		float next = Math.round(quality * STEPS) / STEPS;
		if (Math.abs(next - stepped) > 0.001F && Math.abs(quality - stepped) > 0.6F / STEPS) {
			stepped = next;
		}
	}
}
