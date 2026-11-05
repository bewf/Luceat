package me.bewf.luceat.client.gui.glass;

public interface Surface {
	int scale();

	void fillPx(int x0, int y0, int x1, int y1, int argb);

	void clip(int x0, int y0, int x1, int y1);

	void unclip();

	void text(Role role, String text, int x, int y, int argb);

	int width(Role role, String text);

	default void fill(int x0, int y0, int x1, int y1, int argb) {
		int s = scale();
		fillPx(x0 * s, y0 * s, x1 * s, y1 * s, argb);
	}

	default void grain(int lx0, int ly0, int lx1, int ly1, int radius, int light, int dark) {
		int k = scale();
		int x0 = lx0 * k;
		int y0 = ly0 * k;
		int x1 = lx1 * k;
		int y1 = ly1 * k;
		int r = radius * k;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				int cx = x < x0 + r ? x0 + r - x : x >= x1 - r ? x - (x1 - r) + 1 : 0;
				int cy = y < y0 + r ? y0 + r - y : y >= y1 - r ? y - (y1 - r) + 1 : 0;
				if (cx > 0 && cy > 0 && cx * cx + cy * cy > r * r) {
					continue;
				}
				float n = Frost.at((x + 0.5F) / k, (y + 0.5F) / k);
				if (n > 0.0F) {
					int a = Math.round(light * n);
					if (a > 0) {
						fillPx(x, y, x + 1, y + 1, a << 24 | 0xECE2FF);
					}
				} else {
					int a = Math.round(dark * -n);
					if (a > 0) {
						fillPx(x, y, x + 1, y + 1, a << 24 | 0x06030E);
					}
				}
			}
		}
	}

	default void frost(int x0, int y0, int x1, int y1, int inner, int edge, int edgeWidth) {
	}

	enum Role {
		BODY,
		SMALL,
		TITLE,
		WORDMARK,
		HEADING,
		QUOTE,
		POTATO,
		BALANCED,
		PHENOMENAL,
		HARDCORE
	}
}
