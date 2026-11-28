package me.bewf.luceat.client.sky;

public enum CloudQuality {
	LOW(12, 2, 0),
	MEDIUM(20, 3, 1),
	HIGH(32, 3, 1);

	private final int steps;
	private final int taps;
	private final int detail;

	CloudQuality(int steps, int taps, int detail) {
		this.steps = steps;
		this.taps = taps;
		this.detail = detail;
	}

	public int steps() {
		return steps;
	}

	public int taps() {
		return taps;
	}

	public int detail() {
		return detail;
	}
}
