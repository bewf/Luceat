package me.bewf.luceat.client.gui.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.LuceatLook;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class PreviewPanel {
	private static final float NIGHT = 0.04F;

	private static final class Target {
		final Identifier id;
		final DynamicTexture texture;
		final int width;
		final int height;

		Target(Identifier id, int width, int height) {
			this.id = id;
			this.width = width;
			this.height = height;
			this.texture = new DynamicTexture("luceat preview", width, height, false);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}

		void release() {
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	private final Font font;
	private final List<Target> targets = new ArrayList<>();
	private int serial;
	private SceneSpec spec = Scenes.fallback();
	private LuceatConfig override;
	private float time = NIGHT;
	private boolean userTime;
	private boolean draggingTime;
	private boolean draggingLight;
	private double lightX = Double.NaN;
	private double lightY = Double.NaN;
	private double lightZ = Double.NaN;
	private int lastHash;
	private int lastBase;
	private long lastChange;
	private Target shown;
	private Tracer lastTracer;
	private PreviewOccluders leftOccluders;
	private PreviewOccluders rightOccluders;
	private int leftKey;
	private int rightKey;
	private boolean interactive;
	private int imageX;
	private int imageY;
	private int imageW;
	private int imageH;
	private int trackY;
	private long lastRenderMillis;

	public PreviewPanel(Font font) {
		this.font = font;
	}

	public void setSpec(SceneSpec next) {
		if (next == null) {
			next = Scenes.fallback();
		}
		if (next.scene() != spec.scene()) {
			lightX = Double.NaN;
			lightY = Double.NaN;
			lightZ = Double.NaN;
		}
		this.spec = next;
		if (!userTime) {
			time = next.time() != null ? next.time() : NIGHT;
		}
	}

	public void setOverride(LuceatConfig config) {
		this.override = config;
	}

	public boolean busy() {
		return draggingLight || draggingTime;
	}

	public void close() {
		for (Target target : targets) {
			target.release();
		}
		targets.clear();
		shown = null;
	}

	public int imageBottom() {
		return imageY + imageH;
	}

	public void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY, boolean frozen, String note) {
		LuceatLook.round(graphics, x, y, x + w, y + h, LuceatLook.SIDEBAR);
		Scene scene = spec.scene();
		graphics.text(font, "Live preview", x + 10, y + 8, LuceatLook.TEXT, false);
		String name = scene.title;
		graphics.text(font, name, x + w - 10 - font.width(name), y + 8, LuceatLook.MUTED, false);
		imageX = x + 10;
		imageY = y + 24;
		imageW = w - 20;
		imageH = Math.min(h - 130, Math.round(imageW * 0.95F));
		if (imageH < 60) {
			return;
		}
		LuceatConfig live = override != null ? override : LuceatConfig.get();
		if (!frozen) {
			render(scene, live);
		}
		if (shown != null) {
			graphics.blit(shown.id, imageX, imageY, imageX + imageW, imageY + imageH, 0.0F, 1.0F, 0.0F, 1.0F);
		}
		graphics.outline(imageX - 1, imageY - 1, imageW + 2, imageH + 2, LuceatLook.BORDER);
		if (spec.compares()) {
			int split = imageX + imageW / 2;
			graphics.fill(split, imageY, split + 1, imageY + imageH, 0xCCFFFFFF);
			tag(graphics, spec.leftLabel(), imageX + 4, imageY + 4, 0xFFFFC857);
			String right = spec.rightLabel();
			tag(graphics, right, imageX + imageW - 4 - font.width(right) - 6, imageY + 4, LuceatLook.GOOD);
		}
		if (lastTracer != null) {
			double aspect = (double) imageW / imageH;
			for (Light light : scene.lights) {
				if (light.label() == null) {
					continue;
				}
				double[] p = lastTracer.project(light.x(), light.y() + 0.35, light.z(), aspect);
				if (p == null) {
					continue;
				}
				int lx = imageX + (int) (p[0] * imageW);
				int ly = imageY + (int) (p[1] * imageH);
				int tw = font.width(light.label());
				if (lx - tw / 2 < imageX || lx + tw / 2 > imageX + imageW || ly < imageY || ly > imageY + imageH - 10) {
					continue;
				}
				graphics.text(font, light.label(), lx - tw / 2, ly - 9, 0xFFFFFFFF, true);
			}
		}
		int cursor = imageBottom() + 8;
		List<FormattedCharSequence> lines = font.split(Component.literal(spec.caption()).withStyle(Style.EMPTY.withColor(0xDDDDE6)), imageW);
		int shownLines = 0;
		for (FormattedCharSequence line : lines) {
			if (shownLines++ >= 3) {
				break;
			}
			graphics.text(font, line, imageX, cursor, 0xFFDDDDE6, false);
			cursor += 10;
		}
		cursor += 4;
		if (scene.movable) {
			graphics.text(font, "Drag in the picture to move the light. Scroll to lift it.", imageX, cursor, LuceatLook.MUTED, false);
			cursor += 14;
		}
		graphics.text(font, "Time of day", imageX, cursor, LuceatLook.SOFT, false);
		String clock = time < 0.34F ? "Night" : time < 0.67F ? "Evening" : "Day";
		graphics.text(font, clock, imageX + imageW - font.width(clock), cursor, LuceatLook.SOFT, false);
		trackY = cursor + 16;
		graphics.fill(imageX, trackY, imageX + imageW, trackY + 2, LuceatLook.TRACK);
		int filled = Math.round(imageW * time);
		graphics.fill(imageX, trackY, imageX + filled, trackY + 2, LuceatLook.ACCENT);
		LuceatLook.round(graphics, imageX + filled - 4, trackY - 4, imageX + filled + 4, trackY + 6, 0xFFE6C9FF);
		if (note != null && !note.isEmpty()) {
			int ny = trackY + 14;
			for (FormattedCharSequence line : font.split(Component.literal(note).withStyle(Style.EMPTY.withColor(LuceatLook.WARN & 0xFFFFFF)), imageW)) {
				if (ny + 10 > y + h) {
					break;
				}
				graphics.text(font, line, imageX, ny, 0xFFFFFFFF, false);
				ny += 10;
			}
		}
	}

	private void tag(GuiGraphicsExtractor graphics, String text, int x, int y, int colour) {
		int width = font.width(text) + 6;
		graphics.fill(x, y, x + width, y + 11, 0xAA000000);
		graphics.text(font, text, x + 3, y + 2, colour, false);
	}

	private Target target(int width, int height) {
		for (Target target : targets) {
			if (target.width == width && target.height == height) {
				return target;
			}
		}
		if (targets.size() >= 3) {
			Target old = targets.remove(0);
			if (old == shown) {
				shown = null;
			}
			old.release();
		}
		Target made = new Target(Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "preview/" + serial++), width, height);
		targets.add(made);
		return made;
	}

	private void render(Scene scene, LuceatConfig live) {
		boolean moving = draggingLight || draggingTime;
		int gs = guiScale();
		int ow = Math.max(64, Math.min(1200, imageW * gs));
		int oh = Math.max(48, Math.round(ow * (float) imageH / imageW));
		int finalWidth = Math.min(ow, imageW * 2);
		LuceatConfig left = null;
		if (spec.compares()) {
			left = live.copy();
			spec.baseline().accept(left);
		}
		boolean flicker = live.flicker > 0.0F;
		long bucket = flicker ? System.nanoTime() / 70_000_000L : 0L;
		int base = Objects.hash(live.asJson(), left == null ? "" : left.asJson(), scene.id, spec.caption(), time, lightX, lightY, lightZ, ow, oh, bucket);
		long now = System.nanoTime();
		if (base != lastBase) {
			lastBase = base;
			lastChange = now;
		}
		int tier = moving ? 0 : (now - lastChange > 250_000_000L ? 2 : 1);
		int hash = base * 31 + tier;
		if (hash == lastHash && shown != null && shown.width == ow && shown.height == oh) {
			return;
		}
		float quality = tier == 0 ? 0.42F : tier == 1 ? 0.7F : 1.0F;
		int tw = Math.max(48, Math.round(finalWidth * quality));
		int th = Math.max(36, Math.round(tw * (float) oh / ow));
		long start = System.nanoTime();
		Target target = target(ow, oh);
		int rightKeyNow = PreviewOccluders.key(live);
		if (rightOccluders == null || rightKey != rightKeyNow) {
			rightOccluders = new PreviewOccluders(live);
			rightKey = rightKeyNow;
		}
		int[] pixels = new int[tw * th];
		Tracer rightTracer = tracer(scene, live, rightOccluders);
		if (left != null) {
			int leftKeyNow = PreviewOccluders.key(left);
			if (leftOccluders == null || leftKey != leftKeyNow) {
				leftOccluders = new PreviewOccluders(left);
				leftKey = leftKeyNow;
			}
			Tracer leftTracer = tracer(scene, left, leftOccluders);
			int split = tw / 2;
			leftTracer.render(pixels, tw, th, 0, split);
			rightTracer.render(pixels, tw, th, split, tw);
		} else {
			rightTracer.render(pixels, tw, th);
		}
		NativeImage image = target.texture.getPixels();
		if (tw == ow && th == oh) {
			for (int row = 0; row < oh; row++) {
				for (int column = 0; column < ow; column++) {
					image.setPixel(column, row, pixels[column + row * ow]);
				}
			}
		} else {
			smoothUpscale(pixels, tw, th, image, ow, oh);
		}
		target.texture.upload();
		shown = target;
		lastTracer = rightTracer;
		lastHash = hash;
		lastRenderMillis = (System.nanoTime() - start) / 1_000_000L;
	}

	private static int guiScale() {
		try {
			return Math.max(1, Math.min(3, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale())));
		} catch (RuntimeException error) {
			return 2;
		}
	}

	// bilinear so the picture stays soft instead of showing blocky pixels
	private static void smoothUpscale(int[] src, int sw, int sh, NativeImage dst, int dw, int dh) {
		int[] columns = new int[dw];
		int[] columnsNext = new int[dw];
		float[] columnWeight = new float[dw];
		for (int x = 0; x < dw; x++) {
			float fx = Math.max(0.0F, Math.min(sw - 1.0F, (x + 0.5F) * sw / dw - 0.5F));
			int x0 = (int) fx;
			columns[x] = x0;
			columnsNext[x] = Math.min(sw - 1, x0 + 1);
			columnWeight[x] = fx - x0;
		}
		for (int y = 0; y < dh; y++) {
			float fy = Math.max(0.0F, Math.min(sh - 1.0F, (y + 0.5F) * sh / dh - 0.5F));
			int y0 = (int) fy;
			int y1 = Math.min(sh - 1, y0 + 1);
			float wy = fy - y0;
			for (int x = 0; x < dw; x++) {
				float wx = columnWeight[x];
				int a = src[columns[x] + y0 * sw];
				int b = src[columnsNext[x] + y0 * sw];
				int c = src[columns[x] + y1 * sw];
				int d = src[columnsNext[x] + y1 * sw];
				int out = 0xFF000000;
				for (int shift = 0; shift <= 16; shift += 8) {
					float top = ((a >> shift) & 255) * (1.0F - wx) + ((b >> shift) & 255) * wx;
					float bottom = ((c >> shift) & 255) * (1.0F - wx) + ((d >> shift) & 255) * wx;
					out |= Math.round(top * (1.0F - wy) + bottom * wy) << shift;
				}
				dst.setPixel(x, y, out);
			}
		}
	}

	public long lastRenderMillis() {
		return lastRenderMillis;
	}

	private Tracer tracer(Scene scene, LuceatConfig config, PreviewOccluders occluders) {
		Occ[] occ = new Occ[scene.cells.length];
		for (int i = 0; i < occ.length; i++) {
			occ[i] = scene.cells[i] == null ? Occ.EMPTY : occluders.of(scene.cells[i]);
		}
		double seconds = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000.0;
		List<Light> lights = PreviewLights.build(scene, config, seconds, lightX, lightY, lightZ);
		float shadowDistance = scene.shadowScale == 1.0F ? config.shadowDistance : config.shadowDistance * scene.shadowScale;
		Params params = new Params(config.shadows, config.brightness, config.maxBrightness, config.daylightFade, config.directionalShading, config.shadowStrength, config.vanillaReplace, config.placedStrength, config.shadowSoftness * 0.45F, time, scene.vanilla, shadowDistance, config.lightBounce, config.shine);
		return new Tracer(scene.width, scene.height, scene.depth, scene.voxels(), occ, scene.shine(), lights.toArray(new Light[0]), params, scene.eye, scene.target, scene.fov);
	}

	public void setInteractive(boolean value) {
		this.interactive = value;
	}

	public boolean mouseClicked(double mx, double my) {
		if (imageH < 60) {
			return false;
		}
		if (spec.scene().movable && LuceatLook.inside(mx, my, imageX, imageY, imageX + imageW, imageY + imageH)) {
			draggingLight = true;
			moveLight(mx, my);
			return true;
		}
		if (LuceatLook.inside(mx, my, imageX - 4, trackY - 6, imageX + imageW + 4, trackY + 12)) {
			draggingTime = true;
			userTime = true;
			moveTime(mx);
			return true;
		}
		return false;
	}

	public boolean mouseDragged(double mx, double my) {
		if (draggingLight) {
			moveLight(mx, my);
			return true;
		}
		if (draggingTime) {
			moveTime(mx);
			return true;
		}
		return false;
	}

	public boolean mouseReleased() {
		boolean was = draggingLight || draggingTime;
		draggingLight = false;
		draggingTime = false;
		return was;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		Scene scene = spec.scene();
		if (!scene.movable || !LuceatLook.inside(mx, my, imageX, imageY, imageX + imageW, imageY + imageH)) {
			return false;
		}
		Light first = scene.lights.get(0);
		double y = Double.isNaN(lightY) ? first.y() : lightY;
		lightY = Math.max(0.4, Math.min(scene.height - 0.4, y + amount * 0.2));
		if (Double.isNaN(lightX)) {
			lightX = first.x();
			lightZ = first.z();
		}
		return true;
	}

	private void moveTime(double mx) {
		time = (float) Math.max(0.0, Math.min(1.0, (mx - imageX) / imageW));
	}

	private void moveLight(double mx, double my) {
		if (lastTracer == null) {
			return;
		}
		Scene scene = spec.scene();
		Light first = scene.lights.get(0);
		double y = Double.isNaN(lightY) ? first.y() : lightY;
		double aspect = (double) imageW / imageH;
		double[] dir = lastTracer.rayDirection((mx - imageX) / imageW, (my - imageY) / imageH, aspect);
		double[] eye = lastTracer.eye();
		if (Math.abs(dir[1]) < 1.0E-4) {
			return;
		}
		double t = (y - eye[1]) / dir[1];
		if (t <= 0.0) {
			return;
		}
		lightX = Math.max(0.5, Math.min(scene.width - 0.5, eye[0] + dir[0] * t));
		lightZ = Math.max(0.5, Math.min(scene.depth - 0.5, eye[2] + dir[2] * t));
		lightY = y;
	}
}
