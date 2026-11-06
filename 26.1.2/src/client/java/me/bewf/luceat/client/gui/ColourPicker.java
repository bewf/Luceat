package me.bewf.luceat.client.gui;

import java.util.Locale;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

// a small popup for choosing a light colour by eye, by numbers or by hex
public final class ColourPicker {
	public static final int WIDTH = 214;
	public static final int HEIGHT = 206;
	private static final int SV_W = 128;
	private static final int SV_H = 96;
	private static final int CELL = 4;
	private static final int[] QUICK = {
		0xFFB84D, 0xFF7A2E, 0xFF4B3A, 0xFFE27A, 0xFFFFFF, 0xBFE8FF,
		0x4DA6FF, 0x6B5BFF, 0xBE3FFF, 0xFF6FD8, 0x5BE38A, 0x2EE6D6
	};

	private final TextInput hexInput = new TextInput(7);
	private final TextInput[] channel = {new TextInput(3), new TextInput(3), new TextInput(3)};
	private boolean open;
	private float hue;
	private float sat;
	private float val;
	private int original;
	private int current;
	private IntConsumer listener;
	private int drag;
	private int px;
	private int py;

	public boolean isOpen() {
		return open;
	}

	public void open(int rgb, IntConsumer onChange) {
		open = true;
		listener = onChange;
		original = rgb & 0xFFFFFF;
		setColour(original, true);
		drag = 0;
	}

	public void close() {
		open = false;
		drag = 0;
		hexInput.focus(false);
		for (TextInput input : channel) {
			input.focus(false);
		}
	}

	public boolean typing() {
		return open && (hexInput.focused() || channel[0].focused() || channel[1].focused() || channel[2].focused());
	}

	private void setColour(int rgb, boolean updateHsv) {
		current = rgb & 0xFFFFFF;
		if (updateHsv) {
			float[] hsv = toHsv(current);
			if (hsv[1] > 0.001F && hsv[2] > 0.001F) {
				hue = hsv[0];
			}
			sat = hsv[1];
			val = hsv[2];
		}
		sync();
	}

	private void sync() {
		if (!hexInput.focused()) {
			hexInput.set(String.format(Locale.ROOT, "#%06X", current));
		}
		int[] parts = {current >> 16 & 255, current >> 8 & 255, current & 255};
		for (int i = 0; i < 3; i++) {
			if (!channel[i].focused()) {
				channel[i].set(Integer.toString(parts[i]));
			}
		}
	}

	private void fromHsv() {
		setColour(toRgb(hue, sat, val), false);
		listener.accept(current);
	}

	public static int toRgb(float h, float s, float v) {
		float c = v * s;
		float hp = (h % 1.0F) * 6.0F;
		float x = c * (1.0F - Math.abs(hp % 2.0F - 1.0F));
		float m = v - c;
		float r;
		float g;
		float b;
		if (hp < 1) {
			r = c;
			g = x;
			b = 0;
		} else if (hp < 2) {
			r = x;
			g = c;
			b = 0;
		} else if (hp < 3) {
			r = 0;
			g = c;
			b = x;
		} else if (hp < 4) {
			r = 0;
			g = x;
			b = c;
		} else if (hp < 5) {
			r = x;
			g = 0;
			b = c;
		} else {
			r = c;
			g = 0;
			b = x;
		}
		return Math.round((r + m) * 255.0F) << 16 | Math.round((g + m) * 255.0F) << 8 | Math.round((b + m) * 255.0F);
	}

