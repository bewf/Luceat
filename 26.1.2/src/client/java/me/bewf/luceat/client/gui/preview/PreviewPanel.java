package me.bewf.luceat.client.gui.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.LuceatLook;
import me.bewf.luceat.client.gui.glass.Bar;
import me.bewf.luceat.client.gui.glass.Glass;
import me.bewf.luceat.client.gui.glass.Icons;
import me.bewf.luceat.client.gui.glass.Paint;
import me.bewf.luceat.client.gui.glass.Surface;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class PreviewPanel {
	private static final int TIME_W = 214;
	private static final float NIGHT = 0.04F;
	private static final int MAX_PIXELS = 1_800_000;
	private static final float MOVE_BUDGET_MS = 70.0F;

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
	private final PreviewWorker worker = new PreviewWorker();
	private final List<Target> targets = new ArrayList<>();
	private int serial;
	private SceneSpec spec = Scenes.fallback();
	private LuceatConfig override;
	private float time = NIGHT;
	private boolean userTime;
	private boolean draggingTime;
	private static final double YAW_MIN = -40.0;
	private static final double YAW_MAX = 40.0;
	private static final double YAW_GIVE = 14.0;
	private static final double PITCH_MIN = -14.0;
	private static final double PITCH_MAX = 16.0;
	private static final double PITCH_GIVE = 7.0;
	private boolean draggingLook;
	private double lastMouseX;
	private double lastMouseY;
	private double lookYaw;
	private double lookPitch;
	private double rawYaw;
	private double rawPitch;
	private double zoom = 1.0;
	private Target shown;
	private volatile Tracer lastTracer;
	private PreviewOccluders occluders;
	private int occluderKey;
	private Occ[] occ;
	private String occScene = "";
	private int occSerial = -1;
	private int submittedKey;
	private int submittedTier = -1;
	private boolean inFlight;
	private long lastChange;
	private int lastBase;
	private long submittedAt;
	private long lastRenderMillis;
	private float msPerPixel;
	private boolean interactive;
	private int imageX;
	private int imageY;
	private int imageW;
	private int imageH;
	private int trackX0;
	private int trackX1;
	private int trackY;

	public PreviewPanel(Font font) {
		this.font = font;
	}

	public void setSpec(SceneSpec next) {
		if (next == null) {
			next = Scenes.fallback();
		}
		if (next.scene() != spec.scene()) {
			lookYaw = 0.0;
			lookPitch = 0.0;
			rawYaw = 0.0;
			rawPitch = 0.0;
			zoom = 1.0;
		}
		this.spec = next;
		if (!userTime) {
			time = next.time() != null ? next.time() : NIGHT;
		}
	}

	public void setOverride(LuceatConfig config) {
		this.override = config;
	}

	public void setInteractive(boolean value) {
		this.interactive = value;
	}

	public boolean busy() {
		return draggingTime || draggingLook;
	}

	public void close() {
		worker.shutdown();
		for (Target target : targets) {
			target.release();
		}
		targets.clear();
		shown = null;
	}

	public int imageBottom() {
		return imageY + imageH;
	}

	public void place(int x, int y, int w, int h) {
		imageX = x;
		imageY = y;
		imageW = w;
		imageH = h;
	}

	public String sceneTitle() {
		return spec.scene().title;
	}

	public int overlayHash() {
		return Objects.hash(spec.caption(), spec.scene().id, Math.round(time * 200.0F), draggingTime, draggingLook, imageX, imageY, imageW, imageH);
	}

	public void drawImage(GuiGraphicsExtractor graphics, boolean frozen) {
		if (imageH < 60 || imageW < 60) {
			return;
		}
		Scene scene = spec.scene();
		LuceatConfig live = override != null ? override : LuceatConfig.get();
		if (!draggingLook) {
			springBack();
		}
		if (!frozen) {
			update(scene, live);
		}
		if (shown != null) {
			graphics.blit(shown.id, imageX, imageY, imageX + imageW, imageY + imageH, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	public void drawOverlay(Surface s, String note, boolean hoverTime) {
		if (imageH < 60 || imageW < 60) {
			return;
		}
		Scene scene = spec.scene();
		int fadeUnits = Math.min(imageW / 2, Math.round(Math.max(48.0F, imageW * 0.14F)));
		int left = imageX + Math.max(14, Math.round(fadeUnits * 0.5F));
		int right = imageX + imageW - 14;
		int avail = right - left;
		int rowTop = imageY + 19;
		int fullReserve = 92;
		String title = Glass.fit(s, Surface.Role.BODY, scene.title, imageW - 28 - 18 - fullReserve);
		Icons.draw(s, left, rowTop - 6, 12, Icons.CAMERA, Glass.PALE);
		s.text(Surface.Role.BODY, title, left + 18, Bar.textTop(rowTop), Glass.TEXT);
		int noteRow = rowTop + 20;
		int bottomRow = imageY + imageH - 22;
		int pillH = 28;
		int gap = 8;
		int timeLabelW = Math.max(s.width(Surface.Role.BODY, "Night"), Math.max(s.width(Surface.Role.BODY, "Evening"), s.width(Surface.Role.BODY, "Day")));
		int minTime = 12 + 14 + 10 + 36 + 14 + timeLabelW + 24;
		String[][] labelSets = {{"Scroll to zoom", "Drag to move"}, {"Zoom", "Move"}, {"", ""}};
		int hintW = 0;
		String zoomLabel = "";
		String moveLabel = "";
		for (int i = 0; i < labelSets.length; i++) {
			zoomLabel = labelSets[i][0];
			moveLabel = labelSets[i][1];
			int zoomW = zoomLabel.isEmpty() ? 0 : s.width(Surface.Role.BODY, zoomLabel) + 6;
			int moveW = moveLabel.isEmpty() ? 0 : s.width(Surface.Role.BODY, moveLabel) + 6;
			hintW = 12 + 14 + zoomW + 12 + 1 + 12 + 14 + moveW + 12;
			if (hintW + gap + minTime <= avail) {
				break;
			}
		}
		int hintRow = bottomRow;
		int timeRow = bottomRow;
		if (note != null && !note.isEmpty() && noteRow + 8 < hintRow - pillH / 2 - 6) {
			String text = Glass.fit(s, Surface.Role.BODY, note, imageW - 28 - 18);
			Icons.draw(s, left, noteRow - 6, 12, Icons.ALERT, Glass.WARN);
			s.text(Surface.Role.BODY, text, left + 18, Bar.textTop(noteRow), Glass.WARN);
		}
		Glass.pane(s, left, hintRow - pillH / 2, left + hintW, hintRow + pillH / 2, pillH / 2, 0.0F, 0.0F);
		int hx = left + 12;
		Icons.draw(s, hx, hintRow - 7, 14, Icons.MOUSE_WHEEL, Glass.SOFT);
		int divX = hx + 14 + 12;
		if (!zoomLabel.isEmpty()) {
			s.text(Surface.Role.BODY, zoomLabel, hx + 20, Bar.textTop(hintRow), Glass.SOFT);
			divX = hx + 20 + s.width(Surface.Role.BODY, zoomLabel) + 12;
		}
		Paint.vertical(s, divX, hintRow - 8, divX + 1, hintRow, 0, Paint.argb(0, 205, 187, 255), Paint.argb(60, 205, 187, 255));
		Paint.vertical(s, divX, hintRow, divX + 1, hintRow + 8, 0, Paint.argb(60, 205, 187, 255), Paint.argb(0, 205, 187, 255));
		int sx = divX + 1 + 12;
		Icons.draw(s, sx, hintRow - 7, 14, Icons.MOUSE_DRAG, Glass.SOFT);
		if (!moveLabel.isEmpty()) {
			s.text(Surface.Role.BODY, moveLabel, sx + 20, Bar.textTop(hintRow), Glass.SOFT);
		}

		int labelW = Math.max(s.width(Surface.Role.BODY, "Night"), Math.max(s.width(Surface.Role.BODY, "Evening"), s.width(Surface.Role.BODY, "Day")));
		int columnW = labelW + 22;
		int timeW = Math.max(minTime, Math.min(TIME_W, avail - hintW - gap));
		int timeX1 = right;
		int timeX0 = timeX1 - timeW;
		Glass.pane(s, timeX0, timeRow - pillH / 2, timeX1, timeRow + pillH / 2, pillH / 2, 0.0F, 0.0F);
		int columnX0 = timeX1 - columnW;
		Paint.vertical(s, columnX0, timeRow - 8, columnX0 + 1, timeRow, 0, Paint.argb(0, 205, 187, 255), Paint.argb(60, 205, 187, 255));
		Paint.vertical(s, columnX0, timeRow, columnX0 + 1, timeRow + 8, 0, Paint.argb(60, 205, 187, 255), Paint.argb(0, 205, 187, 255));
		Icons.draw(s, timeX0 + 12, timeRow - 7, 14, Icons.CLOCK, Glass.SOFT);
		trackX0 = timeX0 + 12 + 14 + 10;
		trackX1 = columnX0 - 14;
		trackY = timeRow;
		Glass.slider(s, trackX0, trackY, trackX1, time, hoverTime ? 1.0F : 0.0F, draggingTime ? 1.0F : 0.0F);
		String clock = time < 0.34F ? "Night" : time < 0.67F ? "Evening" : "Day";
		s.text(Surface.Role.BODY, clock, columnX0 + 1 + (columnW - 1 - s.width(Surface.Role.BODY, clock)) / 2, Bar.textTop(timeRow), Glass.TEXT);
	}

	public boolean overTime(double mx, double my) {
		return LuceatLook.inside(mx, my, trackX0 - 8, trackY - 10, trackX1 + 8, trackY + 10);
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

	private static int guiScale() {
		return Math.min(4, me.bewf.luceat.client.gui.GuiFit.raster());
	}

	private void update(Scene scene, LuceatConfig live) {
		int gs = guiScale();
		int ow = Math.max(64, imageW * gs);
		int oh = Math.max(48, imageH * gs);
		while ((long) ow * oh > MAX_PIXELS) {
			ow = Math.round(ow * 0.9F);
			oh = Math.round(oh * 0.9F);
		}
		PreviewWorker.Result result = worker.take();
		if (result != null && result.request().key() == submittedKey) {
			PreviewWorker.Request request = result.request();
			Target target = target(request.width(), request.height());
			NativeImage image = target.texture.getPixels();
			int[] pixels = result.pixels();
			int w = request.width();
			int h = request.height();
			for (int row = 0; row < h; row++) {
				for (int column = 0; column < w; column++) {
					image.setPixel(column, row, pixels[column + row * w]);
				}
			}
			target.texture.upload();
			shown = target;
			lastTracer = result.tracer();
			inFlight = false;
			lastRenderMillis = (System.nanoTime() - submittedAt) / 1_000_000L;
		}
		boolean fire = live.flicker > 0.0F && live.enabled && hasFire(scene);
		long bucket = fire ? System.nanoTime() / 150_000_000L : 0L;
		int base = Objects.hash(live.asJson(), scene.id, time, lookYaw, lookPitch, zoom, ow, oh, bucket);
		long now = System.nanoTime();
		if (base != lastBase) {
			lastBase = base;
			lastChange = now;
		}
		boolean moving = busy();
		int wantTier = moving ? 0 : (fire ? 1 : (now - lastChange > 220_000_000L ? 2 : 1));
		boolean changed = base != submittedKey || submittedTier < 0;
		if ((changed && (!moving || !inFlight)) || (!changed && wantTier > submittedTier && !inFlight)) {
			submit(scene, live, base, wantTier, ow, oh, gs);
		}
	}

	private static boolean hasFire(Scene scene) {
		for (Light light : scene.lights) {
			if (light.fire()) {
				return true;
			}
		}
		return false;
	}

	private void submit(Scene scene, LuceatConfig live, int base, int tier, int ow, int oh, int gs) {
		int key = PreviewOccluders.key(live);
		if (occluders == null || occluderKey != key) {
			occluders = new PreviewOccluders(live);
			occluderKey = key;
			occSerial = -1;
		}
		if (occSerial != System.identityHashCode(scene) || !occScene.equals(scene.id) || occ == null) {
			PreviewRenderer.prepare(scene);
			occ = PreviewRenderer.occluders(scene, occluders);
			occScene = scene.id;
			occSerial = System.identityHashCode(scene);
		}
		double seconds = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000.0;
		List<Light> lights = PreviewLights.build(scene, live, seconds, Double.NaN, Double.NaN, Double.NaN);
		double[] eye = scene.eye;
		double[] target = PreviewRenderer.lookTarget(scene, lookYaw, lookPitch);
		double fov = PreviewRenderer.zoomFov(scene.fov, zoom);
		float quality = tier == 2 ? 1.0F : tier == 1 ? Math.max(0.85F, moveScale(ow, oh)) : moveScale(ow, oh);
		int tw = Math.max(48, Math.round(ow * quality));
		int th = Math.max(36, Math.round(oh * quality));
		int radius = Math.round(12.0F * gs);
		int fade = Math.min(ow / 2, Math.round(Math.max(48.0F * gs, ow * 0.14F)));
		PreviewWorker.Request request = new PreviewWorker.Request(scene, live.copy(), occ, lights, eye, target, fov, time, 3.0, tw, th, ow, oh, radius, fade, base, tier);
		submittedKey = base;
		submittedTier = tier;
		submittedAt = System.nanoTime();
		inFlight = true;
		worker.submit(request);
	}

	private float moveScale(int ow, int oh) {
		if (msPerPixel <= 0.0F) {
			return 0.6F;
		}
		float fitted = (float) Math.sqrt(MOVE_BUDGET_MS / (msPerPixel * ow * oh));
		return Math.max(0.5F, Math.min(0.85F, fitted));
	}

	public long lastRenderMillis() {
		return lastRenderMillis;
	}

	public boolean mouseClicked(double mx, double my) {
		if (imageH < 60) {
			return false;
		}
		if (overTime(mx, my)) {
			draggingTime = true;
			userTime = true;
			moveTime(mx);
			return true;
		}
		if (LuceatLook.inside(mx, my, imageX, imageY, imageX + imageW, imageY + imageH)) {
			draggingLook = true;
			lastMouseX = mx;
			lastMouseY = my;
			return true;
		}
		return false;
	}

	public boolean mouseDragged(double mx, double my) {
		if (draggingTime) {
			moveTime(mx);
			return true;
		}
		if (draggingLook) {
			rawYaw = Math.max(YAW_MIN - 3.0 * YAW_GIVE, Math.min(YAW_MAX + 3.0 * YAW_GIVE, rawYaw + (mx - lastMouseX) * 0.22));
			rawPitch = Math.max(PITCH_MIN - 3.0 * PITCH_GIVE, Math.min(PITCH_MAX + 3.0 * PITCH_GIVE, rawPitch + (my - lastMouseY) * 0.15));
			lookYaw = rubber(rawYaw, YAW_MIN, YAW_MAX, YAW_GIVE);
			lookPitch = rubber(rawPitch, PITCH_MIN, PITCH_MAX, PITCH_GIVE);
			lastMouseX = mx;
			lastMouseY = my;
			return true;
		}
		return false;
	}

	public boolean mouseReleased() {
		boolean was = busy();
		draggingTime = false;
		draggingLook = false;
		return was;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!LuceatLook.inside(mx, my, imageX, imageY, imageX + imageW, imageY + imageH)) {
			return false;
		}
		zoom = Math.max(0.8, Math.min(1.8, zoom * Math.pow(1.1, amount)));
		return true;
	}

	private static double rubber(double raw, double min, double max, double give) {
		if (raw > max) {
			return max + give * (1.0 - Math.exp(-(raw - max) / (give * 4.0)));
		}
		if (raw < min) {
			return min - give * (1.0 - Math.exp(-(min - raw) / (give * 4.0)));
		}
		return raw;
	}

	private void springBack() {
		double targetYaw = Math.max(YAW_MIN, Math.min(YAW_MAX, rawYaw));
		double targetPitch = Math.max(PITCH_MIN, Math.min(PITCH_MAX, rawPitch));
		if (targetYaw == rawYaw && targetPitch == rawPitch) {
			return;
		}
		double k = 1.0 - Math.pow(0.5, me.bewf.luceat.client.gui.glass.Motion.dt() / 0.09);
		rawYaw += (targetYaw - rawYaw) * k;
		rawPitch += (targetPitch - rawPitch) * k;
		if (Math.abs(targetYaw - rawYaw) < 0.02) {
			rawYaw = targetYaw;
		}
		if (Math.abs(targetPitch - rawPitch) < 0.02) {
			rawPitch = targetPitch;
		}
		lookYaw = rubber(rawYaw, YAW_MIN, YAW_MAX, YAW_GIVE);
		lookPitch = rubber(rawPitch, PITCH_MIN, PITCH_MAX, PITCH_GIVE);
	}

	private void moveTime(double mx) {
		time = (float) Math.max(0.0, Math.min(1.0, (mx - trackX0) / Math.max(1, trackX1 - trackX0)));
	}
}
