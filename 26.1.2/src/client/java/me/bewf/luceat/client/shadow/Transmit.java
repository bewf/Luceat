package me.bewf.luceat.client.shadow;

public final class Transmit {
	private static final float[] TABLE = new float[0x10000 * 3];

	static {
		java.util.Arrays.fill(TABLE, 0, 3, 1.0F);
	}

	private Transmit() {
	}

	public static void set(int id, float red, float green, float blue) {
		int at = (id & 0xFFFF) * 3;
		TABLE[at] = red;
		TABLE[at + 1] = green;
		TABLE[at + 2] = blue;
	}

	public static float red(int id) {
		return TABLE[(id & 0xFFFF) * 3];
	}

	public static float green(int id) {
		return TABLE[(id & 0xFFFF) * 3 + 1];
	}

	public static float blue(int id) {
		return TABLE[(id & 0xFFFF) * 3 + 2];
	}
}
