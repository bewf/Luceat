package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.gui.glass.Glass;
import me.bewf.luceat.client.gui.glass.Icons;
import me.bewf.luceat.client.gui.glass.Paint;
import me.bewf.luceat.client.gui.glass.Pane;
import me.bewf.luceat.client.gui.glass.Surface;
import me.bewf.luceat.client.gui.glass.Texts;
import me.bewf.luceat.client.gui.setting.Cost;
import me.bewf.luceat.client.gui.setting.LuceatSettings;
import me.bewf.luceat.client.gui.setting.Setting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SpeedTestPage {
	private record Row(String label, String section, float millis, float percent) {
	}

	private static final Surface.Role BODY = Surface.Role.BODY;
	private static final Surface.Role TITLE = Surface.Role.TITLE;
	private static final Surface.Role HEADING = Surface.Role.HEADING;
	private static final int ROW = 26;

	private static List<Row> last = List.of();
	private static Row lastMod;
	private static String stoppedNote = "";

	private final PerfTest perf = new PerfTest();
	private final List<String> skipped = new ArrayList<>();
	private final Pane pane = new Pane();
	private final List<Setting> queue = new ArrayList<>();
	private final List<Setting> all = new ArrayList<>();
	private boolean running;
	private int total;
	private String note = "";
	private float scroll;
	private float scrollGoal;
	private int contentHeight;
	private int viewHeight;
	private int listTop;
	private int listBottom;
	private int barX;
	private boolean barDrag;
	private int barGrab;
	private int barThumbTop;
	private int barThumbHeight;
	private double fps;
	private long lastFrame;
	private int seenFrames;
	private long clockSeen;
	private int signature;
	private int[] startBox = new int[4];
	private int[] copyBox = new int[4];
	private long copiedUntil;

	public void tick() {
		if (!running || perf.running()) {
			return;
		}
		next();
	}

	public void close() {
		if (running) {
			stoppedNote = "The test stopped because you left this page. Your settings are back to normal.";
		}
		stop();
		pane.release();
	}

	private void begin() {
		if (running) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			note = "Open a world first, then run the test.";
			return;
		}
		if (minecraft.options.enableVsync().get()) {
			note = "Turn VSync off in the video settings first. With it on, small changes don't show.";
			return;
		}
		all.clear();
		queue.clear();
		for (Setting setting : LuceatSettings.create()) {
			if (setting.testable()) {
				all.add(setting);
				queue.add(setting);
			}
		}
		perf.forget();
		skipped.clear();
		total = queue.size();
		note = "";
		stoppedNote = "";
		running = total > 0;
		scroll = 0.0F;
		scrollGoal = 0.0F;
		if (!running) {
			note = "Nothing to test.";
		}
	}

	private void next() {
		if (queue.isEmpty()) {
			finish();
			return;
		}
		while (!queue.isEmpty()) {
			Setting setting = queue.remove(0);
			perf.start(setting);
			if (perf.running()) {
				return;
			}
			if (!perf.skippable()) {
				String reason = perf.note();
				note = reason.isEmpty() ? "The test could not start." : reason;
				stop();
				return;
			}
			skipped.add(setting.label());
			total--;
		}
		finish();
	}

	private void stop() {
		try {
			perf.cancel();
		} finally {
			queue.clear();
			running = false;
		}
	}

	private void finish() {
		running = false;
		last = settingRows();
		lastMod = modRow();
		note = skipped.isEmpty() ? "" : "Skipped, off or unsupported here: " + String.join(", ", skipped);
	}

	private Row modRow() {
		for (Setting setting : all) {
			PerfTest.Result result = perf.result(setting);
			if (result != null && setting.id().equals("Luceat")) {
				return new Row("Luceat", setting.section(), result.millis(), result.percent());
			}
		}
		return null;
	}

	private List<Row> settingRows() {
		List<Row> rows = new ArrayList<>();
		for (Setting setting : all) {
			PerfTest.Result result = perf.result(setting);
			if (result != null && !setting.id().equals("Luceat")) {
				rows.add(new Row(setting.label(), setting.section(), result.millis(), result.percent()));
			}
		}
		rows.sort(Comparator.comparingDouble((Row row) -> row.percent()).reversed());
		return rows;
	}

	private List<Row> rows() {
		return running ? settingRows() : last;
	}

	private Row mod() {
		return running ? modRow() : lastMod;
	}

	public boolean click(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		if (inside(mx, my, startBox)) {
			if (running) {
				stop();
			} else {
				begin();
			}
			return true;
		}
		if (!running && !last.isEmpty() && inside(mx, my, copyBox)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(plainReport());
			copiedUntil = System.currentTimeMillis() + 2200L;
			return true;
		}
		int max = Math.max(0, contentHeight - viewHeight);
		if (max > 0 && mx >= barX - 4 && mx <= barX + 10 && my >= listTop && my <= listBottom) {
			if (my >= barThumbTop && my <= barThumbTop + barThumbHeight) {
				barGrab = (int) (my - barThumbTop);
			} else {
				barGrab = barThumbHeight / 2;
				dragBar(my);
			}
			barDrag = true;
			return true;
		}
		return false;
	}

	public boolean drag(double my) {
		if (!barDrag) {
			return false;
		}
		dragBar(my);
		return true;
	}

	public boolean release() {
		boolean was = barDrag;
		barDrag = false;
		return was;
	}

	private void dragBar(double my) {
		int max = Math.max(0, contentHeight - viewHeight);
		int span = Math.max(1, listBottom - listTop - barThumbHeight);
		float fraction = (float) ((my - barGrab - listTop) / span);
		scrollGoal = Math.max(0.0F, Math.min(1.0F, fraction)) * max;
		scroll = scrollGoal;
	}

	private static boolean inside(double mx, double my, int[] box) {
		return mx >= box[0] && mx < box[2] && my >= box[1] && my < box[3];
	}

	public boolean scroll(double mx, double my, double amount) {
		int max = Math.max(0, contentHeight - viewHeight);
		scrollGoal = Math.max(0.0F, Math.min(max, scrollGoal - (float) amount * 34.0F));
		return true;
	}

	public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		if (running) {
			perf.frame();
		}
		long now = System.nanoTime();
		int clock = me.bewf.luceat.client.light.FrameClock.count();
		if (clock != seenFrames) {
			seenFrames = clock;
			clockSeen = now;
			double instant = 1000.0 / Math.max(0.01, me.bewf.luceat.client.light.FrameClock.millis());
			fps = fps == 0.0 ? instant : fps + (instant - fps) * 0.25;
		} else if (now - clockSeen > 500_000_000L && lastFrame != 0L) {
			double instant = 1.0E9 / Math.max(1L, now - lastFrame);
			fps = fps == 0.0 ? instant : fps + (instant - fps) * 0.1;
		}
		lastFrame = now;

		float gap = scrollGoal - scroll;
		boolean easing = Math.abs(gap) >= 0.4F;
		scroll = easing ? scroll + gap * (1.0F - (float) Math.pow(0.5, 0.016 / 0.045)) : scrollGoal;

		List<Row> rows = rows();
		Row mod = mod();
		boolean copied = System.currentTimeMillis() < copiedUntil;
		int gs = GuiFit.raster();
		int sig = Objects.hash(x, y, w, h, gs, running, perf.progress(), queue.size(), rows.size(), mod == null ? 0 : mod.millis(), rows.isEmpty() ? 0 : rows.get(0).millis(), Math.round(scroll), (int) (fps / 4), note, copied,
			inside(mouseX, mouseY, startBox), inside(mouseX, mouseY, copyBox), barDrag, perf.target() == null ? "" : perf.target().id());
		if (sig != signature || !pane.matches(x, y, w, h, gs)) {
			signature = sig;
			pane.begin(x, y, w, h, gs);
			paint(pane, x, y, w, h, mouseX, mouseY, rows, mod, copied);
			pane.finish();
		}
		pane.draw(g);
	}

	private void paint(Surface s, int x, int y, int w, int h, int mouseX, int mouseY, List<Row> rows, Row mod, boolean copied) {
		int x0 = x + 4;
		int x1 = x + w - 16;
		int y0 = y + 2;
		s.text(HEADING, "Speed test", x0, y0 + 8, Glass.TEXT);

		String startLabel = running ? "Stop" : "Start";
		int startW = Texts.width(BODY, startLabel) + 44;
		startBox = new int[] {x1 - startW, y0, x1, y0 + 26};
		boolean startOver = inside(mouseX, mouseY, startBox);
		Glass.button(s, startBox[0], startBox[1], startBox[2], startBox[3], !running, startOver ? 1.0F : 0.0F, 0.0F);
		Icons.draw(s, startBox[0] + 13, startBox[1] + 8, running ? Icons.CLOSE : Icons.BOLT, running ? Glass.TEXT : Glass.INK);
		s.text(BODY, startLabel, startBox[0] + 30, startBox[1] + 9, running ? Glass.TEXT : Glass.INK);

		boolean canCopy = !running && !last.isEmpty();
		String copyLabel = copied ? "Copied" : "Copy results";
		int copyW = Texts.width(BODY, "Copy results") + 44;
		copyBox = canCopy ? new int[] {startBox[0] - 8 - copyW, y0, startBox[0] - 8, y0 + 26} : new int[4];
		if (canCopy) {
			boolean over = inside(mouseX, mouseY, copyBox);
			Glass.button(s, copyBox[0], copyBox[1], copyBox[2], copyBox[3], false, over ? 1.0F : 0.0F, 0.0F);
			Icons.draw(s, copyBox[0] + 13, copyBox[1] + 8, copied ? Icons.CHECK : Icons.COPY, copied ? Glass.GOOD : Glass.SOFT);
			s.text(BODY, copyLabel, copyBox[0] + 30, copyBox[1] + 9, copied ? Glass.GOOD : Glass.TEXT);
		}

		int ty = y0 + 36;
		String hardware = RenderBackend.kind().label() + ", " + Runtime.getRuntime().availableProcessors() + " threads, " + String.format(Locale.ROOT, "%.0f fps now", fps);
		s.text(BODY, hardware, x0, ty, Glass.MUTED);
		ty += 16;
		for (String line : Glass.wrap(s, BODY, "Finds which settings cost the most on this PC. It adds six test lights around you, so stand still where you can see terrain. Your settings come back afterwards.", x1 - x0, 3)) {
			s.text(BODY, line, x0, ty, Glass.SOFT);
			ty += 11;
		}
		ty += 6;

		if (running) {
			int done = total - queue.size() - (perf.running() ? 1 : 0);
			float inside = perf.progress() / 100.0F;
			float share = (Math.max(0, done) + (perf.running() ? inside : 0.0F)) / Math.max(1, total);
			Glass.slider(s, x0 + 4, ty + 5, x1 - 4, Math.min(1.0F, share), 0.0F, 0.0F);
			String name = perf.target() == null ? "" : perf.target().label();
			s.text(BODY, "Testing " + name, x0, ty + 16, Glass.MUTED);
			ty += 34;
		} else if (!note.isEmpty()) {
			s.text(BODY, Glass.fit(s, BODY, note, x1 - x0), x0, ty, Glass.WARN);
			ty += 18;
		} else if (!stoppedNote.isEmpty()) {
			s.text(BODY, Glass.fit(s, BODY, stoppedNote, x1 - x0), x0, ty, Glass.WARN);
			ty += 18;
		}

		if (mod != null) {
			int cardH = 54;
			Paint.round(s, x0, ty, x1, ty + cardH, 12, Paint.argb(110, 40, 30, 90));
			Paint.outline(s, x0, ty, x1, ty + cardH, 12, Paint.argb(80, 205, 187, 255));
			s.text(BODY, "Whole mod", x0 + 14, ty + 10, Glass.MUTED);
			s.text(TITLE, "Luceat", x0 + 14, ty + 24, Glass.TEXT);
			Cost cost = Cost.fromShare(mod.percent());
			String value = String.format(Locale.ROOT, "%.2f ms per frame, %.1f%% of a frame", mod.millis(), mod.percent());
			Glass.right(s, BODY, value, x1 - 14, ty + 12, Glass.TEXT);
			int bx0 = x0 + 120;
			int bx1 = x1 - 14;
			int by = ty + 34;
			Paint.round(s, bx0, by, bx1, by + 6, 3, Paint.argb(255, 30, 24, 52));
			int fx = bx0 + Math.round((bx1 - bx0) * Math.max(0.0F, Math.min(1.0F, mod.percent() / 30.0F)));
			if (fx > bx0 + 6) {
				Paint.round(s, bx0, by, fx, by + 6, 3, cost.colour());
			}
			Glass.right(s, BODY, cost.label(), bx0 - 10, by - 2, cost.colour());
			ty += cardH + 12;
		}

		if (!running && !rows.isEmpty() && rows.get(0).percent() >= 2.5F) {
			s.text(BODY, Glass.fit(s, BODY, "Biggest cost: " + rows.get(0).label() + ". Lower it first.", x1 - x0), x0, ty, Glass.GOOD);
			ty += 18;
		}

		listTop = ty;
		listBottom = y + h;
		viewHeight = Math.max(0, listBottom - listTop);
		if (rows.isEmpty()) {
			s.text(BODY, running ? "Measuring..." : "No results yet. Press Start.", x0, ty + 4, Glass.MUTED);
			contentHeight = 0;
			barX = x1 + 2;
			return;
		}
		float top = Math.max(0.01F, rows.get(0).percent());
		contentHeight = rows.size() * ROW;
		int max = Math.max(0, contentHeight - viewHeight);
		scrollGoal = Math.min(scrollGoal, max);
		scroll = Math.min(scroll, max);
		s.clip(x, listTop, x + w, listBottom);
		int valueWidth = Texts.width(BODY, "00.00 ms  00.0%") + 4;
		int labelWidth = Math.min(150, (x1 - x0) / 3);
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int ry = listTop + i * ROW - Math.round(scroll);
			if (ry + ROW < listTop || ry > listBottom) {
				continue;
			}
			boolean hot = i < 3 && row.percent() >= 2.5F;
			Paint.round(s, x0, ry, x1, ry + ROW - 4, 9, Paint.argb(hot ? 84 : 44, 40, 30, 90));
			if (hot) {
				Paint.outline(s, x0, ry, x1, ry + ROW - 4, 9, Paint.argb(50, 205, 187, 255));
			}
			int cy = ry + (ROW - 4) / 2;
			s.text(BODY, Glass.fit(s, BODY, row.label(), labelWidth), x0 + 12, cy - 4, hot ? Glass.TEXT : Glass.SOFT);
			int bx0 = x0 + 12 + labelWidth + 10;
			int bx1 = x1 - valueWidth - 20;
			if (bx1 > bx0 + 10) {
				Paint.round(s, bx0, cy - 3, bx1, cy + 3, 3, Paint.argb(255, 30, 24, 52));
				int fx = bx0 + Math.round((bx1 - bx0) * Math.max(0.0F, Math.min(1.0F, row.percent() / top)));
				if (fx > bx0 + 6) {
					Paint.round(s, bx0, cy - 3, fx, cy + 3, 3, Cost.fromShare(row.percent()).colour());
				}
			}
			String value = String.format(Locale.ROOT, "%.2f ms  %.1f%%", row.millis(), row.percent());
			Glass.right(s, BODY, value, x1 - 12, cy - 4, Glass.TEXT);
		}
		s.unclip();
		barX = x1 + 4;
		if (max > 0) {
			int track = listBottom - listTop;
			int thumb = Math.max(26, Math.round(track * (float) viewHeight / contentHeight));
			barThumbHeight = thumb;
			barThumbTop = listTop + Math.round((track - thumb) * (scroll / max));
			int half = barDrag ? 3 : 2;
			Paint.round(s, barX + 3 - half, barThumbTop, barX + 3 + half, barThumbTop + thumb, half, Paint.argb(barDrag ? 180 : 90, 205, 187, 255));
		}
	}

	private static String modLine() {
		return lastMod == null ? null : String.format(Locale.ROOT, "Whole mod: %.2f ms per frame, %.1f%% of a frame", lastMod.millis(), lastMod.percent());
	}

	public static List<String> chatReport() {
		List<String> lines = new ArrayList<>();
		if (last.isEmpty()) {
			lines.add("Run the speed test in the Luceat screen first.");
			return lines;
		}
		lines.add("Luceat speed test, biggest costs first:");
		if (modLine() != null) {
			lines.add(modLine());
		}
		int count = Math.min(8, last.size());
		for (int i = 0; i < count; i++) {
			Row row = last.get(i);
			lines.add(String.format(Locale.ROOT, "%d. %s (%s): %.2f ms, %.1f%% of a frame", i + 1, row.label(), row.section(), row.millis(), row.percent()));
		}
		Row first = last.get(0);
		lines.add(first.percent() >= 2.5F ? "Biggest cost: " + first.label() + ". Lower it first." : "Nothing here costs much on this PC.");
		return lines;
	}

	private String plainReport() {
		StringBuilder text = new StringBuilder("Luceat speed test\n");
		text.append(RenderBackend.kind().label()).append(", ").append(Runtime.getRuntime().availableProcessors()).append(" threads\n");
		if (modLine() != null) {
			text.append(modLine()).append('\n');
		}
		for (int i = 0; i < last.size(); i++) {
			Row row = last.get(i);
			text.append(String.format(Locale.ROOT, "%d. %s (%s): %.2f ms, %.1f%%%n", i + 1, row.label(), row.section(), row.millis(), row.percent()));
		}
		return text.toString();
	}
}
