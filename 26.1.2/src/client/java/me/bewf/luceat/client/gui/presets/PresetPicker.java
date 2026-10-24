package me.bewf.luceat.client.gui.presets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
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
		unlocked = LuceatConfig.get().hardcoreUnlocked && hardcore != null;
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

	// x and width of each card at this moment, the fifth only exists once unlocked
	private int[] layout() {
		float r = reveal();
		float p = holdProgress();
		float shift = -p * 12.0F + (p > 0.0F ? (float) Math.sin(System.nanoTime() / 4.0E7) * p * 2.5F : 0.0F);
		int[] four = slots(4, shift);
		int[] five = slots(5, shift);
		int[] out = new int[10];
		for (int i = 0; i < 4; i++) {
			out[i * 2] = Math.round(four[i * 2] + (five[i * 2] - four[i * 2]) * r);
			out[i * 2 + 1] = Math.round(four[i * 2 + 1] + (five[i * 2 + 1] - four[i * 2 + 1]) * r);
		}
		out[8] = five[8];
		out[9] = Math.round(five[9] * r);
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
			if (this.font.width(tryLine) > width && !line.isEmpty()) {
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
		super.extractRenderState(graphics, mouseX, mouseY, partial);
		PresetFilmstrips.step();
		long now = System.nanoTime();
		if (holding && holdProgress() >= 1.0F && hardcore != null && !unlocked) {
			unlock();
		}
		String heading = "How much light do you want?";
		graphics.text(this.font, heading, (this.width - this.font.width(heading)) / 2, 26, LuceatLook.TEXT, true);
		String sub = "You can change this any time in the settings";
		graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 42, LuceatLook.SOFT, true);

		int[] layout = layout();
		int count = reveal() > 0.0F && hardcore != null ? 5 : 4;
		int top = cardTop();
		int bottom = top + cardHeight();
		LuceatConfig live = LuceatConfig.get();
		for (int i = 0; i < count; i++) {
			Presets.Preset preset = i < 4 ? normal.get(Math.min(i, normal.size() - 1)) : hardcore;
			int x0 = layout[i * 2];
			int w = layout[i * 2 + 1];
			if (w < 24) {
				continue;
			}
			boolean hover = LuceatLook.inside(mouseX, mouseY, x0, top, x0 + w, bottom);
			boolean gold = preset.name().equalsIgnoreCase(recommended);
			boolean hot = i == 4;
			boolean current = preset.name().equalsIgnoreCase(live.preset) && live.presetChosen;
			int lift = hover ? -3 : 0;
			int border = hot ? FIRE : gold ? GOLD : current ? LuceatLook.ACCENT : LuceatLook.BORDER;
			LuceatLook.roundOutlined(graphics, x0, top + lift, x0 + w, bottom + lift, hover ? LuceatLook.CARD_HOVER : LuceatLook.CARD, border);
			if (gold || hot) {
				LuceatLook.round(graphics, x0 + 1, top + lift + 1, x0 + w - 1, top + lift + 3, border);
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
				String wait = "Getting ready";
				int dots = (int) (now / 400_000_000L % 4);
				graphics.text(this.font, wait + ".".repeat(dots), imgX + 8, imgY + imgH / 2 - 4, LuceatLook.MUTED, false);
			}
			int y = imgY + imgH + 10;
			graphics.text(this.font, title(preset.name()), x0 + pad, y, hot ? FIRE : LuceatLook.TEXT, true);
			String cost = preset.cost();
			graphics.text(this.font, cost, x0 + w - pad - this.font.width(cost), y, LuceatLook.SOFT, false);
			y += 14;
			for (String line : wrap("“" + preset.blurb() + "”", w - pad * 2)) {
				if (y + 9 > bottom - 22) {
					break;
				}
				graphics.text(this.font, line, x0 + pad, y, LuceatLook.SOFT, false);
				y += 11;
			}
			if (gold) {
				String tag = "Recommended for you";
				int tw = this.font.width(tag) + 12;
				int tx = x0 + (w - tw) / 2;
				LuceatLook.round(graphics, tx, bottom + lift - 20, tx + tw, bottom + lift - 6, GOLD);
				graphics.text(this.font, tag, tx + 6, bottom + lift - 17, 0xFF1A1405, false);
			} else if (current) {
				String tag = "Current";
				graphics.text(this.font, tag, x0 + (w - this.font.width(tag)) / 2, bottom + lift - 17, LuceatLook.ACCENT, false);
			}
		}
		drawCrack(graphics, now);
		String gpu = HardwareHint.gpuName();
		if (!gpu.isEmpty()) {
			String line = gpu;
			graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, this.height - 30, LuceatLook.MUTED, false);
		}
		String skip = "Esc to skip";
		graphics.text(this.font, skip, (this.width - this.font.width(skip)) / 2, this.height - 16, LuceatLook.MUTED, false);
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
		LuceatConfig config = LuceatConfig.get();
		config.hardcoreUnlocked = true;
		LuceatConfig.save();
		PresetFilmstrips.of(hardcore);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		if (inCrackZone(mx, my)) {
			holding = true;
			holdStart = System.nanoTime();
			dragged = 0.0F;
			return true;
		}
		int[] layout = layout();
		int count = reveal() >= 1.0F && hardcore != null ? 5 : 4;
		for (int i = 0; i < count; i++) {
			int x0 = layout[i * 2];
			int w = layout[i * 2 + 1];
			if (LuceatLook.inside(mx, my, x0, cardTop(), x0 + w, cardTop() + cardHeight())) {
				Presets.Preset preset = i < 4 ? normal.get(Math.min(i, normal.size() - 1)) : hardcore;
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
