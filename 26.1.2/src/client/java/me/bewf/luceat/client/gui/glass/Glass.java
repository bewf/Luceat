package me.bewf.luceat.client.gui.glass;

import static me.bewf.luceat.client.gui.glass.Paint.alpha;
import static me.bewf.luceat.client.gui.glass.Paint.argb;
import static me.bewf.luceat.client.gui.glass.Paint.mix;

public final class Glass {
	public static final int INK = 0xFF0B0713;
	public static final int TEXT = 0xFFF1EEFA;
	public static final int SOFT = 0xFFBDB6D6;
	public static final int MUTED = 0xFF8A84A6;
	public static final int FAINT = 0xFF8A84A6;
	public static final int VIOLET = 0xFF7B63FF;
	public static final int LAVENDER = 0xFFB28CFF;
	public static final int PALE = 0xFFE4D6FF;
	public static final int EDGE = 0xFFCDBBFF;
	public static final int WARN = 0xFFF4EAD2;
	public static final int CREAM = 0xFFF4EAD2;
	public static final int MAGENTA = 0xFFBE3FFF;
	public static final int GOOD = 0xFF6FE3A0;
	public static final int BAD = 0xFFFF7A8A;

	public static boolean frosted;

	private Glass() {
	}

	public static void backdrop(Surface s, int w, int h) {
		s.fill(0, 0, w, h, argb(frosted ? 34 : 120, 8, 5, 20));
		Paint.vertical(s, 0, 0, w, h / 2, 0, argb(frosted ? 70 : 60, 92, 56, 200), argb(0, 92, 56, 200));
		Paint.vertical(s, 0, h / 2, w, h, 0, argb(0, 60, 30, 150), argb(frosted ? 80 : 70, 60, 30, 150));
	}

	private static float lit(float x, float y, int x0, int y0, int x1, int y1) {
		float u = (x - x0) / Math.max(1.0F, x1 - x0);
		float v = (y - y0) / Math.max(1.0F, y1 - y0);
		return Math.max(0.0F, Math.min(1.0F, 1.08F - 0.62F * u - 0.5F * v));
	}

