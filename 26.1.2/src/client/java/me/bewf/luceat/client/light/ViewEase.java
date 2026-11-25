package me.bewf.luceat.client.light;

import net.minecraft.world.phys.Vec3;

public final class ViewEase {
	private static final float FALL_SECONDS = 0.08F;
	private static final float RISE_SECONDS = 0.6F;
	private static final float TELEPORT = 8.0F;

	private static double lastY = Double.NaN;
	private static long lastNanos;
	private static float speed;
	private static float factor = 1.0F;

	private ViewEase() {
	}

	public static float update(Vec3 camera) {
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
		lastNanos = now;
		if (!Double.isNaN(lastY) && dt > 1.0E-4F) {
			float jump = (float) Math.abs(camera.y - lastY);
			if (jump < TELEPORT) {
				float instant = jump / dt;
				speed += (instant - speed) * (1.0F - (float) Math.exp(-dt / 0.06F));
			}
		}
		lastY = camera.y;
		float t = Math.max(0.0F, Math.min(1.0F, (speed - 0.6F) / 3.4F));
		float target = 1.0F - 0.9F * t * t * (3.0F - 2.0F * t);
		float seconds = target < factor ? FALL_SECONDS : RISE_SECONDS;
		factor += (target - factor) * (1.0F - (float) Math.exp(-dt / seconds));
		return factor;
	}

	public static void reset() {
		lastY = Double.NaN;
		lastNanos = 0L;
		speed = 0.0F;
		factor = 1.0F;
	}
}
