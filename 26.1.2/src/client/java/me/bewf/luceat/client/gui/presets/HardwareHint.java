package me.bewf.luceat.client.gui.presets;

import java.util.Locale;

import org.lwjgl.opengl.GL11;

// a rough guess at which preset suits this computer
public final class HardwareHint {
	private static String name;
	private static String gpu;

	private HardwareHint() {
	}

	public static String gpuName() {
		if (gpu == null) {
			String found = null;
			try {
				found = GL11.glGetString(GL11.GL_RENDERER);
			} catch (Throwable ignored) {
			}
			gpu = found == null ? "" : found;
		}
		return gpu;
	}

	public static String recommended() {
		if (name != null) {
			return name;
		}
		String card = gpuName().toLowerCase(Locale.ROOT);
		int cores = Runtime.getRuntime().availableProcessors();
		boolean software = card.contains("llvmpipe") || card.contains("software") || card.contains("microsoft basic");
		boolean strong = card.contains("rtx") || card.contains("rx 6") || card.contains("rx 7") || card.contains("rx 9")
			|| card.contains("gtx 1080") || card.contains("gtx 1070") || card.contains("apple m") || card.contains("arc a7") || card.contains("arc b");
		boolean weak = card.contains("uhd") || card.contains("hd graphics") || card.contains("iris") || card.contains("vega")
			|| card.contains("radeon graphics") || card.contains("intel");
		boolean middle = !strong && !weak && (card.contains("gtx") || card.contains("rx ") || card.contains("radeon") || card.contains("geforce") || card.contains("arc"));
		if (software) {
			name = "Potato";
		} else if (strong) {
			name = cores >= 6 ? "Phenomenal" : "Balanced";
		} else if (middle) {
			name = "Balanced";
		} else if (weak) {
			name = "Potato";
		} else {
			name = "Balanced";
		}
		return name;
	}
}
