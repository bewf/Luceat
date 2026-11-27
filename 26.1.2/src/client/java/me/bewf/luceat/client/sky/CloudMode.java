package me.bewf.luceat.client.sky;

public enum CloudMode {
	REAL_WORLD("Real world", "Clouds follow the weather outside, where you are."),
	IN_GAME("In game", "Clouds follow the weather in the game."),
	CUSTOM("Pick your own", "Always the cloud type you choose.");

	private final String label;
	private final String note;

	CloudMode(String label, String note) {
		this.label = label;
		this.note = note;
	}

	public String label() {
		return label;
	}

	public String note() {
		return note;
	}
}
