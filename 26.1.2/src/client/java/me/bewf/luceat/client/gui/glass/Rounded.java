package me.bewf.luceat.client.gui.glass;

import java.util.LinkedHashMap;
import java.util.Map;

import me.bewf.luceat.client.gui.GuiFit;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Rounded {
	private record Key(int radius, int scale, int fill, int border) {
	}

	private static final Map<Key, Pane> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Pane> eldest) {
			if (size() > 160) {
				eldest.getValue().release();
				return true;
			}
			return false;
		}
	};

	private Rounded() {
	}

	public static void release() {
		for (Pane pane : CACHE.values()) {
			pane.release();
		}
		CACHE.clear();
	}

	public static int radius(int w, int h) {
		int small = Math.min(w, h);
		int want = small < 20 ? 4 : small < 40 ? 6 : small < 80 ? 8 : 10;
		return Math.max(0, Math.min(want, small / 2));
	}

	public static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int fill, int border) {
		draw(g, x0, y0, x1, y1, radius(x1 - x0, y1 - y0), fill, border);
	}

	public static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius, int fill, int border) {
		int r = Math.max(0, Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		boolean edged = (border >>> 24) != 0;
		if (r < 2) {
			if (edged) {
				g.fill(x0, y0, x1, y1, border);
				g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
			} else {
				g.fill(x0, y0, x1, y1, fill);
			}
			return;
		}
		Pane sprite = sprite(r, fill, edged ? border : 0);
		sprite.blitPart(g, x0, y0, x0 + r, y0 + r, 0.0F, 0.5F, 0.0F, 0.5F);
		sprite.blitPart(g, x1 - r, y0, x1, y0 + r, 0.5F, 1.0F, 0.0F, 0.5F);
		sprite.blitPart(g, x0, y1 - r, x0 + r, y1, 0.0F, 0.5F, 0.5F, 1.0F);
		sprite.blitPart(g, x1 - r, y1 - r, x1, y1, 0.5F, 1.0F, 0.5F, 1.0F);
		if (edged) {
			g.fill(x0 + r, y0, x1 - r, y0 + 1, border);
			g.fill(x0 + r, y1 - 1, x1 - r, y1, border);
			g.fill(x0, y0 + r, x0 + 1, y1 - r, border);
			g.fill(x1 - 1, y0 + r, x1, y1 - r, border);
			g.fill(x0 + r, y0 + 1, x1 - r, y1 - 1, fill);
			g.fill(x0 + 1, y0 + r, x0 + r, y1 - r, fill);
			g.fill(x1 - r, y0 + r, x1 - 1, y1 - r, fill);
		} else {
			g.fill(x0 + r, y0, x1 - r, y1, fill);
			g.fill(x0, y0 + r, x0 + r, y1 - r, fill);
			g.fill(x1 - r, y0 + r, x1, y1 - r, fill);
		}
	}

	private static Pane sprite(int r, int fill, int border) {
		int scale = GuiFit.raster();
		Key key = new Key(r, scale, fill, border);
		Pane pane = CACHE.get(key);
		if (pane == null) {
			pane = new Pane();
			pane.begin(0, 0, r * 2, r * 2, scale);
			bake(pane, r, scale, fill, border);
			pane.finish();
			CACHE.put(key, pane);
		}
		return pane;
	}

	static void bake(Surface pane, int r, int k, int fill, int border) {
		int px = r * 2 * k;
		boolean edged = (border >>> 24) != 0;
		float inset = edged ? 1.0F : 0.0F;
		float fa = fill >>> 24;
		float ba = border >>> 24;
		for (int y = 0; y < px; y++) {
			for (int x = 0; x < px; x++) {
				float dx = Math.abs((x + 0.5F) / k - r);
				float dy = Math.abs((y + 0.5F) / k - r);
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
				float outer = Math.max(0.0F, Math.min(1.0F, (r - dist) * k + 0.5F));
				float inner = Math.max(0.0F, Math.min(1.0F, (r - inset - dist) * k + 0.5F));
				float wf = fa * inner;
				float wb = edged ? ba * (outer - inner) : 0.0F;
				float total = wf + wb;
				if (total < 0.5F) {
					continue;
				}
				int rr = Math.round((((fill >> 16) & 255) * wf + ((border >> 16) & 255) * wb) / total);
				int gg = Math.round((((fill >> 8) & 255) * wf + ((border >> 8) & 255) * wb) / total);
				int bb = Math.round(((fill & 255) * wf + (border & 255) * wb) / total);
				pane.fillPx(x, y, x + 1, y + 1, Math.round(total) << 24 | rr << 16 | gg << 8 | bb);
			}
		}
	}
}
