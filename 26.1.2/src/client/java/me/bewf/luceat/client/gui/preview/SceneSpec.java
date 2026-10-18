package me.bewf.luceat.client.gui.preview;

import java.util.function.Consumer;

import me.bewf.luceat.client.config.LuceatConfig;

public record SceneSpec(Scene scene, String caption, Consumer<LuceatConfig> baseline, String leftLabel, String rightLabel, Float time) {
	public boolean compares() {
		return baseline != null;
	}
}
