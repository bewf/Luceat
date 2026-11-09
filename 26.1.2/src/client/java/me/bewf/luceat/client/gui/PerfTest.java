package me.bewf.luceat.client.gui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.bewf.luceat.client.gui.setting.Cost;
import me.bewf.luceat.client.gui.setting.Setting;
import me.bewf.luceat.client.light.FrameClock;
import me.bewf.luceat.client.light.TestLoad;
import me.bewf.luceat.client.shadow.ShadowCache;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.Minecraft;

public final class PerfTest {
	private static final long WARM_NANOS = 220_000_000L;
	private static final long SAMPLE_NANOS = 450_000_000L;
	private static final long SAMPLE_LIMIT_NANOS = 2_500_000_000L;
	private static final long SETTLE_LIMIT_NANOS = 6_000_000_000L;
	private static final int MIN_WARM = 2;
	private static final int MIN_SAMPLE = 6;
	private static final int MAX_SAMPLE = 16;
	private static final int SETTLE_FRAMES = 3;
	private static final int STEPS = 3;

	public record Result(float millis, float percent) {
		public Cost cost() {
			return Cost.fromShare(percent);
		}

		public String text() {
			return String.format(Locale.ROOT, "%.2f ms, %.1f%% of a frame, %s", millis, percent, cost().label().toLowerCase(Locale.ROOT));
		}
	}

	private enum Phase {
		IDLE, PREPARE, WARM, SAMPLE
	}

	private final Map<Setting, Result> results = new HashMap<>();
	private Setting target;
	private boolean skip;
	private Object snapshot;
	private Phase phase = Phase.IDLE;
	private int step;
	private int frames;
	private final double[] window = new double[MAX_SAMPLE];
	private final double[] levels = new double[STEPS];
	private int windowCount;
	private double reference;
	private int attempt;
	private double firstDifference = -1.0;
	private int settleFrames;
	private int lastFilled = -1;
	private long phaseStart;
	private long prepareStart;
	private boolean prepRays;
	private boolean prepSpread;
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

	public boolean skippable() {
		return skip;
	}

	public Result result(Setting setting) {
		return results.get(setting);
	}

	public void forget() {
		results.clear();
		reference = 0.0;
	}

