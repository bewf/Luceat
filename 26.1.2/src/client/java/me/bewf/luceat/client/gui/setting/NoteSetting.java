package me.bewf.luceat.client.gui.setting;

import java.util.function.Supplier;

public final class NoteSetting extends Setting {
	private final Supplier<String> text;

	public NoteSetting(String section, String label, String help, Supplier<String> text) {
		super(section, label, help, Cost.NONE, false);
		this.text = text;
	}

	@Override
	public int rowHeight() {
		return 30;
	}

	@Override
	public String valueText() {
		return text.get();
	}
}
