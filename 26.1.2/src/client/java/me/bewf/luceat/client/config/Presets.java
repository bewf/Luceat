package me.bewf.luceat.client.config;

import java.util.List;
import java.util.function.Consumer;

public final class Presets {
	public record Preset(String name, String blurb, String cost, Consumer<LuceatConfig> apply) {
		public void applyTo(LuceatConfig config) {
			apply.accept(config);
			config.preset = name;
			config.presetChosen = true;
		}

		public boolean matches(LuceatConfig config) {
			LuceatConfig copy = config.copy();
			apply.accept(copy);
			copy.preset = config.preset;
			copy.presetChosen = config.presetChosen;
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
			"But I wasn’t farming potatoes for profit, I was farming them for honor, for glory. - Technoblade",
			"Very light",
			c -> {
				c.shadows = false;
				c.shadowSoftness = 0.0F;
				c.thinShadows = false;
				c.plantShadows = false;
				c.foliageShadows = ShadowQuality.OFF;
				c.glassTint = false;
				c.flicker = 0.25F;
				c.shadowedLights = 0;
				c.lightBounce = 0.0F;
				c.lightSpread = false;
				c.spillStrength = 0.0F;
				c.sunShadows = false;
				c.sunShadowStrength = 0.65F;
				c.caustics = false;
				c.shine = 0.0F;
				c.maxLights = 24;
				c.mergeFarLights = true;
				c.lightRange = 40;
				c.heldLights = true;
				c.otherPlayers = true;
				c.mobs = true;
				c.droppedItems = true;
				c.itemFrames = true;
				c.naturalLights = true;
				c.enchantGlow = true;
				c.entityLights = true;
				c.particleLights = true;
				c.placedLights = true;
				c.placedMinLevel = 6;
				c.directionalShading = 0.4F;
			}),
		new Preset("Balanced",
			"Perfectly balanced, as all things should be - Thanos",
			"Medium",
			c -> {
				c.placedReach = 1.1F;
				c.shadows = true;
				c.shadowDistance = 40;
				c.shadowSoftness = 0.0F;
				c.thinShadows = true;
				c.plantShadows = true;
				c.foliageShadows = ShadowQuality.FANCY;
				c.glassTint = true;
				c.flicker = 0.25F;
				c.shadowedLights = 2;
				c.lightBounce = 0.25F;
				c.lightSpread = false;
				c.spillStrength = 0.0F;
				c.sunShadows = false;
				c.sunShadowStrength = 0.65F;
				c.caustics = true;
				c.shine = 0.35F;
				c.maxLights = 96;
				c.mergeFarLights = true;
				c.entityLights = true;
				c.particleLights = true;
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
				c.spreadSteps = 1;
				c.scanBudget = 0.4F;
				c.clusterSize = 6;
			}),
		new Preset("Phenomenal",
			"Phenomenal cosmic power! - Genie",
			"Heavy",
			c -> {
				c.placedReach = 1.2F;
				c.shadows = true;
				c.shadowDistance = 64;
				c.shadowSoftness = 0.7F;
				c.thinShadows = true;
				c.plantShadows = true;
				c.foliageShadows = ShadowQuality.FANCY;
				c.glassTint = true;
				c.flicker = 0.3F;
				c.shadowedLights = 6;
				c.lightBounce = 0.6F;
				c.lightSpread = true;
				c.spillStrength = 0.6F;
				c.sunShadows = true;
				c.sunShadowStrength = 0.65F;
				c.caustics = true;
				c.shine = 0.7F;
				c.maxLights = 192;
				c.mergeFarLights = true;
				c.entityLights = true;
				c.particleLights = true;
				c.lightRange = 96;
				c.heldLights = true;
				c.otherPlayers = true;
				c.mobs = true;
				c.droppedItems = true;
				c.itemFrames = true;
				c.naturalLights = true;
				c.enchantGlow = true;
				c.placedLights = true;
				c.placedMinLevel = 1;
				c.directionalShading = 0.85F;
			}),
		new Preset("HARDCORE",
			"Hardcore to the Mega - Egg Head",
			"Max",
			c -> {
				c.placedReach = 1.35F;
				c.shadows = true;
				c.shadowDistance = 96;
				c.shadowSoftness = 1.0F;
				c.thinShadows = true;
				c.plantShadows = true;
				c.foliageShadows = ShadowQuality.FANCY;
				c.glassTint = true;
				c.flicker = 0.5F;
				c.shadowedLights = 12;
				c.lightBounce = 1.0F;
				c.lightSpread = true;
				c.spillStrength = 0.85F;
				c.sunShadows = true;
				c.sunShadowStrength = 0.65F;
				c.caustics = true;
				c.shine = 1.0F;
				c.maxLights = 256;
				c.mergeFarLights = false;
				c.adaptiveQuality = false;
				c.entityLights = true;
				c.particleLights = true;
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
				c.directionalShading = 1.0F;
				c.clusterSize = 32;
				c.joinBeyond = 64;
				c.spreadSteps = 3;
				c.shadowUpdates = 6;
				c.scanBudget = 1.2F;
			})
	);

	private Presets() {
	}

	public static List<Preset> all() {
		return ALL;
	}

	public static Preset byName(String name) {
		if (name != null) {
			if (name.equalsIgnoreCase("Light")) {
				name = "Balanced";
			}
			for (Preset preset : ALL) {
				if (preset.name().equalsIgnoreCase(name)) {
					return preset;
				}
			}
		}
		return null;
	}

	public static LuceatConfig baseline(LuceatConfig live) {
		LuceatConfig base = new LuceatConfig();
		Preset chosen = byName(live == null ? null : live.preset);
		if (chosen != null) {
			chosen.applyTo(base);
		}
		return base;
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
