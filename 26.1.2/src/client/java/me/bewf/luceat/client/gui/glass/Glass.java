package me.bewf.luceat.client.gui.glass;

import static me.bewf.luceat.client.gui.glass.Paint.alpha;
import static me.bewf.luceat.client.gui.glass.Paint.argb;
import static me.bewf.luceat.client.gui.glass.Paint.mix;

public final class Glass {
	public static final int INK = 0xFF0B0713;
	public static final int TEXT = 0xFFF1EEFA;
	public static final int SOFT = 0xFFBDB6D6;
	public static final int MUTED = 0xFF8A84A6;
	public static final int FAINT = 0xFF5E5878;
	public static final int VIOLET = 0xFF7B63FF;
	public static final int LAVENDER = 0xFFB28CFF;
	public static final int PALE = 0xFFE4D6FF;
	public static final int EDGE = 0xFFCDBBFF;
	public static final int WARN = 0xFFFFC857;
	public static final int GOOD = 0xFF6FE3A0;
	public static final int BAD = 0xFFFF7A8A;

	private Glass() {
	}

	public static void backdrop(Surface s, int w, int h) {
		s.fill(0, 0, w, h, argb(176, 8, 5, 16));
		Paint.vertical(s, 0, 0, w, h / 2, 0, argb(70, 44, 24, 96), argb(0, 44, 24, 96));
		Paint.vertical(s, 0, h / 2, w, h, 0, argb(0, 30, 14, 70), argb(80, 30, 14, 70));
	}

