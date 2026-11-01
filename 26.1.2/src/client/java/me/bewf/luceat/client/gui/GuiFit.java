package me.bewf.luceat.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Lays a Luceat screen out in its own units instead of Minecraft's GUI scale.
 * The unit size follows the window size, with a one step nudge when the player's GUI scale is far from it.
 */
public final class GuiFit {
	private static int device = 0;

	private int pixels = 1;
	private float factor = 1.0F;
	private int width = 1;
	private int height = 1;

	public static int raster() {
		if (device > 0) {
			return device;
		}
		try {
			return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
		} catch (RuntimeException error) {
			return 2;
		}
	}

	public static void release() {
		device = 0;
	}

	public void update(int screenWidth, int screenHeight) {
		int gui = 2;
		try {
			gui = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
		} catch (RuntimeException error) {
			// keep the default
		}
		float fbWidth = screenWidth * (float) gui;
		float fbHeight = screenHeight * (float) gui;
		int natural = Math.max(1, Math.round(Math.min(fbWidth / 800.0F, fbHeight / 450.0F)));
		int pick = natural;
		if (gui - natural >= 2) {
			pick++;
		} else if (natural - gui >= 2) {
			pick--;
		}
		pixels = Math.max(1, Math.min(6, pick));
		factor = pixels / (float) gui;
		width = (int) Math.ceil(fbWidth / pixels);
		height = (int) Math.ceil(fbHeight / pixels);
		device = pixels;
	}

	public int pixels() {
		return pixels;
	}

	public float factor() {
		return factor;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public double toUnits(double screenCoordinate) {
		return screenCoordinate / factor;
	}

	public int toScreen(int unitCoordinate) {
		return Math.round(unitCoordinate * factor);
	}

	public void begin(GuiGraphicsExtractor graphics) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(factor, factor);
	}

	public void end(GuiGraphicsExtractor graphics) {
		graphics.pose().popMatrix();
	}
}
