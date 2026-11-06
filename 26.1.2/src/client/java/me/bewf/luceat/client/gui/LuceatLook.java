package me.bewf.luceat.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class LuceatLook {
	public static final int ACCENT = 0xFFC66BFF;
	public static final int ACCENT_SOFT = 0x33BE3FFF;
	public static final int ACCENT_DIM = 0xFF8A3DD0;
	public static final int BACKDROP = 0xA0000000;
	public static final int WINDOW = 0xF4281E52;
	public static final int HEADER = 0xFF1A1330;
	public static final int SIDEBAR = 0x4A05030C;
	public static final int CARD = 0x20FFFFFF;
	public static final int CARD_HOVER = 0x36FFFFFF;
	public static final int BORDER = 0x44E4D6FF;
	public static final int TEXT = 0xFFF1EEFA;
	public static final int SOFT = 0xFFBDB6D6;
	public static final int MUTED = 0xFF8A84A6;
	public static final int GOOD = 0xFF5BE38A;
	public static final int WARN = 0xFFF4EAD2;
	public static final int ORANGE = 0xFFBE3FFF;
	public static final int BAD = 0xFFFF6B6B;
	public static final int TRACK = 0xFF2D2548;

	private LuceatLook() {
	}

	public static void round(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
		me.bewf.luceat.client.gui.glass.Rounded.draw(graphics, x0, y0, x1, y1, colour, 0);
	}

	public static void roundOutlined(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int fill, int border) {
		me.bewf.luceat.client.gui.glass.Rounded.draw(graphics, x0, y0, x1, y1, fill, border);
	}

	public static void capsule(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
		me.bewf.luceat.client.gui.glass.Rounded.draw(graphics, x0, y0, x1, y1, (y1 - y0) / 2, colour, 0);
	}

	public static void swatch(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int rgb, boolean hot) {
		me.bewf.luceat.client.gui.glass.Rounded.draw(graphics, x0, y0, x1, y1, Math.min(4, (y1 - y0) / 2), 0xFF000000 | rgb, hot ? 0xE6FFFFFF : 0x70FFFFFF);
	}

	public static void tag(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
		int rgb = colour & 0xFFFFFF;
		me.bewf.luceat.client.gui.glass.Rounded.draw(graphics, x0, y0, x1, y1, (y1 - y0) / 2, 0x38000000 | rgb, 0x90000000 | rgb);
	}

	public static int mix(int from, int to, float amount) {
		int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * amount);
		int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * amount);
		int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * amount);
		int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	public static final int ICON_W = 9;
	public static final int ICON_H = 9;

	public static void resetIcon(GuiGraphicsExtractor graphics, int x, int y, int colour) {
		me.bewf.luceat.client.gui.glass.Icons.sprite(graphics, x - 1, y - 1, 11, me.bewf.luceat.client.gui.glass.Icons.RESET, colour);
	}

	public static void drawSwitch(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
		capsule(graphics, x, y, x + 30, y + 14, on ? ACCENT : TRACK);
		int knob = on ? x + 17 : x + 3;
		capsule(graphics, knob, y + 2, knob + 10, y + 12, 0xFFFFFFFF);
	}

	public static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
		return x >= x0 && x < x1 && y >= y0 && y < y1;
	}
}
