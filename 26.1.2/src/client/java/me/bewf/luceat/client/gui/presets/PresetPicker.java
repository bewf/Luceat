package me.bewf.luceat.client.gui.presets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
import me.bewf.luceat.client.gui.LuceatFonts;
import me.bewf.luceat.client.gui.LuceatLook;
import me.bewf.luceat.client.gui.glass.Glass;
import me.bewf.luceat.client.gui.glass.Icons;
import me.bewf.luceat.client.gui.glass.LuceatBlur;
import me.bewf.luceat.client.gui.glass.Rounded;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

public final class PresetPicker extends Screen {
	private static final int CREAM = 0xFFF4EAD2;
	private static final int MAGENTA = 0xFFBE3FFF;
	private static final long HOLD_NANOS = 1_500_000_000L;
	private static final float DRAG_NEEDED = 120.0F;
	private static final long INTENT_NANOS = 80_000_000L;
	private static final long LEAVE_NANOS = 150_000_000L;
	private static final long RING_NANOS = 350_000_000L;
	private static final float IMAGE_ASPECT = PresetFilmstrips.WIDTH / (float) PresetFilmstrips.HEIGHT;
	private static final int RADIUS = 16;
	private static final int GAP = 14;

	private final Screen parent;
	private final List<Presets.Preset> normal = new ArrayList<>();
	private Presets.Preset hardcore;
	private final String recommended = HardwareHint.recommended();

	private boolean holding;
	private long holdStart;
	private float dragged;
	private long ringStart = -1L;
	private boolean unlocked;
	private final float[] weight = {1.0F / 3.0F, 1.0F / 3.0F, 1.0F / 3.0F, 0.0F};
	private final float[] dim = new float[4];
	private final float[] lift = new float[4];
	private final float[] weightVel = new float[4];
	private final float[] liftVel = new float[4];
	private int focus = -1;
	private int pending = -1;
	private long pendingSince;
	private long leftSince = -1L;
	private double lastMouseX = -1.0;
	private double lastMouseY = -1.0;
	private boolean mouseLive;
	private long lastNanos = System.nanoTime();
	private Presets.Preset chosen;
	private int chosenIndex;
	private long chosenAt;
	private final long openAt = System.nanoTime();

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
	protected void init() {
		LuceatBlur.activate(false);
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
		LuceatBlur.activate(false);
		PresetFilmstrips.releaseTextures();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		extractBlurredBackground(graphics);
		boolean blurred = this.minecraft.options.getMenuBackgroundBlurriness() >= 1;
		graphics.fill(0, 0, this.width, this.height, blurred ? 0x42080511 : 0xB8080511);
	}

	private static int alpha(int rgb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	private static int fadeArgb(int argb, float f) {
		return (Math.round((argb >>> 24) * Math.max(0.0F, Math.min(1.0F, f))) << 24) | (argb & 0xFFFFFF);
	}

	private static float q(float value) {
		return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 8.0F) / 8.0F;
	}

	private static int accent(Presets.Preset preset) {
		return switch (preset.name().toUpperCase(Locale.ROOT)) {
			case "POTATO" -> 0xFFD9B27C;
			case "BALANCED" -> 0xFF5ED6C9;
			case "PHENOMENAL" -> 0xFFB68CFF;
			default -> MAGENTA;
		};
	}

	private static float hash(int a, int b) {
		int h = a * 374761393 + b * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
	}

	private static float ease(float t) {
		float c = Math.max(0.0F, Math.min(1.0F, t));
		float k = 1.0F - c;
		return 1.0F - k * k * k;
	}

	private float holdProgress() {
		if (!holding) {
			return 0.0F;
		}
		float byTime = (System.nanoTime() - holdStart) / (float) HOLD_NANOS;
		return Math.max(0.0F, Math.min(1.0F, Math.min(byTime, dragged / DRAG_NEEDED)));
	}

	private int count() {
		return unlocked && hardcore != null ? 4 : 3;
	}

	private int cardTop() {
		return Math.round(this.height * 0.15F);
	}

	private int cardBottom() {
		return Math.round(this.height * 0.88F);
	}

	private float[] targetWeights(int cards, int focusIndex) {
		float[] out = new float[4];
		float share = focusIndex < 0 ? 1.0F / cards : cards == 3 ? 0.57F : 0.46F;
		for (int i = 0; i < cards; i++) {
			out[i] = focusIndex < 0 || i == focusIndex ? share : (1.0F - share) / (cards - 1);
		}
		return out;
	}