	public static float[] toHsv(int rgb) {
		float r = (rgb >> 16 & 255) / 255.0F;
		float g = (rgb >> 8 & 255) / 255.0F;
		float b = (rgb & 255) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d < 1.0E-6F) {
			h = 0.0F;
		} else if (max == r) {
			h = ((g - b) / d % 6.0F) / 6.0F;
		} else if (max == g) {
			h = ((b - r) / d + 2.0F) / 6.0F;
		} else {
			h = ((r - g) / d + 4.0F) / 6.0F;
		}
		if (h < 0.0F) {
			h += 1.0F;
		}
		return new float[]{h, max <= 0.0F ? 0.0F : d / max, max};
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int mx, int my) {
		if (!open) {
			return;
		}
		px = x;
		py = y;
		LuceatLook.roundOutlined(graphics, x, y, x + WIDTH, y + HEIGHT, LuceatLook.WINDOW, 0x99E4D6FF);
		LuceatFonts.draw(graphics, font, LuceatFonts.body("Pick a colour"), x + 8, y + 7, LuceatLook.TEXT, false);
		String done = "Done";
		int dw = LuceatFonts.width(font, LuceatFonts.body(done)) + 12;
		boolean overDone = LuceatLook.inside(mx, my, x + WIDTH - dw - 6, y + 4, x + WIDTH - 6, y + 18);
		LuceatLook.roundOutlined(graphics, x + WIDTH - dw - 6, y + 4, x + WIDTH - 6, y + 18, overDone ? LuceatLook.CARD_HOVER : LuceatLook.CARD, overDone ? 0xCCE4D6FF : LuceatLook.BORDER);
		LuceatFonts.draw(graphics, font, LuceatFonts.body(done), x + WIDTH - dw, y + 7, LuceatLook.TEXT, false);

		int sx = x + 8;
		int sy = y + 24;
		for (int cy = 0; cy < SV_H / CELL; cy++) {
			for (int cx = 0; cx < SV_W / CELL; cx++) {
				float s = (cx + 0.5F) / (SV_W / CELL);
				float v = 1.0F - (cy + 0.5F) / (SV_H / CELL);
				graphics.fill(sx + cx * CELL, sy + cy * CELL, sx + cx * CELL + CELL, sy + cy * CELL + CELL, 0xFF000000 | toRgb(hue, s, v));
			}
		}
		int markX = sx + Math.round(sat * (SV_W - 1));
		int markY = sy + Math.round((1.0F - val) * (SV_H - 1));
		graphics.outline(markX - 3, markY - 3, 7, 7, 0xFFFFFFFF);
		graphics.outline(markX - 4, markY - 4, 9, 9, 0xFF000000);

		int hx = x + 144;
		for (int i = 0; i < SV_H / 2; i++) {
			graphics.fill(hx, sy + i * 2, hx + 14, sy + i * 2 + 2, 0xFF000000 | toRgb(i / (SV_H / 2.0F), 1.0F, 1.0F));
		}
		int hy = sy + Math.round(hue * (SV_H - 2));
		graphics.fill(hx - 2, hy - 1, hx + 16, hy + 2, 0xFFFFFFFF);
		graphics.fill(hx - 1, hy, hx + 15, hy + 1, 0xFF000000);

		int wx = x + 166;
		LuceatFonts.draw(graphics, font, LuceatFonts.body("Old"), wx, sy, LuceatLook.MUTED, false);
		LuceatLook.swatch(graphics, wx, sy + 11, wx + 40, sy + 33, original, false);
		LuceatFonts.draw(graphics, font, LuceatFonts.body("New"), wx, sy + 38, LuceatLook.MUTED, false);
		LuceatLook.swatch(graphics, wx, sy + 49, wx + 40, sy + 71, current, false);

		int fy = sy + SV_H + 8;
		LuceatFonts.draw(graphics, font, LuceatFonts.body("Hex"), x + 8, fy + 5, LuceatLook.SOFT, false);
		hexInput.draw(graphics, font, x + 30, fy, x + 90, fy + 18, "#RRGGBB");
		String[] names = {"R", "G", "B"};
		for (int i = 0; i < 3; i++) {
			int fx = x + 98 + i * 38;
			LuceatFonts.draw(graphics, font, LuceatFonts.body(names[i]), fx, fy - 9, LuceatLook.MUTED, false);
			channel[i].draw(graphics, font, fx, fy, fx + 34, fy + 18, "0");
		}
		int qy = fy + 26;
		LuceatFonts.draw(graphics, font, LuceatFonts.body("Quick picks"), x + 8, qy, LuceatLook.MUTED, false);
		for (int i = 0; i < QUICK.length; i++) {
			int qx = x + 8 + i * 17;
			boolean over = LuceatLook.inside(mx, my, qx, qy + 11, qx + 15, qy + 26);
			LuceatLook.swatch(graphics, qx, qy + 11, qx + 15, qy + 26, QUICK[i], over);
		}
	}

	private void pickSv(double mx, double my) {
		sat = (float) Math.max(0.0, Math.min(1.0, (mx - (px + 8)) / (SV_W - 1)));
		val = 1.0F - (float) Math.max(0.0, Math.min(1.0, (my - (py + 24)) / (SV_H - 1)));
		fromHsv();
	}

	private void pickHue(double my) {
		hue = (float) Math.max(0.0, Math.min(0.999, (my - (py + 24)) / (SV_H - 2)));
		fromHsv();
	}

	public boolean contains(double mx, double my) {
		return open && LuceatLook.inside(mx, my, px, py, px + WIDTH, py + HEIGHT);
	}

	public boolean mouseClicked(double mx, double my) {
		if (!open) {
			return false;
		}
		if (!contains(mx, my)) {
			close();
			return false;
		}
		String done = "Done";
		int dw = 38 + 0 * done.length();
		if (LuceatLook.inside(mx, my, px + WIDTH - dw - 12, py + 4, px + WIDTH - 6, py + 18)) {
			close();
			return true;
		}
		int sx = px + 8;
		int sy = py + 24;
		if (LuceatLook.inside(mx, my, sx, sy, sx + SV_W, sy + SV_H)) {
			drag = 1;
			pickSv(mx, my);
			return true;
		}
		if (LuceatLook.inside(mx, my, px + 142, sy, px + 162, sy + SV_H)) {
			drag = 2;
			pickHue(my);
			return true;
		}
		int fy = sy + SV_H + 8;
		boolean hexHit = LuceatLook.inside(mx, my, px + 30, fy, px + 90, fy + 18);
		hexInput.focus(hexHit);
		if (hexHit) {
			hexInput.mouseClicked(mx, my);
		}
		for (int i = 0; i < 3; i++) {
			int fx = px + 98 + i * 38;
			boolean hit = LuceatLook.inside(mx, my, fx, fy, fx + 34, fy + 18);
			channel[i].focus(hit);
			if (hit) {
				channel[i].mouseClicked(mx, my);
			}
		}
		int qy = fy + 26;
		for (int i = 0; i < QUICK.length; i++) {
			int qx = px + 8 + i * 17;
			if (LuceatLook.inside(mx, my, qx, qy + 11, qx + 15, qy + 26)) {
				setColour(QUICK[i], true);
				listener.accept(current);
				return true;
			}
		}
		return true;
	}

	public boolean mouseDragged(double mx, double my) {
		if (!open) {
			return false;
		}
		if (drag == 1) {
			pickSv(mx, my);
			return true;
		}
		if (drag == 2) {
			pickHue(my);
			return true;
		}
		return hexInput.mouseDragged(mx) || channel[0].mouseDragged(mx) || channel[1].mouseDragged(mx) || channel[2].mouseDragged(mx);
	}

	public boolean mouseReleased() {
		hexInput.mouseReleased();
		for (TextInput input : channel) {
			input.mouseReleased();
		}
		boolean was = drag != 0;
		drag = 0;
		return was;
	}

	public boolean charTyped(CharacterEvent event) {
		if (!open) {
			return false;
		}
		boolean used = hexInput.charTyped(event);
		for (TextInput input : channel) {
			used |= input.charTyped(event);
		}
		if (used) {
			readFields();
		}
		return used;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!open) {
			return false;
		}
		boolean used = hexInput.keyPressed(event);
		for (TextInput input : channel) {
			used |= input.keyPressed(event);
		}
		if (used) {
			readFields();
		}
		return used;
	}

	private void readFields() {
		if (hexInput.focused()) {
			String text = hexInput.text().trim();
			if (text.startsWith("#")) {
				text = text.substring(1);
			}
			if (text.length() == 6) {
				try {
					setColour(Integer.parseInt(text, 16), true);
					listener.accept(current);
				} catch (NumberFormatException ignored) {
				}
			}
			return;
		}
		int[] parts = {current >> 16 & 255, current >> 8 & 255, current & 255};
		for (int i = 0; i < 3; i++) {
			if (channel[i].focused()) {
				try {
					parts[i] = Math.max(0, Math.min(255, Integer.parseInt(channel[i].text().trim())));
				} catch (NumberFormatException ignored) {
					return;
				}
			}
		}
		setColour(parts[0] << 16 | parts[1] << 8 | parts[2], true);
		listener.accept(current);
	}
}
