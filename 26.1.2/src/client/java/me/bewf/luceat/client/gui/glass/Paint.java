package me.bewf.luceat.client.gui.glass;

public final class Paint {
	private static final float[][] CORNERS = new float[512][];

	private Paint() {
	}

	public interface Tint {
		int at(float x, float y);
	}

	public interface Rows {
		int at(float y);
	}

	public static int argb(int a, int r, int g, int b) {
		return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
	}

	public static int alpha(int argb, float scale) {
		int a = Math.round(((argb >>> 24) & 255) * Math.max(0.0F, Math.min(1.0F, scale)));
		return a << 24 | (argb & 0xFFFFFF);
	}

	public static int withAlpha(int argb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int from, int to, float t) {
		float c = Math.max(0.0F, Math.min(1.0F, t));
		int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * c);
		int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * c);
		int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * c);
		int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * c);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static float[] corner(int r) {
		float[] cached = CORNERS[r];
		if (cached == null) {
			cached = new float[r * r];
			for (int i = 0; i < r; i++) {
				for (int x = 0; x < r; x++) {
					float dx = r - (x + 0.5F);
					float dy = r - (i + 0.5F);
					float d = r - (float) Math.sqrt(dx * dx + dy * dy);
					cached[i * r + x] = Math.max(0.0F, Math.min(1.0F, d + 0.5F));
				}
			}
			CORNERS[r] = cached;
		}
		return cached;
	}

	private static int radius(int r, int x0, int y0, int x1, int y1) {
		return Math.max(0, Math.min(Math.min(r, 500), Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
	}

	public static void round(Surface s, int x0, int y0, int x1, int y1, int r, int argb) {
		rows(s, x0, y0, x1, y1, r, y -> argb, true);
	}

	public static void vertical(Surface s, int x0, int y0, int x1, int y1, int r, int top, int bottom) {
		float h = Math.max(1, y1 - y0);
		rows(s, x0, y0, x1, y1, r, y -> mix(top, bottom, (y - y0) / h), false);
	}

	public static void rows(Surface s, int lx0, int ly0, int lx1, int ly1, int lr, Rows colour, boolean flat) {
		int k = s.scale();
		int x0 = lx0 * k;
		int y0 = ly0 * k;
		int x1 = lx1 * k;
		int y1 = ly1 * k;
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		int r = radius(lr * k, x0, y0, x1, y1);
		float[] cov = r > 0 ? corner(r) : null;
		for (int i = 0; i < r; i++) {
			cornerRow(s, x0, x1, y0 + i, i, r, cov, colour.at((y0 + i + 0.5F) / k));
			cornerRow(s, x0, x1, y1 - 1 - i, i, r, cov, colour.at((y1 - 1 - i + 0.5F) / k));
		}
		int ys = y0 + r;
		int end = y1 - r;
		int band = flat ? Integer.MAX_VALUE : Math.max(1, (y1 - y0) / (k == 1 ? 90 : 360));
		while (ys < end) {
			int ye = flat ? end : (int) Math.min((long) end, (long) ys + band);
			s.fillPx(x0, ys, x1, ye, colour.at((ys + ye) * 0.5F / k));
			ys = ye;
		}
	}

	private static void cornerRow(Surface s, int x0, int x1, int y, int i, int r, float[] cov, int colour) {
		int xs = r;
		for (int x = 0; x < r; x++) {
			float c = cov[i * r + x];
			if (c >= 0.995F) {
				xs = x;
				break;
			}
			if (c > 0.02F) {
				int tint = alpha(colour, c);
				s.fillPx(x0 + x, y, x0 + x + 1, y + 1, tint);
				s.fillPx(x1 - 1 - x, y, x1 - x, y + 1, tint);
			}
		}
		if (x1 - xs > x0 + xs) {
			s.fillPx(x0 + xs, y, x1 - xs, y + 1, colour);
		}
	}

	public static void outline(Surface s, int lx0, int ly0, int lx1, int ly1, int lr, Tint tint) {
		int k = s.scale();
		outlinePx(s, lx0 * k, ly0 * k, lx1 * k, ly1 * k, lr * k, k, tint);
	}

	private static void outlinePx(Surface s, int x0, int y0, int x1, int y1, int rr, int k, Tint tint) {
		int r = radius(rr, x0, y0, x1, y1);
		int chunk = 8 * k;
		for (int x = x0 + r; x < x1 - r; x += chunk) {
			int xe = Math.min(x + chunk, x1 - r);
			float cx = (x + xe) * 0.5F / k;
			s.fillPx(x, y0, xe, y0 + 1, tint.at(cx, y0 / (float) k));
			s.fillPx(x, y1 - 1, xe, y1, tint.at(cx, (y1 - 1) / (float) k));
		}
		for (int y = y0 + r; y < y1 - r; y += chunk) {
			int ye = Math.min(y + chunk, y1 - r);
			float cy = (y + ye) * 0.5F / k;
			s.fillPx(x0, y, x0 + 1, ye, tint.at(x0 / (float) k, cy));
			s.fillPx(x1 - 1, y, x1, ye, tint.at((x1 - 1) / (float) k, cy));
		}
		for (int i = 0; i < r; i++) {
			for (int x = 0; x < r; x++) {
				float dx = r - (x + 0.5F);
				float dy = r - (i + 0.5F);
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
				float outer = Math.max(0.0F, Math.min(1.0F, r - dist + 0.5F));
				float inner = Math.max(0.0F, Math.min(1.0F, r - 1 - dist + 0.5F));
				float c = outer - inner;
				if (c > 0.03F) {
					plot(s, x0 + x, y0 + i, tint, c, k);
					plot(s, x1 - 1 - x, y0 + i, tint, c, k);
					plot(s, x0 + x, y1 - 1 - i, tint, c, k);
					plot(s, x1 - 1 - x, y1 - 1 - i, tint, c, k);
				}
			}
		}
	}

	private static void plot(Surface s, int x, int y, Tint tint, float c, int k) {
		s.fillPx(x, y, x + 1, y + 1, alpha(tint.at((x + 0.5F) / k, (y + 0.5F) / k), c));
	}

	public static void outline(Surface s, int x0, int y0, int x1, int y1, int r, int argb) {
		outline(s, x0, y0, x1, y1, r, (x, y) -> argb);
	}

	public static void halo(Surface s, int lx0, int ly0, int lx1, int ly1, int lr, int spread, int argb) {
		int k = s.scale();
		int steps = spread * k;
		for (int i = 1; i <= steps; i++) {
			float f = 1.0F - (float) i / (steps + 1);
			int colour = alpha(argb, f * f / (k == 1 ? 1.0F : 1.0F));
			outlinePx(s, lx0 * k - i, ly0 * k - i, lx1 * k + i, ly1 * k + i, lr * k + i, k, (x, y) -> colour);
		}
	}

	public static void horizontal(Surface s, int lx0, int ly0, int lx1, int ly1, int left, int right, int step) {
		int k = s.scale();
		int x0 = lx0 * k;
		int x1 = lx1 * k;
		float w = Math.max(1, x1 - x0);
		int stepPx = Math.max(1, k == 1 ? step : step * k / 4);
		for (int x = x0; x < x1; x += stepPx) {
			int xe = Math.min(x1, x + stepPx);
			s.fillPx(x, ly0 * k, xe, ly1 * k, mix(left, right, ((x + xe) * 0.5F - x0) / w));
		}
	}
}
