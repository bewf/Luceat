package me.bewf.luceat.client.gui.glass;

import net.minecraft.client.Minecraft;

public final class LuceatBlur {
	private static final int REFERENCE = 5;
	private static final int MAX = 32;
	private static boolean active;

	private LuceatBlur() {
	}

	public static void activate(boolean on) {
		active = on;
	}

	public static int radius(int base) {
		if (!active || base < 1) {
			return base;
		}
		int height;
		try {
			height = Minecraft.getInstance().getWindow().getHeight();
		} catch (RuntimeException error) {
			return base;
		}
		float target = Math.max(14.0F, Math.min(MAX, height * 0.017F));
		int wide = Math.round(target * base / REFERENCE);
		return Math.max(base, Math.min(MAX, wide));
	}
}
