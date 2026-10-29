package me.bewf.luceat.client.light;

import me.bewf.luceat.client.gui.LuceatLook;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class Panic {
	private static final float SLOW_MS = 250.0F;
	private static final float OK_MS = 100.0F;
	private static final float TRIGGER_MS = 3000.0F;
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
		slowFor = 0.0F;
		slowSum = 0.0F;
		slowFrames = 0;
		afterFrames = 0;
		afterSum = 0.0F;
		reported = false;
	}

	public static void frame(float ms, int lightCount) {
		if (TestLoad.active()) {
			return;
		}
		if (!me.bewf.luceat.client.config.LuceatConfig.get().autoPause) {
			if (paused) {
				resume();
			}
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
		if (lightCount > 0 && ms > SLOW_MS) {
			slowFor += ms;
			slowSum += ms;
			slowFrames++;
			if (slowFor >= TRIGGER_MS) {
				paused = true;
				afterFrames = 0;
				afterSum = 0.0F;
				reported = false;
			}
		} else if (ms < OK_MS) {
			slowFor = 0.0F;
			slowSum = 0.0F;
			slowFrames = 0;
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
		String verdict = after < before * 0.5F
			? "Frames went from " + Math.round(before) + " ms to " + Math.round(after) + " ms with Luceat paused, so the lights are the slow part."
			: "Frames stayed at about " + Math.round(after) + " ms with Luceat paused, so the slowness is somewhere else.";
		Component message = Component.literal("[Luceat] ").withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF))
			.append(Component.literal("The game dropped under 4 FPS, so Luceat paused its lights. " + verdict + " Type /luceat resume to turn them back on or /luceat stats to see the numbers.").withStyle(Style.EMPTY.withColor(0xFFFFFF)));
		minecraft.gui.getChat().addClientSystemMessage(message);
	}
}
