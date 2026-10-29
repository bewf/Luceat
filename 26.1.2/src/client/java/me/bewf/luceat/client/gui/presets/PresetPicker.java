package me.bewf.luceat.client.gui.presets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
import me.bewf.luceat.client.gui.LuceatFonts;
import me.bewf.luceat.client.gui.LuceatLook;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// the first thing a new player sees, pick how much lighting to pay for
public final class PresetPicker extends Screen {
	private static final int GOLD = 0xFFFFD35A;
	private static final int FIRE = 0xFFFF5A3C;
	private static final long HOLD_NANOS = 1_500_000_000L;
	private static final float DRAG_NEEDED = 120.0F;
	private static final long REVEAL_NANOS = 1_100_000_000L;

	private final Screen parent;
	private final List<Presets.Preset> normal = new ArrayList<>();
	private Presets.Preset hardcore;
	private final String recommended = HardwareHint.recommended();

	private boolean holding;
	private long holdStart;
	private float dragged;
	private float crush;
	private long revealStart = -1L;
	private boolean unlocked;
	private final float[] hover = new float[4];
	private float tint;
	private int tintColour = 0xFF4FD1C5;
	private Presets.Preset chosen;
	private int chosenIndex;
	private long chosenAt;

	public PresetPicker(Screen parent) {
		super(Component.literal("Choose a preset"));
		this.parent = parent;
		for (Presets.Preset preset : Presets.all()) {
			if (preset.name().equals("HARDCORE")) {
				hardcore = preset;
			} else {
				normal.add(preset);
			}
		}
		LuceatConfig.get().hardcoreUnlocked = false;
		unlocked = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		PresetFilmstrips.releaseTextures();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		graphics.fill(0, 0, this.width, this.height, 0xE00B0B10);
		graphics.fill(0, 0, this.width, this.height, alpha(tintColour, Math.round(tint * 28.0F)));
	}

	private static int alpha(int rgb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	private static int accent(Presets.Preset preset) {
		return switch (preset.name().toUpperCase(Locale.ROOT)) {
			case "POTATO" -> 0xFFC9975B;
			case "BALANCED" -> 0xFF4FD1C5;
			case "PHENOMENAL" -> 0xFFB68CFF;
			default -> FIRE;
		};
	}

	private static float hash(int a, int b) {
		int h = a * 374761393 + b * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
	}

	private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
		graphics.fill(x0, y0, x1, y0 + 1, colour);
		graphics.fill(x0, y1 - 1, x1, y1, colour);
		graphics.fill(x0, y0, x0 + 1, y1, colour);
		graphics.fill(x1 - 1, y0, x1, y1, colour);
	}

	private void particles(GuiGraphicsExtractor graphics, int index, int x0, int y0, int x1, int y1, int colour, long now) {
		int w = x1 - x0;
		int h = y1 - y0;
		double t = now / 1.0E9;
		for (int k = 0; k < 14; k++) {
			float seed = hash(index, k);
			float seed2 = hash(k, index + 7);
			int px;
			int py;
			int a;
			int size = 1;
			switch (index) {
				case 0 -> {
					double phase = (t * 0.07 + seed) % 1.0;
					px = x0 + Math.round(seed2 * (w - 2));
					py = y1 - 2 - (int) (phase * (h - 2));
					a = (int) (90 * Math.sin(phase * Math.PI));
				}
				case 1 -> {
					double phase = (t * 0.11 + seed) % 1.0;
					px = x0 + 1 + Math.round(seed2 * (w - 4) + (float) Math.sin(t * 1.3 + k) * 3.0F);
					py = y1 - 2 - (int) (phase * (h - 2));
					a = (int) (150 * Math.sin(phase * Math.PI));
				}
				case 2 -> {
					double phase = (t * 0.45 + seed) % 1.0;
					px = x0 + 2 + Math.round(seed2 * (w - 6));
					py = y0 + 2 + Math.round(hash(k, 3 + index) * (h - 6));
					a = (int) (255 * Math.pow(Math.sin(phase * Math.PI), 3.0));
					size = a > 170 ? 2 : 1;
				}
				default -> {
					double phase = (t * 0.34 + seed) % 1.0;
					px = x0 + 1 + Math.round(seed2 * (w - 4) + (float) Math.sin(t * 5.0 + k * 2.0) * 2.0F);
					py = y1 - 2 - (int) (phase * (h - 2));
					a = (int) (230 * (1.0 - phase));
				}
			}
			if (px < x0 || py < y0 || px + size > x1 || py + size > y1 || a < 8) {
				continue;
			}
			int c = index == 3 && k % 3 == 0 ? 0xFFD35A : colour;
			graphics.fill(px, py, px + size, py + size, alpha(c, a));
			if (index == 2 && size == 2) {
				graphics.fill(px - 1, py, px, py + 1, alpha(c, a / 2));
				graphics.fill(px + 2, py, px + 3, py + 1, alpha(c, a / 2));
				graphics.fill(px, py - 1, px + 1, py, alpha(c, a / 2));
				graphics.fill(px, py + 2, px + 1, py + 3, alpha(c, a / 2));
			}
		}
	}

