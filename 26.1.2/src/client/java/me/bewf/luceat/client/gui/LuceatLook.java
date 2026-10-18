package me.bewf.luceat.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class LuceatLook {
	public static final int ACCENT = 0xFFBE3FFF;
	public static final int ACCENT_SOFT = 0x33BE3FFF;
	public static final int ACCENT_DIM = 0xFF7A2A9E;
	public static final int BACKDROP = 0xA0000000;
	public static final int WINDOW = 0xFF16161B;
	public static final int HEADER = 0xFF1D1D24;
	public static final int SIDEBAR = 0xFF191920;
	public static final int CARD = 0xFF22222B;
	public static final int CARD_HOVER = 0xFF2A2A35;
	public static final int BORDER = 0xFF33333F;
	public static final int TEXT = 0xFFEDEDF3;
	public static final int SOFT = 0xFFA2A2B2;
	public static final int MUTED = 0xFF6C6C7C;
	public static final int GOOD = 0xFF5BE38A;
	public static final int WARN = 0xFFFFC857;
	public static final int ORANGE = 0xFFFF9A4D;
	public static final int BAD = 0xFFFF6B6B;
	public static final int TRACK = 0xFF3A3A47;

	private LuceatLook() {
	}

	public static void round(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
		graphics.fill(x0 + 2, y0, x1 - 2, y1, colour);
		graphics.fill(x0, y0 + 2, x1, y1 - 2, colour);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, colour);
	}

	public static void roundOutlined(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int fill, int border) {
		round(graphics, x0, y0, x1, y1, border);
		round(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
	}

	public static int mix(int from, int to, float amount) {
		int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * amount);
		int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * amount);
		int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * amount);
		int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	public static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
		return x >= x0 && x < x1 && y >= y0 && y < y1;
	}
}
