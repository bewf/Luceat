package me.bewf.luceat.client.gui.setting;

import me.bewf.luceat.client.gui.LuceatLook;

public enum Cost {
	NONE("None", LuceatLook.GOOD),
	LOW("Low", LuceatLook.GOOD),
	MEDIUM("Medium", LuceatLook.WARN),
	HIGH("High", LuceatLook.BAD);

	private final String label;
	private final int colour;

	Cost(String label, int colour) {
		this.label = label;
		this.colour = colour;
	}

	public String label() {
		return label;
	}

	public int colour() {
		return colour;
	}

	public static Cost fromMillis(float millis) {
		if (millis < 0.15F) {
			return NONE;
		}
		if (millis < 0.6F) {
			return LOW;
		}
		return millis < 1.5F ? MEDIUM : HIGH;
	}
}
