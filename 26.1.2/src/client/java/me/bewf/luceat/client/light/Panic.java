package me.bewf.luceat.client.light;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.Say;

import net.minecraft.client.Minecraft;

public final class Panic {
	private static final float SLOW_MS = 250.0F;
	private static final float OK_MS = 100.0F;
	private static final float STALL_MS = 3000.0F;
	private static final float TRIGGER_MS = 5000.0F;
	private static final int TRIGGER_FRAMES = 14;
	private static final int AFTER_FRAMES = 30;

	private static boolean paused;
	private static float slowFor;
	private static float slowSum;
	private static int slowFrames;
	private static int afterFrames;
	private static float afterSum;
	private static boolean reported;

	private Panic() {
	}

	public static boolean paused() {
		return paused;
	}

	public static void resume() {
		paused = false;
		clear();
		afterFrames = 0;
		afterSum = 0.0F;
		reported = false;
	}

	private static void clear() {
		slowFor = 0.0F;
		slowSum = 0.0F;
		slowFrames = 0;
	}

	public static void frame(float ms, int lightCount) {
		if (!LuceatConfig.get().pauseOnFreeze) {
			if (paused) {
				resume();
			}
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (TestLoad.active() || minecraft.level == null || minecraft.screen != null || minecraft.isPaused() || !minecraft.isWindowActive() || minecraft.getWindow().isMinimized()) {
			clear();
			return;
		}
		if (paused) {
			if (!reported && afterFrames < AFTER_FRAMES) {
				afterFrames++;
				afterSum += ms;
				if (afterFrames == AFTER_FRAMES) {
					report();
				}
			}
			return;
		}
		if (ms > STALL_MS) {
			clear();
			return;
		}
		if (lightCount > 0 && ms > SLOW_MS) {
			slowFor += ms;
			slowSum += ms;
			slowFrames++;
			if (slowFor >= TRIGGER_MS && slowFrames >= TRIGGER_FRAMES) {
				paused = true;
				afterFrames = 0;
				afterSum = 0.0F;
				reported = false;
			}
		} else if (ms < OK_MS) {
			clear();
		}
	}

	private static void report() {
		reported = true;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		float before = slowFrames == 0 ? 0.0F : slowSum / slowFrames;
		float after = afterSum / AFTER_FRAMES;
		boolean lightsAreSlow = after < before * 0.5F;
		Say.chat(Say.line(
			Say.colour("Frames stayed under 4 FPS for a while, so Luceat paused its lights. ", Say.WARN),
			Say.text(lightsAreSlow ? "Frames went from " : "Frames stayed at "),
			Say.colour(lightsAreSlow ? Math.round(before) + " ms" : Math.round(after) + " ms", Say.VALUE),
			Say.text(lightsAreSlow ? " to " : " with Luceat paused, so the slowness is somewhere else."),
			lightsAreSlow ? Say.colour(Math.round(after) + " ms", Say.VALUE) : Say.text(""),
			lightsAreSlow ? Say.text(" with Luceat paused, so the lights are the slow part.") : Say.text("")
		));
		Say.chat(Say.line(
			Say.colour("/luceat resume", Say.VALUE), Say.text(" turns the lights back on. "),
			Say.colour("/luceat stats", Say.VALUE), Say.text(" shows the numbers. The setting is under Advanced.")
		));
	}
}
