package me.bewf.luceat.client.gui.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.LuceatLook;
import me.bewf.luceat.client.gui.preview.Light.Source;

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
	private static final float[] DEFAULT_COLOUR = {1.0F, 0.7F, 0.35F};
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
	private long lastHash;
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
		imageH = Math.min(h - 130, Math.round(imageW * 0.64F));
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
		float scale = interactive || draggingLight || draggingTime ? 0.5F : 1.0F;
		int tw = Math.max(32, Math.round(imageW * scale));
		int th = Math.max(24, Math.round(imageH * scale));
		LuceatConfig left = null;
		if (spec.compares()) {
			left = live.copy();
			spec.baseline().accept(left);
		}
		boolean flicker = live.flicker > 0.0F;
		long bucket = flicker ? System.nanoTime() / 70_000_000L : 0L;
		int hash = Objects.hash(live.asJson(), left == null ? "" : left.asJson(), scene.id, spec.caption(), time, lightX, lightY, lightZ, tw, th, bucket);
		if (hash == lastHash && shown != null && shown.width == tw && shown.height == th) {
			return;
		}
		long start = System.nanoTime();
		Target target = target(tw, th);
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
		for (int row = 0; row < th; row++) {
			for (int column = 0; column < tw; column++) {
				image.setPixel(column, row, pixels[column + row * tw]);
			}
		}
		target.texture.upload();
		shown = target;
		lastTracer = rightTracer;
		lastHash = hash;
		lastRenderMillis = (System.nanoTime() - start) / 1_000_000L;
	}

	public long lastRenderMillis() {
		return lastRenderMillis;
	}

	private Tracer tracer(Scene scene, LuceatConfig config, PreviewOccluders occluders) {
		Occ[] occ = new Occ[scene.cells.length];
		for (int i = 0; i < occ.length; i++) {
			occ[i] = scene.cells[i] == null ? Occ.EMPTY : occluders.of(scene.cells[i]);
		}
		List<Light> lights = lights(scene, config);
		float shadowDistance = scene.shadowScale == 1.0F ? config.shadowDistance : config.shadowDistance * scene.shadowScale;
		Params params = new Params(config.shadows, config.brightness, config.maxBrightness, config.daylightFade, config.directionalShading, config.shadowStrength, config.vanillaReplace, config.placedStrength, config.shadowSoftness * 0.45F, time, scene.vanilla, shadowDistance);
		return new Tracer(scene.width, scene.height, scene.depth, scene.voxels(), occ, lights.toArray(new Light[0]), params, scene.eye, scene.target, scene.fov);
	}

	private List<Light> lights(Scene scene, LuceatConfig config) {
		List<Light> result = new ArrayList<>();
		if (!config.enabled) {
			return result;
		}
		double seconds = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000.0;
		double maxDistance = scene.rangeScale > 0.0F ? config.lightRange * scene.rangeScale : Double.MAX_VALUE;
		int index = 0;
		for (Light light : scene.lights) {
			Light l = light;
			if (index == 0 && scene.movable) {
				if (!Double.isNaN(lightX)) {
					l = l.at(lightX, Double.isNaN(lightY) ? l.y() : lightY, lightZ);
				}
				if (scene.keepAway) {
					double cx = Math.floor(l.x()) + 0.5;
					double x = config.keepLightOutOfWalls ? Math.max(cx - 0.2, Math.min(cx + 0.2, l.x())) : Math.max(cx - 0.47, Math.min(cx + 0.47, l.x()));
					l = l.at(x, l.y(), l.z());
				}
			}
			index++;
			if (!allowed(l, config)) {
				continue;
			}
			if (l.source() == Source.HELD && scene.id.equals("held")) {
				l = l.at(l.x(), 1.0 + 1.8 * config.heldHeight - 0.8, l.z());
			}
			if (l.textured() && !config.textureColours) {
				l = l.withColour(DEFAULT_COLOUR[0], DEFAULT_COLOUR[1], DEFAULT_COLOUR[2]);
			}
			if (l.fire() && config.flicker > 0.0F) {
				double seed = (index * 37.31) % 100.0;
				double noise = 0.55 * Math.sin(seconds * 9.0 + seed) + 0.3 * Math.sin(seconds * 17.3 + seed * 1.7) + 0.15 * Math.sin(seconds * 31.0 + seed * 2.3);
				l = l.withRadius((float) Math.max(0.0, l.radius() + config.flicker * 1.3 * noise));
			}
			double dx = l.x() - scene.eye[0];
			double dy = l.y() - scene.eye[1];
			double dz = l.z() - scene.eye[2];
			if (Math.sqrt(dx * dx + dy * dy + dz * dz) > maxDistance) {
				continue;
			}
			result.add(l);
		}
		if (scene.capLights && result.size() > config.maxLights) {
			double[] eye = scene.eye;
			result.sort((a, b) -> Double.compare(distance(a, eye), distance(b, eye)));
			return new ArrayList<>(result.subList(0, Math.max(0, config.maxLights)));
		}
		return result;
	}

	private static double distance(Light light, double[] eye) {
		double dx = light.x() - eye[0];
		double dy = light.y() - eye[1];
		double dz = light.z() - eye[2];
		return dx * dx + dy * dy + dz * dz;
	}

	private static boolean allowed(Light light, LuceatConfig config) {
		if (light.wet() && config.waterPutsOutLights) {
			return false;
		}
		return switch (light.source()) {
			case HELD -> config.heldLights;
			case PLAYER -> config.otherPlayers;
			case MOB -> config.mobs;
			case DROPPED -> config.droppedItems;
			case FRAME -> config.itemFrames;
			case NATURAL -> config.naturalLights;
			case ENCHANT -> config.enchantGlow;
			case PLACED -> config.placedLights && light.radius() >= config.placedMinLevel;
			case ALWAYS -> true;
		};
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
