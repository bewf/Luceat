package me.bewf.luceat.client.light;

public final class Flame {
	private Flame() {
	}

	public static float noise(float seconds, float seed) {
		return 0.6F * wobble(seconds * 6.0F + seed) + 0.4F * wobble(seconds * 13.0F + seed * 1.7F + 9.0F);
	}

	private static float wobble(float x) {
		float base = (float) Math.floor(x);
		float f = x - base;
		f = f * f * (3.0F - 2.0F * f);
		float a = hash(base);
		float b = hash(base + 1.0F);
		return (a + (b - a) * f) * 2.0F - 1.0F;
	}

	private static float hash(float i) {
		float s = (float) Math.sin(i * 127.1F + 311.7F) * 43758.5453F;
		return s - (float) Math.floor(s);
	}
}
