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