	public static void slab(Surface s, int x0, int y0, int x1, int y1, int r) {
		Paint.spread(s, x0, y0 + 6, x1, y1 + 6, r, r, 30, 2.2F, (x, y) -> argb(Math.round(84 * (0.55F + 0.45F * (1.0F - lit(x, y, x0, y0, x1, y1)))), 6, 3, 18));
		Paint.vertical(s, x0, y0, x1, y1, r, argb(frosted ? 104 : 206, 98, 74, 196), argb(frosted ? 96 : 214, 38, 26, 92));
		Paint.vertical(s, x0 + 1, y0 + 1, x1 - 1, y0 + (y1 - y0) / 3, r - 1, argb(frosted ? 22 : 16, 255, 255, 255), argb(0, 255, 255, 255));
		Paint.bloom(s, x0 + 1, y0 + 1, x1 - 1, y1 - 1, r - 1, r - 1, 34, 2.6F, (x, y) -> argb(Math.round(70 * (0.25F + 0.75F * lit(x, y, x0, y0, x1, y1))), 222, 208, 255));
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> argb(Math.round(255 * (0.12F + 0.56F * lit(x, y, x0, y0, x1, y1))), 222, 208, 255));
	}

	public static void panel(Surface s, int x0, int y0, int x1, int y1, int r) {
		Paint.vertical(s, x0, y0, x1, y1, r, argb(frosted ? 30 : 40, 255, 255, 255), argb(frosted ? 16 : 26, 255, 255, 255));
		Paint.bloom(s, x0 + 1, y0 + 1, x1 - 1, y1 - 1, r - 1, r - 1, 14, 2.4F, (x, y) -> argb(Math.round(38 * (0.2F + 0.8F * lit(x, y, x0, y0, x1, y1))), 230, 216, 255));
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> argb(Math.round(255 * (0.08F + 0.34F * lit(x, y, x0, y0, x1, y1))), 222, 208, 255));
	}

	public static void tabTop(Surface s, int x0, int y0, int x1, int y1, int r, float a) {
		Paint.verticalCorners(s, x0, y0, x1, y1, 0, r, argb(Math.round(30 * a), 255, 255, 255), argb(Math.round(10 * a), 255, 255, 255));
		Paint.bloom(s, x0, y0, x1, y1, 0, r, 30, 1.6F, (x, y) -> argb(Math.round(84 * a), 232, 220, 255));
		Paint.outline(s, x0, y0, x1, y1, 0, r, (x, y) -> y < y0 + 1.5F ? 0 : argb(Math.round(a * 255 * (0.1F + 0.36F * lit(x, y, x0, y0, x1, y1))), 226, 212, 255));
	}

	public static void tabBottom(Surface s, int x0, int y0, int x1, int y1, int r, float a) {
		Paint.verticalCorners(s, x0, y0, x1, y1, r, 0, argb(Math.round(10 * a), 255, 255, 255), argb(Math.round(30 * a), 255, 255, 255));
		Paint.bloom(s, x0, y0, x1, y1, r, 0, 30, 1.6F, (x, y) -> argb(Math.round(84 * a), 232, 220, 255));
		Paint.outline(s, x0, y0, x1, y1, r, 0, (x, y) -> y > y1 - 1.5F ? 0 : argb(Math.round(a * 255 * (0.1F + 0.36F * lit(x, y, x0, y0, x1, y1))), 226, 212, 255));
	}

	public static void inset(Surface s, int x0, int y0, int x1, int y1, int r) {
		Paint.round(s, x0, y0, x1, y1, r, argb(110, 5, 3, 12));
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> y < (y0 + y1) / 2 ? argb(80, 0, 0, 0) : argb(26, 205, 187, 255));
	}

	public static void solid(Surface s, int x0, int y0, int x1, int y1, int r) {
		Paint.vertical(s, x0, y0, x1, y1, r, argb(255, 25, 19, 42), argb(255, 17, 12, 30));
		Paint.outline(s, x0, y0, x1, y1, r, argb(70, 205, 187, 255));
	}

	public static void divider(Surface s, int x0, int x1, int y) {
		int mid = (x0 + x1) / 2;
		Paint.horizontal(s, x0, y, mid, y + 1, argb(0, 205, 187, 255), argb(34, 205, 187, 255), 4);
		Paint.horizontal(s, mid, y, x1, y + 1, argb(34, 205, 187, 255), argb(0, 205, 187, 255), 4);
	}

	public static void navItem(Surface s, int x0, int y0, int x1, int y1, float active, float hover) {
		if (hover > 0.01F && active < 0.99F) {
			Paint.round(s, x0, y0, x1, y1, 9, argb(Math.round(26 * hover * (1.0F - active)), 205, 187, 255));
		}
		if (active > 0.01F) {
			Paint.vertical(s, x0, y0, x1, y1, 9, argb(Math.round(170 * active), 190, 63, 255), argb(Math.round(130 * active), 128, 40, 214));
			Paint.outline(s, x0, y0, x1, y1, 9, (x, y) -> alpha(argb(120, 205, 187, 255), active * (1.0F - 0.5F * (y - y0) / (float) (y1 - y0))));
		}
	}

	public static void button(Surface s, int x0, int y0, int x1, int y1, boolean primary, float hover, float press) {
		int r = Math.min(8, (y1 - y0) / 2);
		if (primary) {
			Paint.vertical(s, x0, y0 + Math.round(press), x1, y1, r, mix(argb(255, 248, 241, 222), argb(255, 255, 250, 238), hover), mix(argb(255, 226, 214, 186), argb(255, 238, 228, 200), hover));
			Paint.outline(s, x0, y0 + Math.round(press), x1, y1, r, (x, y) -> y < (y0 + y1) / 2 ? argb(220, 255, 255, 255) : argb(90, 255, 244, 214));
		} else {
			Paint.round(s, x0, y0, x1, y1, r, argb(Math.round(50 + 40 * hover), 40, 30, 80));
			Paint.outline(s, x0, y0, x1, y1, r, argb(Math.round(60 + 70 * hover), 205, 187, 255));
		}
	}

	public static void toggle(Surface s, int x, int y, float on, float hover) {
		int w = 30;
		int h = 16;
		Paint.vertical(s, x, y, x + w, y + h, 8, mix(argb(255, 44, 36, 70), argb(255, 204, 92, 255), on), mix(argb(255, 34, 28, 56), argb(255, 160, 52, 232), on));
		Paint.outline(s, x, y, x + w, y + h, 8, mix(argb(60, 205, 187, 255), argb(120, 235, 225, 255), Math.max(on, hover * 0.6F)));
		int kx = x + 3 + Math.round(14 * on);
		Paint.round(s, kx, y + 3, kx + 10, y + 13, 5, mix(argb(255, 150, 142, 176), argb(255, 250, 246, 255), on));
	}

	public static void slider(Surface s, int x0, int cy, int x1, float value, float hover, float drag) {
		float v = Math.max(0.0F, Math.min(1.0F, value));
		int kx = x0 + Math.round(v * (x1 - x0));
		Paint.round(s, x0, cy - 2, x1, cy + 2, 2, argb(255, 38, 31, 62));
		Paint.outline(s, x0, cy - 2, x1, cy + 2, 2, argb(30, 0, 0, 0));
		if (kx > x0 + 2) {
			int tipColour = mix(argb(255, 190, 63, 255), argb(255, 236, 214, 255), v);
			Paint.rows(s, x0, cy - 2, kx, cy + 2, 2, y -> y < cy ? tipColour : mix(tipColour, argb(255, 92, 70, 220), 0.5F), false);
			Paint.horizontal(s, x0 + 1, cy - 1, kx, cy, argb(255, 160, 60, 240), tipColour, 3);
		}
		Paint.round(s, kx - 6, cy - 6, kx + 6, cy + 6, 6, argb(255, 236, 228, 255));
		Paint.round(s, kx - 3, cy - 3, kx + 3, cy + 3, 3, mix(argb(255, 150, 70, 255), argb(255, 226, 150, 255), v));
	}

	public static void chip(Surface s, int x0, int y0, int x1, int y1, float hover) {
		pane(s, x0, y0, x1, y1, Math.min(8, (y1 - y0) / 2), hover, 0.0F);
	}

	public static void tool(Surface s, int x0, int y0, int x1, int y1, float hover) {
		pane(s, x0, y0, x1, y1, Math.min(8, (y1 - y0) / 2), hover, 0.0F);
	}

	public static void pane(Surface s, int x0, int y0, int x1, int y1, int r, float hover, float press) {
		Paint.vertical(s, x0, y0, x1, y1, r, argb(Math.round(40 + 30 * hover), 226, 214, 255), argb(Math.round(12 + 20 * hover), 150, 120, 255));
		Paint.bloom(s, x0 + 1, y0 + 1, x1 - 1, y1 - 1, r - 1, r - 1, Math.min(7, (y1 - y0) / 3), 2.2F, (x, y) -> argb(Math.round((34 + 26 * hover) * (0.3F + 0.7F * lit(x, y, x0, y0, x1, y1))), 236, 226, 255));
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> argb(Math.round(255 * Math.max(0.1F, Math.min(1.0F, 0.12F + 0.5F * lit(x, y, x0, y0, x1, y1) + 0.25F * hover))), 226, 214, 255));
	}

	public static void card(Surface s, int x0, int y0, int x1, int y1, int r) {
		Paint.vertical(s, x0, y0, x1, y1, r, argb(26, 255, 255, 255), argb(12, 255, 255, 255));
		Paint.horizontal(s, x0 + r, y0 + 1, x1 - r, y0 + 2, argb(0, 255, 255, 255), argb(34, 255, 255, 255), 8);
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> argb(y < (y0 + y1) / 2 ? 40 : 22, 235, 228, 255));
	}

	public static void field(Surface s, int x0, int y0, int x1, int y1, float focus) {
		int r = Math.min(8, (y1 - y0) / 2);
		Paint.vertical(s, x0, y0, x1, y1, r, argb(150, 3, 2, 9), argb(96, 8, 5, 20));
		Paint.horizontal(s, x0 + r, y0 + 1, x1 - r, y0 + 3, argb(40, 0, 0, 0), argb(40, 0, 0, 0), 8);
		int w = x1 - x0;
		int h = y1 - y0;
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> {
			float fy = (y - y0) / (float) h;
			float fx = (x - x0) / (float) w;
			return argb(Math.round(fy < 0.5F ? 90 - 60 * fy : 18 + 62 * fy * (1.0F - 0.4F * fx)), fy < 0.5F ? 0 : 205, fy < 0.5F ? 0 : 187, fy < 0.5F ? 0 : 255);
		});
		if (focus > 0.01F) {
			Paint.outline(s, x0 - 1, y0 - 1, x1 + 1, y1 + 1, r + 1, argb(Math.round(70 * focus), 160, 124, 255));
			Paint.outline(s, x0, y0, x1, y1, r, argb(Math.round(170 * focus), 170, 136, 255));
		}
	}

	public static void text(Surface s, Surface.Role role, String text, int x, int y, int colour) {
		s.text(role, text, x, y, colour);
	}

	public static void centred(Surface s, Surface.Role role, String text, int cx, int y, int colour) {
		s.text(role, text, cx - s.width(role, text) / 2, y, colour);
	}

	public static void right(Surface s, Surface.Role role, String text, int rx, int y, int colour) {
		s.text(role, text, rx - s.width(role, text), y, colour);
	}

	public static java.util.List<String> wrap(Surface s, Surface.Role role, String text, int width, int maxLines) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		String[] words = text.split(" ");
		for (int i = 0; i < words.length; i++) {
			String trial = line.length() == 0 ? words[i] : line + " " + words[i];
			if (s.width(role, trial) > width && line.length() > 0) {
				lines.add(line.toString());
				line = new StringBuilder(words[i]);
				if (lines.size() == maxLines) {
					break;
				}
			} else {
				line = new StringBuilder(trial);
			}
		}
		if (lines.size() < maxLines && line.length() > 0) {
			lines.add(line.toString());
		} else if (lines.size() == maxLines && line.length() > 0 && maxLines > 0) {
			String last = lines.get(maxLines - 1);
			if (!last.endsWith("...")) {
				lines.set(maxLines - 1, fit(s, role, last + "...", width));
			}
		}
		return lines;
	}

	public static String fit(Surface s, Surface.Role role, String text, int width) {
		if (s.width(role, text) <= width) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && s.width(role, cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}
}