	public void start(Setting setting) {
		if (running()) {
			return;
		}
		skip = false;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			note = "Open a world to run a test.";
			return;
		}
		if (minecraft.options.enableVsync().get()) {
			note = "Turn VSync off in the video settings first, or the test cannot see small changes.";
			return;
		}
		String label = setting.id().toLowerCase(Locale.ROOT);
		boolean needsRays = setting.rebuildsShadows() || label.contains("shadow") || label.contains("bounce") || label.contains("shin") || label.contains("sun") || label.contains("water");
		me.bewf.luceat.client.shader.ShaderVariant wanted = me.bewf.luceat.client.shader.ShaderVariant.wanted();
		boolean needsSpread = label.contains("corner");
		if (wanted == me.bewf.luceat.client.shader.ShaderVariant.OFF) {
			note = "Luceat is not lighting the world right now, so there is nothing to test. A shader pack may be on, or Luceat is off.";
			return;
		}
		if (needsSpread && !me.bewf.luceat.client.light.LightSpread.supported()) {
			note = "Not supported here: " + me.bewf.luceat.client.light.LightSpread.blocker() + ".";
			skip = true;
			return;
		}
		if (needsSpread && !wanted.spread()) {
			note = "Turn light around corners on first.";
			skip = true;
			return;
		}
		if (needsRays && !wanted.rays()) {
			note = "Turn on shadows, bounce, shine or sun shadows first.";
			skip = true;
			return;
		}
		prepRays = needsRays;
		prepSpread = needsSpread;
		target = setting;
		snapshot = null;
		prepareStart = System.nanoTime();
		me.bewf.luceat.client.shader.VariantWatcher.rebuildNow(minecraft);
		note = "Preparing the shader.";
		step = 0;
		attempt = 0;
		firstDifference = -1.0;
		phaseStart = System.nanoTime();
		seenCount = FrameClock.count();
		lastCall = 0L;
		rewarm(phaseStart);
		phase = Phase.PREPARE;
	}

	private void rewarm(long now) {
		phase = Phase.WARM;
		phaseStart = now;
		frames = 0;
		windowCount = 0;
		settleFrames = 0;
		lastFilled = -1;
	}

	private boolean settled() {
		int filled = ShadowVolume.cellsFilled();
		boolean quiet = filled == lastFilled && !ShadowCache.busy();
		lastFilled = filled;
		settleFrames = quiet ? settleFrames + 1 : 0;
		return settleFrames >= SETTLE_FRAMES;
	}

	private void take(double millis) {
		if (windowCount < window.length) {
			window[windowCount++] = millis;
		}
	}

	private double[] summary() {
		double[] sorted = Arrays.copyOf(window, windowCount);
		Arrays.sort(sorted);
		int n = sorted.length;
		double median = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) * 0.5;
		double spread = (sorted[Math.min(n - 1, n * 3 / 4)] - sorted[n / 4]) / Math.max(median, 0.001);
		return new double[] {median, spread};
	}

	private void begin(long now) {
		note = "";
		snapshot = target.snapshot();
		target.useTestState(false);
		applied(target);
		TestLoad.set(true);
		rewarm(now);
		lastCall = 0L;
		seenCount = FrameClock.count();
	}

	public void cancel() {
		if (running()) {
			if (snapshot != null) {
				target.restore(snapshot);
				applied(target);
			}
			snapshot = null;
			phase = Phase.IDLE;
			TestLoad.set(false);
		}
	}

	public void frame() {
		if (!running()) {
			return;
		}
		long now = System.nanoTime();
		if (phase == Phase.PREPARE) {
			if (me.bewf.luceat.client.shader.VariantWatcher.satisfies(prepRays, prepSpread)) {
				begin(now);
			} else if (now - prepareStart > 30_000_000_000L) {
				note = "The shader did not rebuild in time. Close this screen for a moment and try again.";
				cancel();
			}
			return;
		}
		double millis = -1.0;
		int count = FrameClock.count();
		if (count != seenCount) {
			int fresh = Math.min(count - seenCount, 6);
			seenCount = count;
			millis = FrameClock.millis();
			if (phase == Phase.SAMPLE) {
				for (int i = 1; i < fresh; i++) {
					take(millis);
				}
			}
			frames += fresh - 1;
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
			long waited = now - phaseStart;
			boolean quiet = settled();
			if (frames >= MIN_WARM && waited >= WARM_NANOS && (quiet || waited >= SETTLE_LIMIT_NANOS)) {
				phase = Phase.SAMPLE;
				frames = 0;
				windowCount = 0;
				phaseStart = now;
			}
			return;
		}
		take(millis);
		long taken = now - phaseStart;
		if (windowCount < MIN_SAMPLE || taken < SAMPLE_NANOS) {
			return;
		}
		double[] stats = summary();
		if (stats[1] > 0.2 && windowCount < MAX_SAMPLE && taken < SAMPLE_LIMIT_NANOS) {
			return;
		}
		levels[step] = stats[0];
		step++;
		if (step >= STEPS) {
			finishProbe(now);
			return;
		}
		target.useTestState(step % 2 == 1);
		applied(target);
		rewarm(now);
	}

	private void finishProbe(long now) {
		double low = (levels[0] + levels[2]) * 0.5;
		double high = levels[1];
		double difference = Math.abs(high - low);
		double drift = Math.abs(levels[0] - levels[2]);
		boolean steady = drift <= Math.max(0.6, difference * 0.5) && (reference <= 0.0 || difference <= reference * 1.25 + 0.5);
		if (!steady && attempt == 0) {
			attempt = 1;
			firstDifference = difference;
			step = 0;
			target.useTestState(false);
			applied(target);
			rewarm(now);
			return;
		}
		if (attempt == 1 && firstDifference >= 0.0) {
			difference = Math.min(difference, firstDifference);
		}
		double scale = Math.max(high, low);
		if (target.id().equals("Luceat")) {
			reference = scale;
		}
		double base = reference > 0.0 ? reference : scale;
		float percent = (float) Math.min(100.0, difference / Math.max(base, 0.001) * 100.0);
		target.restore(snapshot);
		applied(target);
		results.put(target, new Result((float) difference, percent));
		note = "";
		phase = Phase.IDLE;
		TestLoad.set(false);
	}

	public int progress() {
		if (!running()) {
			return 0;
		}
		float inStep = phase == Phase.SAMPLE ? 0.3F + 0.7F * Math.min(1.0F, windowCount / (float) MIN_SAMPLE) : 0.3F * Math.min(1.0F, (System.nanoTime() - phaseStart) / (float) WARM_NANOS);
		return Math.min(100, Math.round((step + inStep) * 100.0F / STEPS));
	}

	private static void applied(Setting setting) {
		if (setting.rebuildsShadows()) {
			ShadowVolume.reset();
		}
	}
}
