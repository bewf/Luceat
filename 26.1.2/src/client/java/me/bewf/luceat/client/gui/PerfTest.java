package me.bewf.luceat.client.gui;

import java.util.HashMap;
import java.util.Map;

import me.bewf.luceat.client.gui.setting.Cost;
import me.bewf.luceat.client.gui.setting.Setting;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.Minecraft;

public final class PerfTest {
	private static final int WARM_FRAMES = 20;
	private static final int SAMPLE_FRAMES = 90;

	private enum Phase {
		IDLE, WARM_FIRST, SAMPLE_FIRST, WARM_SECOND, SAMPLE_SECOND
	}

	private final Map<Setting, Float> results = new HashMap<>();
	private Setting target;
	private Object snapshot;
	private Phase phase = Phase.IDLE;
	private int frames;
	private double first;
	private double second;
	private long last;
	private String note = "";

	public boolean running() {
		return phase != Phase.IDLE;
	}

	public Setting target() {
		return target;
	}

	public String note() {
		return note;
	}

	public Float result(Setting setting) {
		return results.get(setting);
	}

	public void start(Setting setting) {
		if (running()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			note = "Open a world to run a test.";
			return;
		}
		if (minecraft.options.enableVsync().get()) {
			note = "Turn VSync off in the video settings first, or the test cannot see small changes.";
			return;
		}
		note = "";
		target = setting;
		snapshot = setting.snapshot();
		phase = Phase.WARM_FIRST;
		frames = 0;
		first = 0.0;
		second = 0.0;
		last = 0L;
	}

	public void cancel() {
		if (running()) {
			target.restore(snapshot);
			applied(target);
			phase = Phase.IDLE;
		}
	}

	public void frame() {
		if (!running()) {
			return;
		}
		long now = System.nanoTime();
		if (last == 0L) {
			last = now;
			return;
		}
		double millis = (now - last) / 1.0E6;
		last = now;
		frames++;
		switch (phase) {
			case WARM_FIRST -> {
				if (frames >= WARM_FRAMES) {
					phase = Phase.SAMPLE_FIRST;
					frames = 0;
				}
			}
			case SAMPLE_FIRST -> {
				first += millis;
				if (frames >= SAMPLE_FRAMES) {
					target.useOtherState();
					applied(target);
					phase = Phase.WARM_SECOND;
					frames = 0;
				}
			}
			case WARM_SECOND -> {
				if (frames >= WARM_FRAMES) {
					phase = Phase.SAMPLE_SECOND;
					frames = 0;
				}
			}
			case SAMPLE_SECOND -> {
				second += millis;
				if (frames >= SAMPLE_FRAMES) {
					target.restore(snapshot);
					applied(target);
					float difference = (float) Math.abs(first - second) / SAMPLE_FRAMES;
					results.put(target, difference);
					note = "";
					phase = Phase.IDLE;
				}
			}
			default -> {
			}
		}
	}

	public int progress() {
		if (!running()) {
			return 0;
		}
		int total = (WARM_FRAMES + SAMPLE_FRAMES) * 2;
		int done = switch (phase) {
			case WARM_FIRST -> frames;
			case SAMPLE_FIRST -> WARM_FRAMES + frames;
			case WARM_SECOND -> WARM_FRAMES + SAMPLE_FRAMES + frames;
			case SAMPLE_SECOND -> 2 * WARM_FRAMES + SAMPLE_FRAMES + frames;
			default -> 0;
		};
		return Math.min(100, done * 100 / total);
	}

	public static String describe(float millis) {
		return String.format(java.util.Locale.ROOT, "%.2f ms, %s", millis, Cost.fromMillis(millis).label().toLowerCase());
	}

	private static void applied(Setting setting) {
		if (setting.rebuildsShadows()) {
			ShadowVolume.reset();
		}
	}
}
