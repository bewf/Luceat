package me.bewf.luceat.client.gui;

import me.bewf.luceat.client.gui.glass.Paint;
import me.bewf.luceat.client.gui.glass.Surface;
import me.bewf.luceat.client.sky.CloudMode;
import me.bewf.luceat.client.sky.CloudType;

final class CardArt {
	private CardArt() {
	}

	static void draw(Surface s, Enum<?> value, int x0, int y0, int x1, int y1, float lit) {
		if (value instanceof CloudType type) {
			cloud(s, type, x0, y0, x1, y1, lit);
		} else if (value instanceof CloudMode mode) {
			mode(s, mode, x0, y0, x1, y1, lit);
		}
	}

	private static int dim(int argb, float lit) {
		return Paint.mix(argb, 0xFF000000, 0.28F * (1.0F - lit));
	}

	private static void puff(Surface s, int cx, int cy, int w, int h, int colour) {
		int half = h / 2;
		Paint.round(s, cx - w / 2, cy - half, cx + w / 2, cy + half, half, colour);
		int bump = Math.max(3, Math.round(h * 0.95F));
		Paint.round(s, cx - w / 5 - bump / 2, cy - half - bump / 3, cx - w / 5 + bump / 2, cy - half - bump / 3 + bump, bump / 2, colour);
		int small = Math.round(bump * 0.75F);
		Paint.round(s, cx + w / 6 - small / 2, cy - half - small / 3, cx + w / 6 + small / 2, cy - half - small / 3 + small, small / 2, colour);
	}

	private static void sky(Surface s, int x0, int y0, int x1, int y1, int top, int bottom, float lit) {
		Paint.vertical(s, x0, y0, x1, y1, 6, dim(top, lit), dim(bottom, lit));
	}

	private static void cloud(Surface s, CloudType type, int x0, int y0, int x1, int y1, float lit) {
		int w = x1 - x0;
		int h = y1 - y0;
		int white = dim(0xFFFFFFFF, lit);
		switch (type) {
			case FEW -> {
				sky(s, x0, y0, x1, y1, 0xFF5B96EA, 0xFFAACDF8, lit);
				puff(s, x0 + w * 28 / 100, y0 + h * 62 / 100, w * 24 / 100, h * 16 / 100, white);
				puff(s, x0 + w * 72 / 100, y0 + h * 40 / 100, w * 16 / 100, h * 12 / 100, white);
			}
			case CUMULUS -> {
				sky(s, x0, y0, x1, y1, 0xFF5B96EA, 0xFFAACDF8, lit);
				puff(s, x0 + w * 26 / 100, y0 + h * 66 / 100, w * 34 / 100, h * 22 / 100, white);
				puff(s, x0 + w * 62 / 100, y0 + h * 50 / 100, w * 38 / 100, h * 24 / 100, white);
				puff(s, x0 + w * 84 / 100, y0 + h * 78 / 100, w * 24 / 100, h * 16 / 100, dim(0xFFEAF1FF, lit));
			}
			case STRATUS -> {
				sky(s, x0, y0, x1, y1, 0xFF7FA6DA, 0xFFC6D8EE, lit);
				Paint.round(s, x0 + 3, y0 + h * 30 / 100, x1 - 3, y0 + h * 50 / 100, 4, dim(0xE6F2F5FA, lit));
				Paint.round(s, x0 + w * 12 / 100, y0 + h * 58 / 100, x1 - 3, y0 + h * 76 / 100, 4, dim(0xCCE6ECF4, lit));
			}
			case CIRRUS -> {
				sky(s, x0, y0, x1, y1, 0xFF4F8CE6, 0xFF9CC4F6, lit);
				for (int i = 0; i < 4; i++) {
					int ly = y0 + h * (22 + i * 17) / 100;
					int lx = x0 + w * (10 + (i * 23) % 30) / 100;
					Paint.round(s, lx, ly, lx + w * (42 - i * 4) / 100, ly + 2, 1, dim(0xB3FFFFFF, lit));
				}
			}
			case OVERCAST -> {
				sky(s, x0, y0, x1, y1, 0xFF6E7687, 0xFF9AA1B0, lit);
				Paint.round(s, x0 + 2, y0 + 2, x1 - 2, y0 + h * 60 / 100, 5, dim(0xF0B8BEC9, lit));
				Paint.round(s, x0 + 2, y0 + h * 45 / 100, x1 - 2, y1 - 2, 5, dim(0xF0A4AAB6, lit));
			}
			case STORM -> {
				sky(s, x0, y0, x1, y1, 0xFF2D3145, 0xFF5A5F78, lit);
				int slate = dim(0xFF6A6F86, lit);
				puff(s, x0 + w * 30 / 100, y0 + h * 38 / 100, w * 40 / 100, h * 24 / 100, slate);
				puff(s, x0 + w * 68 / 100, y0 + h * 34 / 100, w * 44 / 100, h * 26 / 100, dim(0xFF565B72, lit));
				int bx = x0 + w * 50 / 100;
				int bolt = dim(0xFFFFE27A, lit);
				Paint.round(s, bx, y0 + h * 52 / 100, bx + 2, y0 + h * 72 / 100, 1, bolt);
				Paint.round(s, bx - 3, y0 + h * 70 / 100, bx + 3, y0 + h * 74 / 100, 1, bolt);
				Paint.round(s, bx - 3, y0 + h * 72 / 100, bx - 1, y0 + h * 90 / 100, 1, bolt);
			}
		}
	}

