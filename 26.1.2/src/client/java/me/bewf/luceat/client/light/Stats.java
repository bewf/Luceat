package me.bewf.luceat.client.light;

public final class Stats {
	public static final int GATHER = 0;
	public static final int VOLUME = 1;
	public static final int PLACED = 2;
	public static final int GRID = 3;
	public static final int BUFFER = 4;
	public static final int SPREAD = 5;
	public static final String[] NAMES = {"collecting lights", "shadow block map", "placed lights", "light grid", "light buffer", "light spreading"};

	private static final float[] millis = new float[NAMES.length];
	private static float frame;
	private static int lights;

	private Stats() {
	}

	public static void record(int stage, long nanos) {
		float ms = nanos / 1.0E6F;
		millis[stage] += (ms - millis[stage]) * 0.1F;
	}

	public static void frame(float ms, int lightCount) {
		frame += (ms - frame) * 0.1F;
		lights = lightCount;
	}

	public static float stage(int stage) {
		return millis[stage];
	}

	public static float frameMillis() {
		return frame;
	}

	public static int lights() {
		return lights;
	}
}