	public static void slab(Surface s, int x0, int y0, int x1, int y1, int r) {
		Paint.halo(s, x0, y0, x1, y1, r, 18, argb(40, 122, 77, 255));
		Paint.vertical(s, x0, y0, x1, y1, r, argb(224, 25, 18, 50), argb(240, 9, 6, 19));
		int w = x1 - x0;
		int h = y1 - y0;
		Paint.horizontal(s, x0 + r, y0 + 1, x0 + w / 2, y0 + 2, argb(0, 255, 255, 255), argb(46, 255, 255, 255), 6);
		Paint.horizontal(s, x0 + w / 2, y0 + 1, x1 - r, y0 + 2, argb(46, 255, 255, 255), argb(0, 255, 255, 255), 6);
		Paint.outline(s, x0 + 2, y0 + 2, x1 - 2, y1 - 2, r - 2, (x, y) -> {
			float fy = (y - y0) / (float) h;
			return argb(Math.round(22 * (1.0F - fy)), 205, 187, 255);
		});
		Paint.outline(s, x0, y0, x1, y1, r, (x, y) -> {
			float fx = (x - x0) / (float) w;
			float fy = (y - y0) / (float) h;
			float lit = 0.62F - 0.5F * (fx * 0.55F + fy * 0.45F);
			float glow = Math.max(0.0F, 1.0F - (float) Math.hypot(fx * 1.4F, (1.0F - fy) * 2.2F)) * 0.9F;
			int base = argb(Math.round(255 * Math.max(0.1F, lit)), 205, 187, 255);
			return mix(base, argb(230, 168, 107, 255), glow);
		});
		for (int k = 0; k < 10; k++) {
			float f = 1.0F - k / 10.0F;
			int peak = Math.round(52 * f * f);
			int xa = x0 + r;
			int xm = xa + 24;
			Paint.horizontal(s, xa, y1 - 2 - k, xm, y1 - 1 - k, argb(0, 166, 107, 255), argb(peak, 166, 107, 255), 5);
			Paint.horizontal(s, xm, y1 - 2 - k, x0 + Math.round(w * 0.42F * f) + 24, y1 - 1 - k, argb(peak, 166, 107, 255), argb(0, 166, 107, 255), 5);
		}
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
			Paint.vertical(s, x0, y0, x1, y1, 9, argb(Math.round(150 * active), 110, 88, 235), argb(Math.round(120 * active), 78, 58, 190));
			Paint.outline(s, x0, y0, x1, y1, 9, (x, y) -> alpha(argb(120, 205, 187, 255), active * (1.0F - 0.5F * (y - y0) / (float) (y1 - y0))));
		}
	}

	public static void button(Surface s, int x0, int y0, int x1, int y1, boolean primary, float hover, float press) {
		int r = Math.min(9, (y1 - y0) / 2);
		if (primary) {
			Paint.halo(s, x0, y0, x1, y1, r, 6, argb(Math.round(26 + 40 * hover), 140, 100, 255));
			Paint.vertical(s, x0, y0 + Math.round(press), x1, y1, r, mix(argb(255, 126, 100, 255), argb(255, 150, 122, 255), hover), mix(argb(255, 88, 66, 210), argb(255, 104, 80, 226), hover));
			Paint.outline(s, x0, y0 + Math.round(press), x1, y1, r, (x, y) -> y < (y0 + y1) / 2 ? argb(150, 235, 225, 255) : argb(60, 235, 225, 255));
		} else {
			Paint.round(s, x0, y0, x1, y1, r, argb(Math.round(50 + 40 * hover), 40, 30, 80));
			Paint.outline(s, x0, y0, x1, y1, r, argb(Math.round(60 + 70 * hover), 205, 187, 255));
		}
	}

	public static void toggle(Surface s, int x, int y, float on, float hover) {
		int w = 30;
		int h = 16;
		if (on > 0.01F) {
			Paint.halo(s, x, y, x + w, y + h, 8, 7, argb(Math.round(70 * on), 140, 100, 255));
		}
		Paint.vertical(s, x, y, x + w, y + h, 8, mix(argb(255, 44, 36, 70), argb(255, 130, 106, 255), on), mix(argb(255, 34, 28, 56), argb(255, 96, 72, 226), on));
		Paint.outline(s, x, y, x + w, y + h, 8, mix(argb(60, 205, 187, 255), argb(120, 235, 225, 255), Math.max(on, hover * 0.6F)));
		int kx = x + 3 + Math.round(14 * on);
		Paint.round(s, kx, y + 3, kx + 10, y + 13, 5, mix(argb(255, 150, 142, 176), argb(255, 250, 246, 255), on));
	}

	public static void slider(Surface s, int x0, int cy, int x1, float value, float hover, float drag) {
		float v = Math.max(0.0F, Math.min(1.0F, value));
		int kx = x0 + Math.round(v * (x1 - x0));
		Paint.round(s, x0, cy - 2, x1, cy + 2, 2, argb(255, 38, 31, 62));
		Paint.outline(s, x0, cy - 2, x1, cy + 2, 2, argb(30, 0, 0, 0));
		float glow = (0.12F + 0.78F * v) * (1.0F + 0.25F * Math.max(hover, drag));
		if (kx > x0 + 2) {
			Paint.halo(s, x0, cy - 2, kx, cy + 2, 2, 6, argb(Math.round(Math.min(1.0F, glow) * 70), 150, 110, 255));
			int tipColour = mix(argb(255, 150, 120, 255), argb(255, 226, 208, 255), v);
			Paint.rows(s, x0, cy - 2, kx, cy + 2, 2, y -> y < cy ? tipColour : mix(tipColour, argb(255, 92, 70, 220), 0.5F), false);
			Paint.horizontal(s, x0 + 1, cy - 1, kx, cy, argb(255, 108, 92, 255), tipColour, 3);
		}
		float ring = 0.25F + 0.75F * v;
		Paint.halo(s, kx - 5, cy - 5, kx + 5, cy + 5, 5, 9, argb(Math.round(Math.min(1.0F, ring * (1.0F + 0.4F * drag)) * 96), 170, 132, 255));
		Paint.round(s, kx - 6, cy - 6, kx + 6, cy + 6, 6, argb(255, 236, 228, 255));
		Paint.round(s, kx - 3, cy - 3, kx + 3, cy + 3, 3, mix(argb(255, 124, 100, 255), argb(255, 190, 150, 255), v));
	}

	public static void chip(Surface s, int x0, int y0, int x1, int y1, float hover) {
		int r = (y1 - y0) / 2;
		Paint.round(s, x0, y0, x1, y1, r, argb(Math.round(130 + 40 * hover), 14, 10, 28));
		Paint.outline(s, x0, y0, x1, y1, r, argb(Math.round(46 + 50 * hover), 205, 187, 255));
	}

	public static void field(Surface s, int x0, int y0, int x1, int y1, float focus) {
		inset(s, x0, y0, x1, y1, 8);
		if (focus > 0.01F) {
			Paint.outline(s, x0, y0, x1, y1, 8, argb(Math.round(140 * focus), 160, 124, 255));
			Paint.halo(s, x0, y0, x1, y1, 8, 4, argb(Math.round(40 * focus), 140, 100, 255));
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
}