	private int[] rects(float[] weights) {
		float p = unlocked ? 0.0F : ease(holdProgress());
		int margin = Math.round(this.width * 0.04F);
		float last = Math.min(1.0F, weights[3] * 4.0F);
		float usable = this.width - margin * 2 - GAP * (2.0F + last);
		float sum = 0.0F;
		for (float w : weights) {
			sum += w;
		}
		int[] out = new int[8];
		float x = margin - p * 28.0F;
		for (int i = 0; i < 4; i++) {
			float w = sum <= 0.0F ? 0.0F : weights[i] / sum * usable;
			out[i * 2] = Math.round(x);
			out[i * 2 + 1] = Math.round(x + w);
			x += w + (i < 2 ? GAP : i == 2 ? GAP * last : 0.0F);
		}
		return out;
	}

	private int[] layout() {
		return rects(weight);
	}

	private int cardAt(double mx, double my, int[] r, int cards) {
		if (my < cardTop() || my > cardBottom()) {
			return -1;
		}
		for (int i = 0; i < cards; i++) {
			if (r[i * 2 + 1] - r[i * 2] > 8 && mx >= r[i * 2] && mx < r[i * 2 + 1]) {
				return i;
			}
		}
		return -1;
	}

	private boolean inGroup(double mx, double my, int[] r, int cards) {
		return my >= cardTop() && my <= cardBottom() && mx >= r[0] && mx < r[(cards - 1) * 2 + 1];
	}

	private int stableWidth(int index, int cards) {
		int[] r = rects(targetWeights(cards, index));
		return r[index * 2 + 1] - r[index * 2];
	}

	private Presets.Preset presetAt(int index) {
		return index < 3 ? normal.get(Math.min(index, normal.size() - 1)) : hardcore;
	}

	private void updateFocus(int mouseX, int mouseY, long now) {
		if (mouseX != lastMouseX || mouseY != lastMouseY) {
			if (lastMouseX >= 0.0) {
				mouseLive = true;
			}
			lastMouseX = mouseX;
			lastMouseY = mouseY;
		}
		if (chosen != null || !mouseLive) {
			return;
		}
		int cards = count();
		int[] target = rects(targetWeights(cards, focus));
		int under = cardAt(mouseX, mouseY, target, cards);
		if (under < 0 && focus >= 0 && inGroup(mouseX, mouseY, target, cards)) {
			pending = -1;
			leftSince = -1L;
			return;
		}
		if (under < 0) {
			pending = -1;
			if (focus >= 0) {
				if (leftSince < 0L) {
					leftSince = now;
				} else if (now - leftSince >= LEAVE_NANOS) {
					focus = -1;
					leftSince = -1L;
				}
			}
			return;
		}
		leftSince = -1L;
		if (under == focus) {
			pending = -1;
		} else if (focus < 0) {
			focus = under;
			pending = -1;
		} else if (pending != under) {
			pending = under;
			pendingSince = now;
		} else if (now - pendingSince >= INTENT_NANOS) {
			focus = under;
			pending = -1;
		}
	}

	private void animate(long now) {
		float dt = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
		lastNanos = now;
		float[] target = targetWeights(count(), focus);
		boolean revealing = ringStart >= 0L && now - ringStart < 900_000_000L;
		float omega = revealing ? 14.0F : 19.0F;
		int steps = Math.max(1, (int) Math.ceil(dt / 0.008F));
		float h = dt / steps;
		for (int s = 0; s < steps; s++) {
			for (int i = 0; i < 4; i++) {
				weightVel[i] += (omega * omega * (target[i] - weight[i]) - 2.0F * omega * weightVel[i]) * h;
				weight[i] = Math.max(0.0F, weight[i] + weightVel[i] * h);
				float liftTarget = focus == i ? 1.0F : 0.0F;
				liftVel[i] += (omega * omega * (liftTarget - lift[i]) - 2.0F * omega * liftVel[i]) * h;
				lift[i] = Math.max(0.0F, Math.min(1.0F, lift[i] + liftVel[i] * h));
			}
		}
		float k = 1.0F - (float) Math.exp(-dt * 0.6931F / 0.07F);
		for (int i = 0; i < 4; i++) {
			float dimTarget = chosen != null ? (i == chosenIndex ? 0.0F : 1.0F) : focus >= 0 && focus != i ? 1.0F : 0.0F;
			dim[i] += (dimTarget - dim[i]) * k;
		}
	}

