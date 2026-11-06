package me.bewf.luceat.client.gui.setting;

import me.bewf.luceat.client.gui.LuceatLook;

public enum Cost {
	NONE("None", LuceatLook.MUTED),
	LOW("Low", LuceatLook.GOOD),
	MEDIUM("Medium", 0xFFB28CFF),
	HIGH("High", 0xFFBE3FFF),
	VERY_HIGH("Very high", LuceatLook.BAD);

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

	public static Cost fromShare(float percent) {
		if (percent < 0.5F) {
			return NONE;
		}
		if (percent < 2.5F) {
			return LOW;
		}
		if (percent < 7.0F) {
			return MEDIUM;
		}
		return percent < 15.0F ? HIGH : VERY_HIGH;
	}
}
