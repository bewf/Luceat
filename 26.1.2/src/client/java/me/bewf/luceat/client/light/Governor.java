package me.bewf.luceat.client.light;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.Minecraft;

// eases the heavy shader work off while the frame rate is low and brings it back when there is room
public final class Governor {
	private static final float FLOOR = 0.35F;
	private static final double SLOW_MS = 24.0;
	private static final double FAST_MS = 16.0;
	private static final double SKIP_MS = 120.0;

	private static float quality = 1.0F;
	private static double smooth = 16.0;
	private static long last;

	private Governor() {
	}

	public static float quality() {
		return quality;
	}

	public static void reset() {
		quality = 1.0F;
		last = 0L;
	}

	// called once per rendered frame
	public static void frame(Minecraft minecraft, LuceatConfig config) {
		long now = System.nanoTime();
		double ms = last == 0L ? 16.0 : (now - last) / 1.0E6;
		last = now;
		if (!config.adaptiveQuality) {
			quality = 1.0F;
			return;
		}
		if (minecraft.screen != null || ms > SKIP_MS) {
			return;
		}
		smooth += (ms - smooth) * 0.04;
		if (smooth > SLOW_MS) {
			quality = Math.max(FLOOR, quality - 0.006F);
		} else if (smooth < FAST_MS) {
			quality = Math.min(1.0F, quality + 0.0015F);
		}
	}
}