	private static List<String> wrap(Font font, String text, int width, java.util.function.Function<String, Component> measure) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String tryLine = line.isEmpty() ? word : line + " " + word;
			if (LuceatFonts.width(font, measure.apply(tryLine)) > width && !line.isEmpty()) {
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

	private static String[] split(String blurb) {
		int cut = blurb.lastIndexOf(" - ");
		return cut < 0 ? new String[] {blurb, ""} : new String[] {blurb.substring(0, cut), blurb.substring(cut + 3)};
	}

	private static String title(String name) {
		return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	private static int inset(int radius, int y, int top, int bottom) {
		float dy;
		if (y < top + radius) {
			dy = top + radius - y - 0.5F;
		} else if (y >= bottom - radius) {
			dy = y - (bottom - radius) + 0.5F;
		} else {
			return 0;
		}
		if (dy >= radius) {
			return radius;
		}
		return (int) Math.ceil(radius - Math.sqrt((double) radius * radius - (double) dy * dy));
	}

	private static void blitRounded(GuiGraphicsExtractor graphics, Identifier tex, int x0, int y0, int x1, int y1, int radius, float u0, float u1, float v0, float v1) {
		float w = x1 - x0;
		float h = y1 - y0;
		int r = Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5F;
			int cut = Math.round(r - (float) Math.sqrt(Math.max(0.0F, r * r - dy * dy)));
			if (x1 - cut <= x0 + cut) {
				continue;
			}
			float ua = u0 + (u1 - u0) * cut / w;
			float ub = u0 + (u1 - u0) * (w - cut) / w;
			float va = v0 + (v1 - v0) * i / h;
			float vb = v0 + (v1 - v0) * (i + 1) / h;
			graphics.blit(tex, x0 + cut, y0 + i, x1 - cut, y0 + i + 1, ua, ub, va, vb);
			float vc = v0 + (v1 - v0) * (h - i - 1) / h;
			float vd = v0 + (v1 - v0) * (h - i) / h;
			graphics.blit(tex, x0 + cut, y1 - i - 1, x1 - cut, y1 - i, ua, ub, vc, vd);
		}
		float vm0 = v0 + (v1 - v0) * r / h;
		float vm1 = v0 + (v1 - v0) * (h - r) / h;
		graphics.blit(tex, x0, y0 + r, x1, y1 - r, u0, u1, vm0, vm1);
	}

	private static final float PILL_SCALE = 1.15F;
	private static final float NAME_SCALE = 1.35F;
	private static final float QUOTE_SCALE = 1.2F;

	private int pillWidth(Component text, Icons.Icon icon) {
		return 22 + (icon != null ? 15 : 0) + Math.round(LuceatFonts.width(this.font, text) * PILL_SCALE);
	}

	private void drawScaled(GuiGraphicsExtractor graphics, Component text, float x, float y, float scale, int colour, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		LuceatFonts.draw(graphics, this.font, text, 0, 0, colour, shadow);
		graphics.pose().popMatrix();
	}

	private int pill(GuiGraphicsExtractor graphics, int x, int y, Component text, Icons.Icon icon, int fill, int border, int ink) {
		int w = pillWidth(text, icon);
		Rounded.draw(graphics, x, y, x + w, y + 22, 11, fill, border);
		int tx = x + 11;
		if (icon != null) {
			Icons.sprite(graphics, tx, y + 4, 14, icon, ink);
			tx += 15;
		}
		drawScaled(graphics, text, tx, y + 6, PILL_SCALE, ink, false);
		return w;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		long now = System.nanoTime();
		updateFocus(mouseX, mouseY, now);
		animate(now);
		super.extractRenderState(graphics, mouseX, mouseY, partial);
		PresetFilmstrips.step();
		if (holding && holdProgress() >= 1.0F && hardcore != null && !unlocked) {
			unlock(now);
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
		float headIn = ease((now - openAt) / 500_000_000.0F);
		float subIn = ease((now - openAt - 180_000_000L) / 500_000_000.0F);
		int headY = Math.max(10, Math.round(this.height * 0.05F));
		if (headIn > 0.03F) {
			LuceatFonts.centred(graphics, this.font, LuceatFonts.title("How much light do you want?"), this.width / 2, headY - Math.round((1.0F - headIn) * 8.0F), alpha(LuceatLook.TEXT, Math.round(255.0F * headIn)), true);
		}
		if (subIn > 0.03F) {
			LuceatFonts.centred(graphics, this.font, LuceatFonts.body("You can change this any time in the settings"), this.width / 2, headY + 16 - Math.round((1.0F - subIn) * 8.0F), alpha(LuceatLook.SOFT, Math.round(255.0F * subIn)), true);
		}

		int[] layout = layout();
		int top = cardTop();
		int bottom = cardBottom();
		int height = bottom - top;
		int[] settledRects = rects(targetWeights(count(), focus));
		int[] settledWidths = new int[4];
		for (int k = 0; k < 4; k++) {
			settledWidths[k] = settledRects[k * 2 + 1] - settledRects[k * 2];
		}
		LuceatConfig live = LuceatConfig.get();
		for (int i = 0; i < 4; i++) {
			int x0 = layout[i * 2];
			int x1 = layout[i * 2 + 1];
			int w = x1 - x0;
			if (w < 24 || i == 3 && hardcore == null) {
				continue;
			}
			Presets.Preset preset = presetAt(i);
			int colour = accent(preset);
			boolean hot = i == 3;
			boolean gold = preset.name().equalsIgnoreCase(recommended);
			boolean current = preset.name().equalsIgnoreCase(live.preset) && live.presetChosen;
			float h = q(lift[i]);
			float inCard = ease((now - openAt - 120_000_000L - i * 110_000_000L) / 600_000_000.0F);
			int rise = Math.round((1.0F - inCard) * 14.0F);
			int y0 = top + rise;
			int y1 = bottom + rise;
			int r = Math.min(RADIUS, w / 2);
			PresetFilmstrips.Strip strip = PresetFilmstrips.of(preset);
			Identifier tex = strip.texture(now);
			if (tex != null) {
				float aspect = w / (float) height;
				float u0 = 0.0F;
				float u1 = 1.0F;
				float v0 = 0.0F;
				float v1 = 1.0F;
				if (aspect < IMAGE_ASPECT) {
					float visible = aspect / IMAGE_ASPECT;
					float centre = Math.max(visible / 2.0F, Math.min(1.0F - visible / 2.0F, 0.56F));
					u0 = centre - visible / 2.0F;
					u1 = centre + visible / 2.0F;
				} else {
					float visible = IMAGE_ASPECT / aspect;
					v0 = (1.0F - visible) * 0.55F;
					v1 = v0 + visible;
				}
				blitRounded(graphics, tex, x0, y0, x1, y1, r, u0, u1, v0, v1);
			} else {
				Rounded.draw(graphics, x0, y0, x1, y1, r, 0xFF120A24, 0);
				Rounded.draw(graphics, x0, y0, x1, y1, r, alpha(colour, 56), 0);
			}
			float veil = q(1.0F - inCard);
			if (veil > 0.0F) {
				Rounded.draw(graphics, x0, y0, x1, y1, r, alpha(0x080511, Math.round(veil * 255.0F)), 0);
			}
			int scrimH = Math.round(height * 0.55F);
			for (int ys = y1 - scrimH; ys < y1; ys += 3) {
				float t = (ys - (y1 - scrimH)) / (float) scrimH;
				int cut = inset(r, Math.min(ys + 2, y1 - 1), y0, y1);
				graphics.fill(x0 + cut, ys, x1 - cut, Math.min(ys + 3, y1), alpha(0x06030E, Math.round(190.0F * t * t)));
			}
			float dimmed = q(dim[i]);
			if (dimmed > 0.0F) {
				Rounded.draw(graphics, x0, y0, x1, y1, r, alpha(0x06030E, Math.round(dimmed * 72.0F)), 0);
			}
			if (h > 0.0F || hot) {
				embers(graphics, i, x0, y0, x1, y1, colour, hot ? 1.0F : h, now);
			}
			int rim;
			if (hot) {
				float swing = q((float) (0.5 + 0.5 * Math.sin(now / 1.2E9 + 1.0)));
				rim = LuceatLook.mix(0xB0BE3FFF, 0xE0F4EAD2, swing);
			} else if (current) {
				rim = 0xCCC66BFF;
			} else {
				rim = LuceatLook.mix(0x58E4D6FF, alpha(colour, 210), h);
			}
			int rimAlpha = Math.min(255, Math.round((rim >>> 24) / 16.0F) * 16);
			Rounded.draw(graphics, x0, y0, x1, y1, r, 0, (rimAlpha << 24) | (rim & 0xFFFFFF));

			int pillX = x0 + 14;
			int pillY = y0 + 14;
			if (gold) {
				pillX += pill(graphics, pillX, pillY, LuceatFonts.body("Recommended for you"), Icons.STAR, CREAM, 0, Glass.INK) + 6;
			}
			Component cost = LuceatFonts.body(preset.cost());
			int costW = pillWidth(cost, null);
			int costX = x1 - 14 - costW;
			float costIn = q((costX - pillX - 4) / 16.0F);
			if (costIn > 0.0F) {
				pill(graphics, costX, y0 + 14, cost, null, fadeArgb(0x88120A28, costIn), fadeArgb(0x70E4D6FF, costIn), fadeArgb(LuceatLook.TEXT, costIn));
			}

			float out = chosen == null ? 0.0F : Math.min(1.0F, (now - chosenAt) / 300_000_000.0F);
			int textAlpha = Math.round(255.0F * inCard * (1.0F - out * out * out));
			int nameY = y1 - 36;
			graphics.enableScissor(x0 + 2, y0, x1 - 2, y1);
			if (textAlpha > 8) {
				Component name = LuceatFonts.preset(preset.name(), title(preset.name()));
				drawScaled(graphics, name, x0 + 18, nameY, NAME_SCALE, alpha(LuceatLook.TEXT, textAlpha), true);
				if (current) {
					Component here = LuceatFonts.body("Current");
					int hereW = pillWidth(here, Icons.CHECK);
					int nameEnd = x0 + 18 + Math.round(LuceatFonts.width(this.font, name) * NAME_SCALE) + 8;
					int hereX = x1 - 14 - hereW;
					int hereY = y1 - 14 - 22;
					if (x0 + settledWidths[i] - 14 - hereW < nameEnd) {
						hereX = x0 + 14;
						hereY = y0 + 14 + (gold ? 28 : 0);
					}
					pill(graphics, hereX, hereY, here, Icons.CHECK, 0x88120A28, 0xCCC66BFF, LuceatLook.TEXT);
				}
				float quoteIn = ease((lift[i] - 0.35F) / 0.45F);
				if (quoteIn > 0.02F) {
					String[] parts = split(preset.blurb());
					java.util.function.Function<String, Component> face = chunk -> LuceatFonts.preset(preset.name(), chunk);
					List<String> lines = wrap(this.font, "“" + parts[0] + "”", Math.round((stableWidth(i, count()) - 36) / QUOTE_SCALE), face);
					int blockH = lines.size() * 15 + (parts[1].isEmpty() ? 0 : 17);
					int qy = nameY - 12 - blockH;
					int qa = Math.round(textAlpha * quoteIn);
					for (String line : lines) {
						drawScaled(graphics, face.apply(line), x0 + 18, qy, QUOTE_SCALE, alpha(LuceatLook.TEXT, qa), false);
						qy += 15;
					}
					if (!parts[1].isEmpty()) {
						drawScaled(graphics, face.apply(parts[1]), x0 + 36, qy + 2, QUOTE_SCALE, alpha(LuceatLook.mix(LuceatLook.SOFT, colour, 0.5F), qa), false);
					}
				}
			}
			graphics.disableScissor();
			if (chosen != null && chosenIndex == i) {
				float t = Math.min(1.0F, (now - chosenAt) / 380_000_000.0F);
				int grow = Math.round(t * 44.0F);
				Rounded.draw(graphics, x0 - grow, y0 - grow, x1 + grow, y1 + grow, RADIUS, 0, alpha(colour, Math.round(q(1.0F - t) * 200.0F)));
			}
		}
		drawFissure(graphics, now);
		drawFooter(graphics);
	}

	private void embers(GuiGraphicsExtractor graphics, int index, int x0, int y0, int x1, int y1, int colour, float amount, long now) {
		double t = now / 1.0E9;
		int w = x1 - x0;
		int h = y1 - y0;
		for (int k = 0; k < 7; k++) {
			float seed = hash(index, k);
			float spread = hash(k, index + 9);
			double phase = (t * (0.12 + 0.1 * seed) + seed) % 1.0;
			int px = x0 + 14 + Math.round(spread * Math.max(1, w - 28));
			int py = y1 - 20 - (int) (phase * h * 0.55);
			float a = q((float) Math.sin(phase * Math.PI) * 0.8F * amount);
			if (a <= 0.0F) {
				continue;
			}
			Rounded.draw(graphics, px, py, px + 4, py + 4, 2, alpha(k % 2 == 0 ? colour : CREAM, Math.round(a * 230.0F)), 0);
		}
	}

	private void drawFissure(GuiGraphicsExtractor graphics, long now) {
		if (hardcore == null) {
			return;
		}
		int mid = this.height / 2;
		if (unlocked) {
			if (ringStart >= 0L && now - ringStart < RING_NANOS) {
				float t = (now - ringStart) / (float) RING_NANOS;
				int grow = Math.round(ease(t) * this.height * 0.3F);
				int x = this.width - 6;
				Rounded.draw(graphics, x - grow, mid - 60 - grow, x + 4, mid + 60 + grow, RADIUS, 0, alpha(MAGENTA, Math.round(q(1.0F - t) * 190.0F)));
				if (now - ringStart < 60_000_000L) {
					Rounded.draw(graphics, this.width - 36, mid - this.height / 3, this.width, mid + this.height / 3, 16, alpha(CREAM, 40), 0);
				}
			}
			return;
		}
		float p = ease(holdProgress());
		float pulse = holding ? 1.0F : 0.3F + 0.1F * (float) Math.sin(now / 1.0E9 * Math.PI * 2.0 * 0.4);
		int half = 18 + Math.round(p * (this.height / 2 - 90));
		int width = 2 + Math.round(p * 3.0F);
		int x = this.width - 8 - Math.round(p * 6.0F);
		float strength = Math.min(1.0F, holding ? 0.35F + 0.65F * p : pulse);
		for (int g = 3; g >= 1; g--) {
			int gw = width + g * 4;
			Rounded.draw(graphics, x - gw / 2, mid - half - g * 2, x + width + gw / 2, mid + half + g * 2, gw / 2 + 1, alpha(MAGENTA, Math.round(q(strength * 0.22F * (4 - g)) * 255.0F)), 0);
		}
		Rounded.draw(graphics, x, mid - half, x + width, mid + half, Math.max(1, width / 2), alpha(MAGENTA, Math.round(q(strength) * 255.0F)), 0);
		if (p > 0.45F) {
			Rounded.draw(graphics, x + width / 2 - 1, mid - half + 4, x + width / 2 + 1, mid + half - 4, 1, alpha(CREAM, Math.round(q((p - 0.45F) / 0.55F) * 255.0F)), 0);
		}
	}

	private void drawFooter(GuiGraphicsExtractor graphics) {
		String gpu = HardwareHint.gpuName();
		Component skip = LuceatFonts.body("Esc to skip");
		Component name = LuceatFonts.body(gpu);
		int skipW = LuceatFonts.width(this.font, skip);
		int nameW = gpu.isEmpty() ? 0 : LuceatFonts.width(this.font, name) + 20;
		int w = 24 + nameW + skipW;
		int x0 = (this.width - w) / 2;
		int y0 = this.height - 34;
		Rounded.draw(graphics, x0, y0, x0 + w, y0 + 22, 11, 0x66120A28, 0x50E4D6FF);
		int tx = x0 + 12;
		if (!gpu.isEmpty()) {
			LuceatFonts.draw(graphics, this.font, name, tx, y0 + 7, LuceatLook.SOFT, false);
			tx += nameW - 10;
			graphics.fill(tx, y0 + 5, tx + 1, y0 + 17, 0x44E4D6FF);
			tx += 10;
		}
		LuceatFonts.draw(graphics, this.font, skip, tx, y0 + 7, LuceatLook.MUTED, false);
	}

	private boolean inCrackZone(double mx, double my) {
		return !unlocked && hardcore != null && mx >= this.width - 28 && my >= 60 && my <= this.height - 60;
	}

	private void unlock(long now) {
		holding = false;
		unlocked = true;
		ringStart = now;
		focus = -1;
		PresetFilmstrips.of(hardcore);
	}

	private void choose(int index) {
		if (chosen != null || index < 0 || index >= count()) {
			return;
		}
		chosen = presetAt(index);
		chosenIndex = index;
		chosenAt = System.nanoTime();
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
		int index = cardAt(mx, my, layout(), count());
		if (index >= 0) {
			choose(index);
			return true;
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
		int key = event.key();
		int cards = count();
		if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
			mouseLive = false;
			int next = focus < 0 ? (key == GLFW.GLFW_KEY_LEFT ? cards - 1 : 0) : focus + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
			focus = Math.max(0, Math.min(cards - 1, next));
			pending = -1;
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (focus >= 0) {
				choose(focus);
			}
			return true;
		}
		if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
			choose(key - GLFW.GLFW_KEY_1);
			return true;
		}
		if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_4) {
			choose(key - GLFW.GLFW_KEY_KP_1);
			return true;
		}
		return super.keyPressed(event);
	}
}
