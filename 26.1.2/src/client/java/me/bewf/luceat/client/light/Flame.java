package me.bewf.luceat.client.light;

/**
 * Random stepped flame flicker. The same function is implemented in
 * TerrainShaderPatch (luceatFlame) for baked lights, so both must stay identical.
 */
public final class Flame {
	private static final float HOLD_RAMP = 0.3F;

	private Flame() {
	}

	public static float noise(float seconds, int seed) {
		return 0.6F * layer(seconds, 3.3F, seed) + 0.4F * layer(seconds, 7.9F, seed * 31 + 7);
	}

	public static int seedByte(long key) {
		int top = (int) ((key * 0x9E3779B97F4A7C15L) >>> 56);
		return top == 0 ? 1 : top;
	}

	private static float layer(float seconds, float rate, int seed) {
		float x = seconds * rate;
		int slot = (int) Math.floor(x);
		float f = x - slot;
		float ramp = Math.min(f / HOLD_RAMP, 1.0F);
		ramp = ramp * ramp * (3.0F - 2.0F * ramp);
		float from = slotValue(slot, seed);
		float to = slotValue(slot + 1, seed);
		return from + (to - from) * ramp;
	}

	private static float slotValue(int slot, int seed) {
		int x = slot * 0x9E3779B1 ^ seed * 0x85EBCA6B;
		x ^= x >>> 16;
		x *= 0x7FEB352D;
		x ^= x >>> 15;
		x *= 0x846CA68B;
		x ^= x >>> 16;
		float n = (x & 0xFFFFFFFFL) / 4294967295.0F * 2.0F - 1.0F;
		return Math.signum(n) * (float) Math.pow(Math.abs(n), 1.5);
	}
}