	private float reveal() {
		if (!unlocked) {
			return 0.0F;
		}
		if (revealStart < 0L) {
			return 1.0F;
		}
		float t = Math.min(1.0F, (System.nanoTime() - revealStart) / (float) REVEAL_NANOS);
		return t * t * (3.0F - 2.0F * t);
	}

	private float holdProgress() {
		if (!holding) {
			return 0.0F;
		}
		float byTime = (System.nanoTime() - holdStart) / (float) HOLD_NANOS;
		return Math.max(0.0F, Math.min(1.0F, Math.min(byTime, dragged / DRAG_NEEDED)));
	}

	private int cardTop() {
		return 78;
	}

	private int cardHeight() {
		return Math.min(this.height - cardTop() - 56, 300);
	}

	private int[] slots(int count, float shift) {
		int margin = 36;
		int gap = 12;
		int usable = this.width - margin * 2 - gap * (count - 1);
		int w = Math.min(190, usable / count);
		int total = w * count + gap * (count - 1);
		int x = (this.width - total) / 2 + Math.round(shift);
		int[] out = new int[count * 2];
		for (int i = 0; i < count; i++) {
			out[i * 2] = x + i * (w + gap);
			out[i * 2 + 1] = w;
		}
		return out;
	}

	// x and width of each card at this moment, the fourth  only exists once unlocked
	private int[] layout() {
		float r = reveal();
		float p = holdProgress();
		float shift = -p * 12.0F + (p > 0.0F ? (float) Math.sin(System.nanoTime() / 4.0E7) * p * 2.5F : 0.0F);
		int[] four = slots(3, shift);
		int[] five = slots(4, shift);
		int[] out = new int[8];
		for (int i = 0; i < 3; i++) {
			out[i * 2] = Math.round(four[i * 2] + (five[i * 2] - four[i * 2]) * r);
			out[i * 2 + 1] = Math.round(four[i * 2 + 1] + (five[i * 2 + 1] - four[i * 2 + 1]) * r);
		}
		out[6] = five[6];
		out[7] = Math.round(five[7] * r);
		return out;
	}

