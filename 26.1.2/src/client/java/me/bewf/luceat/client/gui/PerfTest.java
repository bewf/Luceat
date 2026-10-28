package me.bewf.luceat.client.gui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.bewf.luceat.client.gui.setting.Cost;
import me.bewf.luceat.client.gui.setting.Setting;
import me.bewf.luceat.client.light.FrameClock;
import me.bewf.luceat.client.light.TestLoad;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.Minecraft;

public final class PerfTest {
	private static final int ROUNDS = 2;
	private static final long WARM_NANOS = 300_000_000L;
	private static final long SAMPLE_NANOS = 700_000_000L;
	private static final int MIN_WARM = 2;
	private static final int MIN_SAMPLE = 4;
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
	private long phaseStart;
	private int seenCount;
	private long lastCall;
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
		TestLoad.set(true);
		phase = Phase.WARM;
		step = 0;
		frames = 0;
		sum = 0.0;
		totalA = 0.0;
		totalB = 0.0;
		phaseStart = System.nanoTime();
		seenCount = FrameClock.count();
		lastCall = 0L;
	}

	public void cancel() {
		if (running()) {
			target.restore(snapshot);
			applied(target);
			phase = Phase.IDLE;
			TestLoad.set(false);
		}
	}

	public void frame() {
		if (!running()) {
			return;
		}
		long now = System.nanoTime();
		double millis = -1.0;
		int count = FrameClock.count();
		if (count != seenCount) {
			int fresh = count - seenCount;
			seenCount = count;
			millis = FrameClock.millis();
			frames += fresh - 1;
			sum += millis * (fresh - 1);
			lastCall = now;
		} else if (lastCall != 0L && now - lastCall > 400_000_000L) {
			millis = (now - lastCall) / 1.0E6;
			lastCall = now;
		} else if (lastCall == 0L) {
			lastCall = now;
		}
		if (millis < 0.0) {
			return;
		}
		frames++;
		if (phase == Phase.WARM) {
			if (frames >= MIN_WARM && now - phaseStart >= WARM_NANOS) {
				phase = Phase.SAMPLE;
				frames = 0;
				sum = 0.0;
				phaseStart = now;
			}
			return;
		}
		sum += millis;
		if (frames < MIN_SAMPLE || now - phaseStart < SAMPLE_NANOS) {
			return;
		}
		double average = sum / frames;
		if (step % 2 == 0) {
			totalA += average;
		} else {
			totalB += average;
		}
		step++;
		frames = 0;
		sum = 0.0;
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
			TestLoad.set(false);
			return;
		}
		target.useTestState(step % 2 == 1);
		applied(target);
		phase = Phase.WARM;
		phaseStart = now;
	}

	public int progress() {
		if (!running()) {
			return 0;
		}
		float inStep = phase == Phase.SAMPLE ? 0.3F + 0.7F * Math.min(1.0F, (System.nanoTime() - phaseStart) / (float) SAMPLE_NANOS) : 0.3F * Math.min(1.0F, (System.nanoTime() - phaseStart) / (float) WARM_NANOS);
		return Math.min(100, Math.round((step + inStep) * 100.0F / STEPS));
	}

	private static void applied(Setting setting) {
		if (setting.rebuildsShadows()) {
			ShadowVolume.reset();
		}
	}
}
