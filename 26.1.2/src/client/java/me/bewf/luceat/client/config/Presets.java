package me.bewf.luceat.client.config;

import java.util.List;
import java.util.function.Consumer;

public final class Presets {
	public record Preset(String name, String blurb, String cost, Consumer<LuceatConfig> apply) {
		public boolean matches(LuceatConfig config) {
			LuceatConfig copy = config.copy();
			apply.accept(copy);
			return copy.asJson().equals(config.asJson());
		}

		public LuceatConfig preview(LuceatConfig config) {
			LuceatConfig copy = config.copy();
			apply.accept(copy);
			return copy;
		}
	}

	private static final List<Preset> ALL = List.of(
		new Preset("Potato",
			"But I wasn’t farming potatoes for profit, I was farming them for honor, for glory.",
			"Very light",
			c -> {
				c.shadows = false;
				c.shadowSoftness = 0.0F;
				c.thinShadows = false;
				c.plantShadows = false;
				c.foliageShadows = ShadowQuality.OFF;
				c.glassTint = false;
				c.flicker = 0.0F;
				c.maxLights = 6;
				c.lightRange = 32;
				c.heldLights = true;
				c.otherPlayers = false;
				c.mobs = false;
				c.droppedItems = false;
				c.itemFrames = false;
				c.naturalLights = false;
				c.enchantGlow = false;
				c.placedLights = true;
				c.placedMinLevel = 8;
				c.directionalShading = 0.4F;
			}),
		new Preset("Light",
			"Quick shadows from solid blocks only.",
			"Light",
			c -> {
				c.shadows = true;
				c.shadowDistance = 24;
				c.shadowSoftness = 0.0F;
				c.thinShadows = false;
				c.plantShadows = false;
				c.foliageShadows = ShadowQuality.FAST;
				c.glassTint = false;
				c.flicker = 0.0F;
				c.maxLights = 8;
				c.lightRange = 48;
				c.heldLights = true;
				c.otherPlayers = true;
				c.mobs = false;
				c.droppedItems = true;
				c.itemFrames = false;
				c.naturalLights = false;
				c.enchantGlow = false;
				c.placedLights = true;
				c.placedMinLevel = 5;
				c.directionalShading = 0.6F;
			}),
		new Preset("Balanced",
			"Perfectly balanced, as all things should be",
			"Medium",
			c -> {
				c.shadows = true;
				c.shadowDistance = 48;
				c.shadowSoftness = 0.0F;
				c.thinShadows = true;
				c.plantShadows = true;
				c.foliageShadows = ShadowQuality.FANCY;
				c.glassTint = true;
				c.flicker = 0.0F;
				c.maxLights = 16;
				c.lightRange = 64;
				c.heldLights = true;
				c.otherPlayers = true;
				c.mobs = true;
				c.droppedItems = true;
				c.itemFrames = true;
				c.naturalLights = true;
				c.enchantGlow = true;
				c.placedLights = true;
				c.placedMinLevel = 3;
				c.directionalShading = 0.7F;
			}),
		new Preset("Phenomenal",
			"Softer shadow edges, longer shadows and a little fire flicker.",
			"Heavy",
			c -> {
				c.shadows = true;
				c.shadowDistance = 64;
				c.shadowSoftness = 0.5F;
				c.thinShadows = true;
				c.plantShadows = true;
				c.foliageShadows = ShadowQuality.FANCY;
				c.glassTint = true;
				c.flicker = 0.3F;
				c.maxLights = 24;
				c.lightRange = 96;
				c.heldLights = true;
				c.otherPlayers = true;
				c.mobs = true;
				c.droppedItems = true;
				c.itemFrames = true;
				c.naturalLights = true;
				c.enchantGlow = true;
				c.placedLights = true;
				c.placedMinLevel = 2;
				c.directionalShading = 0.8F;
			}),
		new Preset("HARDCORE",
			"HARDCORE TO THE MEGA - Egg Head",
			"Max",
			c -> {
				c.shadows = true;
				c.shadowDistance = 96;
				c.shadowSoftness = 1.0F;
				c.thinShadows = true;
				c.plantShadows = true;
				c.foliageShadows = ShadowQuality.FANCY;
				c.glassTint = true;
				c.flicker = 0.4F;
				c.maxLights = 32;
				c.lightRange = 128;
				c.heldLights = true;
				c.otherPlayers = true;
				c.mobs = true;
				c.droppedItems = true;
				c.itemFrames = true;
				c.naturalLights = true;
				c.enchantGlow = true;
				c.placedLights = true;
				c.placedMinLevel = 1;
				c.directionalShading = 0.9F;
			})
	);

	private Presets() {
	}

	public static List<Preset> all() {
		return ALL;
	}

	public static Preset current(LuceatConfig config) {
		for (Preset preset : ALL) {
			if (preset.matches(config)) {
				return preset;
			}
		}
		return null;
	}
}