	private static String title(String name) {
		return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	private List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String tryLine = line.isEmpty() ? word : line + " " + word;
			if (LuceatFonts.width(this.font, LuceatFonts.quote(tryLine)) > width && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(tryLine);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		long now = System.nanoTime();
		float targetTint = 0.0F;
		int[] pre = layout();
		int preCount = reveal() > 0.0F && hardcore != null ? 4 : 3;
		for (int i = 0; i < preCount; i++) {
			boolean over = chosen == null && LuceatLook.inside(mouseX, mouseY, pre[i * 2], cardTop(), pre[i * 2] + pre[i * 2 + 1], cardTop() + cardHeight());
			hover[i] += ((over ? 1.0F : 0.0F) - hover[i]) * 0.22F;
			if (over) {
				targetTint = 1.0F;
				tintColour = accent(i < 3 ? normal.get(Math.min(i, normal.size() - 1)) : hardcore);
			}
		}
		tint += (targetTint - tint) * 0.12F;
		super.extractRenderState(graphics, mouseX, mouseY, partial);
		PresetFilmstrips.step();
		if (holding && holdProgress() >= 1.0F && hardcore != null && !unlocked) {
			unlock();
		}
		if (chosen != null && now - chosenAt > 380_000_000L) {
			Presets.Preset preset = chosen;
			chosen = null;
			LuceatConfig config = LuceatConfig.get();
			preset.applyTo(config);
			LuceatConfig.save();
			onClose();
			return;
		}
		Component heading = LuceatFonts.title("How much light do you want?");
		LuceatFonts.centred(graphics, this.font, heading, this.width / 2, 24, LuceatLook.TEXT, true);
		LuceatFonts.centred(graphics, this.font, LuceatFonts.body("You can change this any time in the settings"), this.width / 2, 42, LuceatLook.SOFT, true);

		int[] layout = layout();
		int count = reveal() > 0.0F && hardcore != null ? 4 : 3;
		int top = cardTop();
		int bottom = top + cardHeight();
		LuceatConfig live = LuceatConfig.get();
		for (int i = 0; i < count; i++) {
			Presets.Preset preset = i < 3 ? normal.get(Math.min(i, normal.size() - 1)) : hardcore;
			int x0 = layout[i * 2];
			int w = layout[i * 2 + 1];
			if (w < 24) {
				continue;
			}
			float h = hover[i];
			boolean gold = preset.name().equalsIgnoreCase(recommended);
			boolean hot = i == 3;
			boolean current = preset.name().equalsIgnoreCase(live.preset) && live.presetChosen;
			int colour = accent(preset);
			int lift = -Math.round(h * 4.0F);
			for (int g = 1; g <= 5; g++) {
				graphics.fill(x0 - g, top + lift - g, x0 + w + g, bottom + lift + g, alpha(colour, Math.round(h * 26.0F / g)));
			}
			int border = LuceatLook.mix(hot ? FIRE : gold ? GOLD : current ? LuceatLook.ACCENT : LuceatLook.BORDER, colour, h);
			LuceatLook.roundOutlined(graphics, x0, top + lift, x0 + w, bottom + lift, LuceatLook.mix(LuceatLook.CARD, LuceatLook.CARD_HOVER, h), border);
			if (gold || hot) {
				LuceatLook.round(graphics, x0 + 1, top + lift + 1, x0 + w - 1, top + lift + 3, border);
			}
			if (h > 0.02F || gold || hot) {
				double sweep = (now / 1.8E9 + i * 0.23) % 1.0;
				int sx = x0 + (int) (sweep * (w + 40)) - 20;
				int from = Math.max(x0 + 5, sx - 14);
				int to = Math.min(x0 + w - 5, sx + 14);
				if (to > from) {
					graphics.fill(from, top + lift, to, top + lift + 1, alpha(colour, Math.round(220.0F * Math.max(h, 0.55F))));
				}
			}
			int pad = 8;
			int imgW = w - pad * 2;
			int imgH = imgW * 2 / 3;
			int imgX = x0 + pad;
			int imgY = top + lift + pad + 2;
			PresetFilmstrips.Strip strip = PresetFilmstrips.of(preset);
			Identifier tex = strip.texture(now);
			if (tex != null) {
				graphics.blit(tex, imgX, imgY, imgX + imgW, imgY + imgH, 0.0F, 1.0F, 0.0F, 1.0F);
			} else {
				graphics.fill(imgX, imgY, imgX + imgW, imgY + imgH, 0xFF111116);
				int dots = (int) (now / 400_000_000L % 4);
				graphics.text(this.font, "Getting ready" + ".".repeat(dots), imgX + 8, imgY + imgH / 2 - 4, LuceatLook.MUTED, false);
			}
			particles(graphics, i, imgX, imgY, imgX + imgW, imgY + imgH, colour, now);
			outline(graphics, imgX - 1, imgY - 1, imgX + imgW + 1, imgY + imgH + 1, alpha(colour, 60 + Math.round(h * 120.0F)));
			int y = imgY + imgH + 10;
			LuceatFonts.draw(graphics, this.font, LuceatFonts.preset(preset.name(), title(preset.name())), x0 + pad, y, hot ? FIRE : LuceatLook.mix(LuceatLook.TEXT, colour, h * 0.6F), true);
			Component cost = LuceatFonts.body(preset.cost());
			LuceatFonts.draw(graphics, this.font, cost, x0 + w - pad - LuceatFonts.width(this.font, cost), y, LuceatLook.SOFT, false);
			y += 15;
			for (String line : wrap("“" + preset.blurb() + "”", w - pad * 2)) {
				if (y + 9 > bottom - 22) {
					break;
				}
				LuceatFonts.draw(graphics, this.font, LuceatFonts.quote(line), x0 + pad, y, LuceatLook.SOFT, false);
				y += 12;
			}
			if (gold) {
				Component tag = LuceatFonts.body("Recommended for you");
				int tw = LuceatFonts.width(this.font, tag) + 12;
				int tx = x0 + (w - tw) / 2;
				LuceatLook.round(graphics, tx, bottom + lift - 20, tx + tw, bottom + lift - 6, GOLD);
				LuceatFonts.draw(graphics, this.font, tag, tx + 6, bottom + lift - 17, 0xFF1A1405, false);
			} else if (current) {
				LuceatFonts.centred(graphics, this.font, LuceatFonts.body("Current"), x0 + w / 2, bottom + lift - 17, LuceatLook.ACCENT, false);
			}
			if (chosen != null && chosenIndex == i) {
				float t = (now - chosenAt) / 380_000_000.0F;
				for (int r = 0; r < 3; r++) {
					int grow = Math.round(t * (30.0F + r * 14.0F));
					outline(graphics, x0 - grow, top + lift - grow, x0 + w + grow, bottom + lift + grow, alpha(colour, Math.round((1.0F - t) * 200.0F / (r + 1))));
				}
			}
		}
		drawCrack(graphics, now);
		String gpu = HardwareHint.gpuName();
		if (!gpu.isEmpty()) {
			LuceatFonts.centred(graphics, this.font, LuceatFonts.body(gpu), this.width / 2, this.height - 30, LuceatLook.MUTED, false);
		}
		LuceatFonts.centred(graphics, this.font, LuceatFonts.body("Esc to skip"), this.width / 2, this.height - 16, LuceatLook.MUTED, false);
	}

	// a thin glowing split on the right edge that grows while you hold and pull
	private void drawCrack(GuiGraphicsExtractor graphics, long now) {
		if (unlocked || hardcore == null) {
			return;
		}
		float p = holdProgress();
		int mid = this.height / 2;
		int half = 10 + Math.round(p * (this.height / 2 - 80));
		int x = this.width - 4 - Math.round(p * 6.0F);
		int colour = LuceatLook.mix(0x44FF5A3C, 0xFFFF5A3C, p);
		graphics.fill(x, mid - half, x + 2, mid + half, colour);
		if (p > 0.0F) {
			int glow = LuceatLook.mix(0x00FF5A3C, 0x55FF5A3C, p);
			graphics.fill(x - 6, mid - half, x, mid + half, glow);
		}
	}

	private boolean inCrackZone(double mx, double my) {
		return !unlocked && hardcore != null && mx >= this.width - 28 && my >= 60 && my <= this.height - 60;
	}

	private void unlock() {
		holding = false;
		unlocked = true;
		revealStart = System.nanoTime();
		PresetFilmstrips.of(hardcore);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		if (chosen != null) {
			return true;
		}
		if (inCrackZone(mx, my)) {
			holding = true;
			holdStart = System.nanoTime();
			dragged = 0.0F;
			return true;
		}
		int[] layout = layout();
		int count = reveal() >= 1.0F && hardcore != null ? 4 : 3;
		for (int i = 0; i < count; i++) {
			int x0 = layout[i * 2];
			int w = layout[i * 2 + 1];
			if (LuceatLook.inside(mx, my, x0, cardTop(), x0 + w, cardTop() + cardHeight())) {
				Presets.Preset preset = i < 3 ? normal.get(Math.min(i, normal.size() - 1)) : hardcore;
				LuceatConfig config = LuceatConfig.get();
				preset.applyTo(config);
				LuceatConfig.save();
				onClose();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (holding) {
			if (dx < 0.0) {
				dragged += (float) -dx;
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding) {
			holding = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event);
	}
}
