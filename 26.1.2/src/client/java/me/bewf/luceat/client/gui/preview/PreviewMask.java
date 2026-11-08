package me.bewf.luceat.client.gui.preview;

import java.util.stream.IntStream;

final class PreviewMask {
	private PreviewMask() {
	}

	static void apply(int[] pixels, int width, int height, int radius, int fade, boolean roundLeft) {
		float fadeWidth = Math.max(1, fade);
		IntStream.range(0, height).parallel().forEach(y -> {
			for (int x = 0; x < width; x++) {
				float alpha = 1.0F;
				if (fade > 0) {
					float t = Math.min(1.0F, (x + 0.5F) / fadeWidth);
					alpha = t * t * (3.0F - 2.0F * t);
				}
				alpha *= corner(x, y, width, height, radius, roundLeft);
				int a = Math.round(alpha * 255.0F);
				int index = x + y * width;
				pixels[index] = (pixels[index] & 0x00FFFFFF) | (a << 24);
			}
		});
	}


	static void scrim(int[] pixels, int width, int height, int top, int bottom, int topAlpha, int bottomAlpha) {
		for (int y = 0; y < height; y++) {
			float a = 0.0F;
			if (y < top) {
				float t = 1.0F - y / (float) top;
				a = topAlpha / 255.0F * t * t;
			}
			int fromBottom = height - 1 - y;
			if (fromBottom < bottom) {
				float t = 1.0F - fromBottom / (float) bottom;
				a = Math.max(a, bottomAlpha / 255.0F * t * t);
			}
			if (a <= 0.001F) {
				continue;
			}
			for (int x = 0; x < width; x++) {
				int p = pixels[x + y * width];
				int r = Math.round(((p >> 16) & 255) * (1.0F - a) + 6 * a);
				int g = Math.round(((p >> 8) & 255) * (1.0F - a) + 4 * a);
				int b = Math.round((p & 255) * (1.0F - a) + 14 * a);
				pixels[x + y * width] = (p & 0xFF000000) | r << 16 | g << 8 | b;
			}
		}
	}

	static void rim(int[] pixels, int width, int height, int[] traced, int tw, int th, int band, float blurUnits, float pixelsPerUnit) {
		if (band < 2 || blurUnits <= 0.0F) {
			return;
		}
		float traceScale = tw / (float) width;
		float cellPx = Math.max(1.0F, blurUnits * pixelsPerUnit * traceScale / 4.0F);
		int cw = Math.max(2, Math.round(tw / cellPx));
		int ch = Math.max(2, Math.round(th / cellPx));
		float[][] small = new float[3][cw * ch];
		int[] counts = new int[cw * ch];
		for (int y = 0; y < th; y++) {
			int cy = Math.min(ch - 1, y * ch / th);
			for (int x = 0; x < tw; x++) {
				int cx = Math.min(cw - 1, x * cw / tw);
				int p = traced[x + y * tw];
				int i = cx + cy * cw;
				small[0][i] += (p >> 16) & 255;
				small[1][i] += (p >> 8) & 255;
				small[2][i] += p & 255;
				counts[i]++;
			}
		}
		for (int i = 0; i < counts.length; i++) {
			float inv = counts[i] == 0 ? 0.0F : 1.0F / counts[i];
			small[0][i] *= inv;
			small[1][i] *= inv;
			small[2][i] *= inv;
		}
		float[][] soft = new float[3][];
		for (int c = 0; c < 3; c++) {
			soft[c] = boxBlur(small[c], cw, ch, 4);
		}
		IntStream.range(0, height).parallel().forEach(y -> {
			float dy = Math.min(y + 0.5F, height - y - 0.5F);
			for (int x = 0; x < width; x++) {
				float d = Math.min(dy, width - x - 0.5F);
				if (d >= band) {
					continue;
				}
				float t = Math.max(0.0F, d / band);
				float w = 1.0F - t * t * (3.0F - 2.0F * t);
				float u = Math.max(0.0F, Math.min(cw - 1.0F, (x + 0.5F) * cw / width - 0.5F));
				float v = Math.max(0.0F, Math.min(ch - 1.0F, (y + 0.5F) * ch / height - 0.5F));
				int x0 = (int) u;
				int y0 = (int) v;
				int x1 = Math.min(cw - 1, x0 + 1);
				int y1 = Math.min(ch - 1, y0 + 1);
				float fx = u - x0;
				float fy = v - y0;
				int index = x + y * width;
				int p = pixels[index];
				int out = p & 0xFF000000;
				for (int c = 0; c < 3; c++) {
					float[] s = soft[c];
					float top = s[x0 + y0 * cw] * (1.0F - fx) + s[x1 + y0 * cw] * fx;
					float bottom = s[x0 + y1 * cw] * (1.0F - fx) + s[x1 + y1 * cw] * fx;
					float blurred = top * (1.0F - fy) + bottom * fy;
					int shift = 16 - 8 * c;
					float sharp = (p >> shift) & 255;
					int value = Math.round(sharp + (blurred - sharp) * w);
					out |= Math.max(0, Math.min(255, value)) << shift;
				}
				pixels[index] = out;
			}
		});
	}

	private static float[] boxBlur(float[] source, int w, int h, int radius) {
		float[] current = source;
		for (int pass = 0; pass < 2; pass++) {
			float[] across = new float[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float sum = 0.0F;
					for (int k = -radius; k <= radius; k++) {
						sum += current[Math.max(0, Math.min(w - 1, x + k)) + y * w];
					}
					across[x + y * w] = sum / (radius * 2 + 1);
				}
			}
			float[] down = new float[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float sum = 0.0F;
					for (int k = -radius; k <= radius; k++) {
						sum += across[x + Math.max(0, Math.min(h - 1, y + k)) * w];
					}
					down[x + y * w] = sum / (radius * 2 + 1);
				}
			}
			current = down;
		}
		return current;
	}

	private static float corner(int x, int y, int width, int height, int radius, boolean roundLeft) {
		float px = x + 0.5F;
		float py = y + 0.5F;
		float cx;
		float cy;
		if (px >= width - radius) {
			cx = width - radius;
		} else if (roundLeft && px <= radius) {
			cx = radius;
		} else {
			return 1.0F;
		}
		if (py <= radius) {
			cy = radius;
		} else if (py >= height - radius) {
			cy = height - radius;
		} else {
			return 1.0F;
		}
		float distance = (float) Math.hypot(px - cx, py - cy);
		return Math.max(0.0F, Math.min(1.0F, radius - distance + 0.5F));
	}
}
