package me.bewf.luceat.client.gui.glass;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Icons {
	public static final int BOX = 9;
	private static final int SIZE = 11;

	public static final class Icon {
		private final float[] data;
		private final Map<Integer, float[]> masks = new HashMap<>();

		private Icon(String text) {
			String[] parts = text.split(" ");
			this.data = new float[parts.length];
			for (int i = 0; i < parts.length; i++) {
				this.data[i] = Float.parseFloat(parts[i]);
			}
		}
	}

	public static final Icon SUN = new Icon(LucideData.SUN);
	public static final Icon SHADOW = new Icon(LucideData.SHADOW);
	public static final Icon BULB = new Icon(LucideData.BULB);
	public static final Icon FLAME = new Icon(LucideData.FLAME);
	public static final Icon GAUGE = new Icon(LucideData.GAUGE);
	public static final Icon GEAR = new Icon(LucideData.GEAR);
	public static final Icon CUBE = new Icon(LucideData.CUBE);
	public static final Icon BOLT = new Icon(LucideData.BOLT);
	public static final Icon INFO = new Icon(LucideData.INFO);
	public static final Icon SEARCH = new Icon(LucideData.SEARCH);
	public static final Icon RESET = new Icon(LucideData.RESET);
	public static final Icon CLOSE = new Icon(LucideData.CLOSE);
	public static final Icon CHEVRON = new Icon(LucideData.CHEVRON);
	public static final Icon MOUSE = new Icon(LucideData.MOUSE);
	public static final Icon EXPAND = new Icon(LucideData.EXPAND);
	public static final Icon TRASH = new Icon(LucideData.TRASH);
	public static final Icon CHECK = new Icon(LucideData.CHECK);
	public static final Icon STAR = new Icon(LucideData.STAR);
	public static final Icon SLIDERS = new Icon(LucideData.SLIDERS);
	public static final Icon ALERT = new Icon(LucideData.ALERT);
	public static final Icon BUG = new Icon(LucideData.BUG);
	public static final Icon COPY = new Icon(LucideData.COPY);
	public static final Icon UNDO = new Icon(LucideData.UNDO);
	public static final Icon PLUS = new Icon(LucideData.PLUS);
	public static final Icon POWER = new Icon(LucideData.POWER);
	public static final Icon CPU = new Icon(LucideData.CPU);

	private static final Map<Long, Pane> SPRITES = new HashMap<>();

	private Icons() {
	}

	public static void draw(Surface s, int x, int y, Icon icon, int argb) {
		int offset = (SIZE - BOX) * s.scale() / 2;
		paint(s, x * s.scale() - offset, y * s.scale() - offset, SIZE * s.scale(), icon, argb);
	}

	public static void draw(Surface s, int x, int y, int size, Icon icon, int argb) {
		paint(s, x * s.scale(), y * s.scale(), size * s.scale(), icon, argb);
	}

	public static void sprite(GuiGraphicsExtractor graphics, int x, int y, int size, Icon icon, int argb) {
		int scale = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
		long key = ((long) System.identityHashCode(icon) << 32) ^ ((long) size << 24) ^ ((long) scale << 20) ^ (argb & 0xFFFFFFL) ^ ((long) (argb >>> 24) << 16);
		Pane pane = SPRITES.computeIfAbsent(key, k -> {
			Pane made = new Pane();
			made.begin(0, 0, size, size, scale);
			paint(made, 0, 0, size * scale, icon, argb);
			made.finish();
			return made;
		});
		pane.blitAt(graphics, x, y);
	}

	public static void release() {
		for (Pane pane : SPRITES.values()) {
			pane.release();
		}
		SPRITES.clear();
	}

	public static int width(Icon icon) {
		return BOX;
	}

	public static int height(Icon icon) {
		return BOX;
	}

	private static void paint(Surface s, int x0, int y0, int px, Icon icon, int argb) {
		float[] mask = icon.masks.computeIfAbsent(px, size -> build(icon.data, size));
		int base = argb >>> 24;
		int rgb = argb & 0xFFFFFF;
		for (int row = 0; row < px; row++) {
			int runStart = -1;
			int runAlpha = 0;
			for (int col = 0; col <= px; col++) {
				int alpha = col < px ? Math.round(mask[row * px + col] * base) : 0;
				if (runStart >= 0 && alpha != runAlpha) {
					s.fillPx(x0 + runStart, y0 + row, x0 + col, y0 + row + 1, runAlpha << 24 | rgb);
					runStart = -1;
				}
				if (runStart < 0 && alpha > 0) {
					runStart = col;
					runAlpha = alpha;
				}
			}
		}
	}

	private static float[] build(float[] data, int px) {
		float k = px / 24.0F;
		float half = Math.max(1.4F, 2.0F * k) / 2.0F;
		float[] mask = new float[px * px];
		int i = 0;
		while (i < data.length) {
			int count = (int) data[i++];
			float prevX = data[i] * k;
			float prevY = data[i + 1] * k;
			if (count == 1) {
				stroke(mask, px, half, prevX, prevY, prevX, prevY);
			}
			for (int p = 1; p < count; p++) {
				float nextX = data[i + p * 2] * k;
				float nextY = data[i + p * 2 + 1] * k;
				stroke(mask, px, half, prevX, prevY, nextX, nextY);
				prevX = nextX;
				prevY = nextY;
			}
			i += count * 2;
		}
		return mask;
	}

	private static void stroke(float[] mask, int px, float half, float x0, float y0, float x1, float y1) {
		int reach = (int) Math.ceil(half + 1.0F);
		int fromX = Math.max(0, (int) Math.floor(Math.min(x0, x1)) - reach);
		int toX = Math.min(px - 1, (int) Math.ceil(Math.max(x0, x1)) + reach);
		int fromY = Math.max(0, (int) Math.floor(Math.min(y0, y1)) - reach);
		int toY = Math.min(px - 1, (int) Math.ceil(Math.max(y0, y1)) + reach);
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = dx * dx + dy * dy;
		for (int y = fromY; y <= toY; y++) {
			for (int x = fromX; x <= toX; x++) {
				float cx = x + 0.5F;
				float cy = y + 0.5F;
				float t = length == 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, ((cx - x0) * dx + (cy - y0) * dy) / length));
				float ex = cx - (x0 + t * dx);
				float ey = cy - (y0 + t * dy);
				float cover = Math.max(0.0F, Math.min(1.0F, half - (float) Math.sqrt(ex * ex + ey * ey) + 0.5F));
				if (cover > mask[y * px + x]) {
					mask[y * px + x] = cover;
				}
			}
		}
	}
}