	private static void mode(Surface s, CloudMode mode, int x0, int y0, int x1, int y1, float lit) {
		int w = x1 - x0;
		int h = y1 - y0;
		int cx = (x0 + x1) / 2;
		int cy = (y0 + y1) / 2;
		switch (mode) {
			case REAL_WORLD -> {
				sky(s, x0, y0, x1, y1, 0xFF1B1F3D, 0xFF2A3262, lit);
				int r = Math.min(w, h) * 40 / 100;
				Paint.round(s, cx - r, cy - r, cx + r, cy + r, r, dim(0xFF3C78D8, lit));
				Paint.round(s, cx - r * 55 / 100, cy - r * 60 / 100, cx - r * 5 / 100, cy - r * 15 / 100, 3, dim(0xFF58B36B, lit));
				Paint.round(s, cx + r * 10 / 100, cy + r * 5 / 100, cx + r * 62 / 100, cy + r * 55 / 100, 3, dim(0xFF58B36B, lit));
				Paint.outline(s, cx - r, cy - r, cx + r, cy + r, r, Paint.argb(Math.round(150 + 80 * lit), 205, 187, 255));
			}
			case IN_GAME -> {
				sky(s, x0, y0, x1, y1, 0xFF5B96EA, 0xFFAACDF8, lit);
				int r = Math.min(w, h) * 36 / 100;
				int bx0 = cx - r;
				int by0 = cy - r + 1;
				int bx1 = cx + r;
				int by1 = cy + r + 1;
				Paint.round(s, bx0, by0, bx1, by1, 2, dim(0xFF8A5B33, lit));
				Paint.round(s, bx0, by0, bx1, by0 + r * 55 / 100, 2, dim(0xFF5DA83F, lit));
				Paint.round(s, bx0 + r * 30 / 100, by0 + r * 90 / 100, bx0 + r * 55 / 100, by0 + r * 115 / 100, 0, dim(0xFF6E4727, lit));
				Paint.round(s, bx0 + r * 110 / 100, by0 + r * 120 / 100, bx0 + r * 135 / 100, by0 + r * 145 / 100, 0, dim(0xFF6E4727, lit));
			}
			case CUSTOM -> {
				sky(s, x0, y0, x1, y1, 0xFF251C4A, 0xFF3A2C78, lit);
				int[] knobs = {32, 68, 48};
				for (int i = 0; i < 3; i++) {
					int ly = y0 + h * (24 + i * 26) / 100;
					Paint.round(s, x0 + w * 16 / 100, ly - 1, x1 - w * 16 / 100, ly + 2, 1, dim(0x66CDBBFF, lit));
					int kx = x0 + w * 16 / 100 + (w * 68 / 100) * knobs[i] / 100;
					Paint.round(s, x0 + w * 16 / 100, ly - 1, kx, ly + 2, 1, dim(0xFFB28CFF, lit));
					Paint.round(s, kx - 3, ly - 3, kx + 3, ly + 4, 3, dim(0xFFF1EEFA, lit));
				}
			}
		}
	}
}
