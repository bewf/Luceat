package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.gui.setting.LuceatSettings;
import me.bewf.luceat.client.gui.setting.Setting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SpeedTestPage {
	private record Row(String label, String section, float millis, float percent) {
	}

	private static List<Row> last = List.of();
	private static String stoppedNote = "";

	private final PerfTest perf = new PerfTest();
	private final List<Setting> queue = new ArrayList<>();
	private final List<Setting> all = new ArrayList<>();
	private boolean running;
	private int total;
	private String note = "";
	private int scroll;
	private int startX0;
	private int startY0;
	private int startX1;
	private int startY1;
	private int contentHeight;
	private int viewHeight;
	private double fps;
	private long lastFrame;
	private int seenFrames;
	private long clockSeen;

	public SpeedTestPage() {
	}

	public void tick() {
		if (!running) {
			return;
		}
		if (perf.running()) {
			return;
		}
		next();
	}

	public void close() {
		if (running) {
			stoppedNote = "The test stopped because you left this page. Your settings were put back.";
		}
		stop();
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
			note = "Turn VSync off in the video settings first, or small changes cannot be seen.";
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
		total = queue.size();
		note = "";
		stoppedNote = "";
		running = total > 0;
		scroll = 0;
		if (!running) {
			note = "Nothing to test.";
		}
	}

	private void next() {
		if (queue.isEmpty()) {
			finish();
			return;
		}
		Setting setting = queue.remove(0);
		perf.start(setting);
		if (!perf.running()) {
			String reason = perf.note();
			note = reason.isEmpty() ? "The test could not start." : reason;
			stop();
		}
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
		List<Row> rows = new ArrayList<>();
		for (Setting setting : all) {
			PerfTest.Result result = perf.result(setting);
			if (result != null) {
				rows.add(new Row(setting.label(), setting.section(), result.millis(), result.percent()));
			}
		}
		rows.sort(Comparator.comparingDouble((Row row) -> row.percent()).reversed());
		last = rows;
		note = "";
	}

	private List<Row> rows() {
		if (running) {
			List<Row> rows = new ArrayList<>();
			for (Setting setting : all) {
				PerfTest.Result result = perf.result(setting);
				if (result != null) {
					rows.add(new Row(setting.label(), setting.section(), result.millis(), result.percent()));
				}
			}
			rows.sort(Comparator.comparingDouble((Row row) -> row.percent()).reversed());
			return rows;
		}
		return last;
	}

	public boolean click(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		if (LuceatLook.inside(mx, my, startX0, startY0, startX1, startY1)) {
			if (running) {
				stop();
			} else {
				begin();
			}
			return true;
		}
		return false;
	}

	public boolean scroll(double mx, double my, double amount) {
		int max = Math.max(0, contentHeight - viewHeight);
		scroll = (int) Math.max(0, Math.min(max, scroll - amount * 14));
		return true;
	}

	public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		var font = Minecraft.getInstance().font;
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

		g.text(font, "Speed test", x + 4, y + 4, LuceatLook.ACCENT, false);
		String hardware = RenderBackend.kind().label() + ", " + Runtime.getRuntime().availableProcessors() + " threads, " + String.format(Locale.ROOT, "%.0f fps now", fps);
		g.text(font, hardware, x + 4, y + 18, LuceatLook.MUTED, false);
		g.text(font, "Finds which settings cost the most on this PC. It adds six test lights around you,", x + 4, y + 32, LuceatLook.SOFT, false);
		g.text(font, "so stand still where you can see terrain. Your settings are put back afterwards.", x + 4, y + 43, LuceatLook.SOFT, false);

		String label = running ? "Stop" : "Start";
		int bw = font.width(label) + 24;
		startX0 = x + 4;
		startY0 = y + 58;
		startX1 = startX0 + bw;
		startY1 = startY0 + 18;
		boolean over = LuceatLook.inside(mouseX, mouseY, startX0, startY0, startX1, startY1);
		int fill = running ? (over ? 0xFF8A3A3A : 0xFF6E2E2E) : (over ? LuceatLook.ACCENT : LuceatLook.ACCENT_DIM);
		LuceatLook.round(g, startX0, startY0, startX1, startY1, fill);
		g.text(font, label, startX0 + 12, startY0 + 5, LuceatLook.TEXT, false);

		int barX0 = startX1 + 10;
		int barX1 = x + w - 8;
		if (running && barX1 > barX0 + 20) {
			int done = total - queue.size() - (perf.running() ? 1 : 0);
			float inside = perf.progress() / 100.0F;
			float share = (Math.max(0, done) + (perf.running() ? inside : 0.0F)) / Math.max(1, total);
			LuceatLook.round(g, barX0, startY0 + 6, barX1, startY0 + 12, LuceatLook.TRACK);
			int fillX = barX0 + (int) ((barX1 - barX0) * Math.min(1.0F, share));
			if (fillX > barX0 + 4) {
				LuceatLook.round(g, barX0, startY0 + 6, fillX, startY0 + 12, LuceatLook.ACCENT);
			}
			String name = perf.target() == null ? "" : perf.target().label();
			g.text(font, "Testing " + name, barX0, startY0 + 16, LuceatLook.MUTED, false);
		} else if (!note.isEmpty()) {
			g.text(font, note, barX0, startY0 + 5, LuceatLook.WARN, false);
		} else if (!stoppedNote.isEmpty() && !running) {
			g.text(font, stoppedNote, barX0, startY0 + 5, LuceatLook.WARN, false);
		}

		int listTop = y + 84;
		int listBottom = y + h;
		viewHeight = Math.max(0, listBottom - listTop);
		List<Row> rows = rows();
		if (rows.isEmpty()) {
			g.text(font, running ? "Measuring..." : "No results yet. Press Start.", x + 4, listTop + 4, LuceatLook.MUTED, false);
			contentHeight = 0;
			return;
		}
		float top = Math.max(0.01F, rows.get(0).percent());
		int rowHeight = 22;
		contentHeight = rows.size() * rowHeight;
		int max = Math.max(0, contentHeight - viewHeight);
		scroll = Math.min(scroll, max);
		g.enableScissor(x, listTop, x + w, listBottom);
		int labelWidth = Math.min(150, w / 3);
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int ry = listTop + i * rowHeight - scroll;
			if (ry + rowHeight < listTop || ry > listBottom) {
				continue;
			}
			boolean hot = i < 3 && row.percent() >= 2.5F;
			LuceatLook.round(g, x + 2, ry, x + w - 2, ry + rowHeight - 2, hot ? LuceatLook.CARD_HOVER : LuceatLook.CARD);
			g.text(font, row.label(), x + 8, ry + 7, hot ? LuceatLook.TEXT : LuceatLook.SOFT, false);
			int bx0 = x + 8 + labelWidth;
			int bx1 = x + w - 120;
			if (bx1 > bx0 + 10) {
				LuceatLook.round(g, bx0, ry + 8, bx1, ry + 14, LuceatLook.TRACK);
				int fx = bx0 + (int) ((bx1 - bx0) * Math.max(0.0F, Math.min(1.0F, row.percent() / top)));
				if (fx > bx0 + 4) {
					int colour = me.bewf.luceat.client.gui.setting.Cost.fromShare(row.percent()).colour();
					LuceatLook.round(g, bx0, ry + 8, fx, ry + 14, colour);
				}
			}
			String value = String.format(Locale.ROOT, "%.2f ms  %.1f%%", row.millis(), row.percent());
			g.text(font, value, x + w - 8 - font.width(value), ry + 7, LuceatLook.TEXT, false);
		}
		g.disableScissor();
		if (!running && last == rows && !rows.isEmpty()) {
			Row first = rows.get(0);
			if (first.percent() >= 2.5F) {
				// tip is drawn in the header area so it never scrolls away
				g.text(font, "Turn down " + first.label() + " first for the biggest gain.", barX0, startY0 + 5, LuceatLook.GOOD, false);
			}
		}
	}

	public static List<String> chatReport() {
		List<String> lines = new ArrayList<>();
		if (last.isEmpty()) {
			lines.add("Run the speed test in the Luceat screen first.");
			return lines;
		}
		lines.add("Luceat speed test, biggest costs first:");
		int count = Math.min(8, last.size());
		for (int i = 0; i < count; i++) {
			Row row = last.get(i);
			lines.add(String.format(Locale.ROOT, "%d. %s (%s): %.2f ms, %.1f%% of a frame", i + 1, row.label(), row.section(), row.millis(), row.percent()));
		}
		Row first = last.get(0);
		if (first.percent() >= 2.5F) {
			lines.add("Turn down " + first.label() + " first for the biggest gain.");
		} else {
			lines.add("Nothing here costs much on this PC.");
		}
		return lines;
	}
}
