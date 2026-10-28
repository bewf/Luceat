package me.bewf.luceat.client.light;

public final class FrameClock {
	private static long last;
	private static float millis;
	private static int count;

	private FrameClock() {
	}

	public static void frame() {
		long now = System.nanoTime();
		if (last != 0L) {
			millis = (now - last) / 1.0E6F;
			count++;
		}
		last = now;
	}

	public static int count() {
		return count;
	}

	public static float millis() {
		return millis;
	}
}
