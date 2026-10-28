package me.bewf.luceat.client.gui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.bewf.luceat.client.gui.setting.Cost;
import me.bewf.luceat.client.gui.setting.Setting;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.Minecraft;

public final class PerfTest {
	private static final int ROUNDS = 3;
	private static final int WARM_FRAMES = 12;
	private static final int SAMPLE_FRAMES = 40;
	private static final int STEPS = ROUNDS * 2;

	public record Result(float millis, float percent) {
		public Cost cost() {
			return Cost.fromShare(percent);
		}

		public String text() {
			return String.format(Locale.ROOT, "%.2f ms, %.1f%% of a frame, %s", millis, percent, cost().label().toLowerCase(Locale.ROOT));
		}
	}

	private enum Phase {
		IDLE, WARM, SAMPLE
	}

	private final Map<Setting, Result> results = new HashMap<>();
	private Setting target;
	private Object snapshot;
	private Phase phase = Phase.IDLE;
	private int step;
	private int frames;
	private double sum;
	private double totalA;
	private double totalB;
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

	public Result result(Setting setting) {
		return results.get(setting);
	}

	public void forget() {
		results.clear();
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
		String label = setting.label().toLowerCase(Locale.ROOT);
		boolean needsRays = setting.rebuildsShadows() || label.contains("shadow") || label.contains("bounce") || label.contains("shin") || label.contains("sun") || label.contains("water");
		me.bewf.luceat.client.shader.ShaderVariant variant = me.bewf.luceat.client.shader.TerrainShaderPatch.built();
		boolean needsSpread = label.contains("corner");
		if (needsSpread && (variant == null || !variant.spread())) {
			note = "Turn on light around corners first, so the shader has that code to test.";
			return;
		}
		if (needsRays && (variant == null || !variant.rays())) {
			note = "Turn on shadows, bounce or shine first, so the shader has that code to test.";
			return;
		}
		note = "";
		target = setting;
		snapshot = setting.snapshot();
		setting.useTestState(false);
		applied(setting);
		phase = Phase.WARM;
		step = 0;
		frames = 0;
		sum = 0.0;
		totalA = 0.0;
		totalB = 0.0;
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
		if (phase == Phase.WARM) {
			if (frames >= WARM_FRAMES) {
				phase = Phase.SAMPLE;
				frames = 0;
				sum = 0.0;
			}
			return;
		}
		sum += millis;
		if (frames < SAMPLE_FRAMES) {
			return;
		}
		if (step % 2 == 0) {
			totalA += sum / SAMPLE_FRAMES;
		} else {
			totalB += sum / SAMPLE_FRAMES;
		}
		step++;
		frames = 0;
		if (step >= STEPS) {
			target.restore(snapshot);
			applied(target);
			double a = totalA / ROUNDS;
			double b = totalB / ROUNDS;
			float difference = (float) Math.abs(a - b);
			float percent = (float) (difference / Math.max(Math.max(a, b), 0.001) * 100.0);
			results.put(target, new Result(difference, percent));
			note = "";
			phase = Phase.IDLE;
			return;
		}
		target.useTestState(step % 2 == 1);
		applied(target);
		phase = Phase.WARM;
	}

	public int progress() {
		if (!running()) {
			return 0;
		}
		int unit = WARM_FRAMES + SAMPLE_FRAMES;
		int done = step * unit + (phase == Phase.WARM ? frames : WARM_FRAMES + frames);
		return Math.min(100, done * 100 / (STEPS * unit));
	}

	private static void applied(Setting setting) {
		if (setting.rebuildsShadows()) {
			ShadowVolume.reset();
		}
	}
}
