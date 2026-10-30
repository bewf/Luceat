package me.bewf.luceat.client.gui.setting;

import java.util.ArrayList;
import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShaderPackMode;
import me.bewf.luceat.client.config.ShadowQuality;
import me.bewf.luceat.client.light.LightSpecs;
import me.bewf.luceat.client.shadow.ShadowVolume;

public final class LuceatSettings {
	public static final String LIGHTING = "Lighting";
	public static final String SHADOWS = "Shadows";
	public static final String PLACED = "Placed lights";
	public static final String SOURCES = "Light sources";
	public static final String PERFORMANCE = "Performance";
	public static final String GENERAL = "General";
	public static final String ADVANCED = "Advanced";

	private LuceatSettings() {
	}

	public static List<String> sections() {
		return List.of(LIGHTING, SHADOWS, PLACED, SOURCES, PERFORMANCE, GENERAL, ADVANCED);
	}

	public static List<Setting> create() {
		return create(LuceatConfig.get());
	}

	public static List<Setting> create(LuceatConfig c) {
		List<Setting> all = new ArrayList<>();

		all.add(new ToggleSetting(GENERAL, "Luceat",
			"Turns Luceat on or off. Off is the default minecraft look.",
			Cost.MEDIUM, false, () -> c.enabled, value -> c.enabled = value));

		all.add(new SliderSetting(LIGHTING, "Light strength",
			"How bright your lights are. Higher is brighter.",
			Cost.NONE, false, 0.0F, 3.0F, 0.05F, "x", () -> c.brightness, value -> c.brightness = value));
		all.add(new SliderSetting(LIGHTING, "Brightness limit",
			"Stops multiple lights from adding up to pure white.",
			Cost.NONE, false, 0.3F, 2.0F, 0.05F, "x", () -> c.maxBrightness, value -> c.maxBrightness = value));
		all.add(new SliderSetting(LIGHTING, "Fade in daylight",
			"Fades out light based during daytime",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.daylightFade, value -> c.daylightFade = value));
		all.add(new SliderSetting(LIGHTING, "Surface direction",
			"Surface facing the light are lit up more than surfaces facing away. At zero everything is lit the same.",
			Cost.LOW, false, 0.0F, 1.0F, 0.05F, "", () -> c.directionalShading, value -> c.directionalShading = value));
		all.add(new SliderSetting(LIGHTING, "Light bounce",
			"Shadows keep some light to simulate light bouncing off the walls around them. At zero shadows go fully dark.",
			Cost.LOW, false, 0.0F, 1.0F, 0.05F, "", () -> c.lightBounce, value -> c.lightBounce = value));
		all.add(new ToggleSetting(LIGHTING, "Light around corners",
			"Light spreads around corners and through gaps, so rooms next to a lit room glow a little. Needs OpenGL 4.3.",
			Cost.MEDIUM, false, () -> c.lightSpread, value -> c.lightSpread = value));
		all.add(new SliderSetting(LIGHTING, "Corner light strength",
			"How strong the light around corners is.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.spillStrength, value -> c.spillStrength = value));
		all.add(new SliderSetting(LIGHTING, "Shiny surfaces",
			"Shiny stuff being shiny. At zero nothing is shiny.",
			Cost.LOW, true, 0.0F, 1.0F, 0.05F, "", () -> c.shine, value -> c.shine = value));
		all.add(new ToggleSetting(LIGHTING, "Colours from textures",
			"Picks the glow colour from the item's own texture, so a texture pack changes the colours too.",
			Cost.NONE, false, () -> c.textureColours, value -> {
				c.textureColours = value;
				LightSpecs.clearCache();
			}));
		all.add(new ToggleSetting(LIGHTING, "Glow on enchanted items",
			"Enchanted items give off a small purple glow while you hold them.",
			Cost.LOW, false, () -> c.enchantGlow, value -> c.enchantGlow = value));

		all.add(new ToggleSetting(SHADOWS, "Sun shadows",
			"Trees, roofs and cliffs cast shadows from the sun on the ground near you.",
			Cost.HIGH, false, () -> c.sunShadows, value -> c.sunShadows = value));
		all.add(new SliderSetting(SHADOWS, "Sun shadow strength",
			"How dark sun shadows are.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.sunShadowStrength, value -> c.sunShadowStrength = value));
		all.add(new ToggleSetting(SHADOWS, "Water light patterns",
			"Moving bright streaks of sunlight on the bottom of pools and the sea.",
			Cost.LOW, false, () -> c.caustics, value -> c.caustics = value));
		all.add(new ToggleSetting(SHADOWS, "Shadows",
			"Adds Shadows to lights. Stop light from going through blocks.",
			Cost.HIGH, false, () -> c.shadows, value -> c.shadows = value));
		all.add(new SliderSetting(SHADOWS, "Shadow darkness",
			"How dark a shadow gets. At zero the light ignores walls.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.shadowStrength, value -> c.shadowStrength = value));
		all.add(new ToggleSetting(SHADOWS, "Keep lights out of walls",
			"If a light is held inside a wall, it is moved back out so the wall does not go dark.",
			Cost.LOW, false, () -> c.keepLightOutOfWalls, value -> c.keepLightOutOfWalls = value));
		all.add(new SliderSetting(SHADOWS, "Soft edges",
			"Blurs the edge of shadows. Zero is sharp and fastest. Higher is softer and costs more.",
			Cost.MEDIUM, false, 0.0F, 1.0F, 0.05F, "", () -> c.shadowSoftness, value -> c.shadowSoftness = value));
		all.add(new ToggleSetting(SHADOWS, "Fences, panes, slabs and more",
			"Blocks that aren't full, like fences, bars, doors, stairs and slabs, cast their real shape as a shadow.",
			Cost.MEDIUM, true, () -> c.thinShadows, value -> c.thinShadows = value));
		all.add(new ToggleSetting(SHADOWS, "Grass and flowers",
			"Plants cast a soft shadow too.",
			Cost.LOW, true, () -> c.plantShadows, value -> c.plantShadows = value));
		all.add(new ChoiceSetting<>(SHADOWS, "Leaves and holes",
			"How leaves, grates and other blocks with holes shade the light. Click to change.",
			Cost.MEDIUM, true, ShadowQuality.values(), new String[]{"Off", "Fast", "Fancy"}, () -> c.foliageShadows, value -> c.foliageShadows = value)
			.withNotes("Leaves and grates act like solid blocks.", "They dim the light by how see through the block is.", "They cast the real shape of the leaves or holes."));
		all.add(new ToggleSetting(SHADOWS, "Coloured glass",
			"Stained glass tints the light that comes through it to the colour of the glass.",
			Cost.LOW, true, () -> c.glassTint, value -> c.glassTint = value));
		all.add(new SliderSetting(SHADOWS, "Hole strength",
			"How solid the parts of leaves and grates count as. Lower lets more light through.",
			Cost.NONE, true, 0.3F, 1.0F, 0.05F, "", () -> c.cutoutOpacity, value -> c.cutoutOpacity = value));

		all.add(new ToggleSetting(PLACED, "Placed lights",
			"Light blocks you place light up their surroundings with their own colour and cast shadows.",
			Cost.HIGH, false, () -> c.placedLights, value -> {
				c.placedLights = value;
				ShadowVolume.reset();
			}));
		all.add(new SliderSetting(PLACED, "Placed light strength",
			"How bright the light from placed blocks is. Higher is brighter.",
			Cost.NONE, false, 0.0F, 2.0F, 0.05F, "x", () -> c.placedStrength, value -> c.placedStrength = value));
		all.add(new SliderSetting(PLACED, "Placed light reach",
			"How far the light from placed blocks reaches. Higher lets it spread further before it fades out.",
			Cost.LOW, false, 0.8F, 1.6F, 0.05F, "x", () -> c.placedReach, value -> {
				c.placedReach = value;
				ShadowVolume.reset();
			}));
		all.add(new ToggleSetting(PLACED, "Blocks from other mods",
			"Blocks from other mods that give off light get a Luceat light too, with a colour picked from their texture.",
			Cost.LOW, false, () -> c.autoLights, value -> {
				c.autoLights = value;
				ShadowVolume.reset();
			}));
		all.add(new SliderSetting(PLACED, "Replace normal light",
			"How much of the normal Minecraft block light is swapped for Luceat's light. At zero, placed light blocks look the way they always did.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.vanillaReplace, value -> c.vanillaReplace = value));
		all.add(new SliderSetting(PLACED, "Smallest light",
			"Light blocks dimmer than this are left alone. Raising it skips candles and small buds.",
			Cost.LOW, false, 1.0F, 15.0F, 1.0F, "", () -> c.placedMinLevel, value -> {
				c.placedMinLevel = Math.round(value);
				ShadowVolume.reset();
			}));

		all.add(new ToggleSetting(SOURCES, "Your held lights",
			"Items in your hands light up the world around you.",
			Cost.LOW, false, () -> c.heldLights, value -> c.heldLights = value));
		all.add(new ToggleSetting(SOURCES, "Other players",
			"Items other players hold light up the world around them.",
			Cost.LOW, false, () -> c.otherPlayers, value -> c.otherPlayers = value));
		all.add(new ToggleSetting(SOURCES, "Mobs holding items",
			"Mobs that hold a light give off light.",
			Cost.LOW, false, () -> c.mobs, value -> c.mobs = value));
		all.add(new ToggleSetting(SOURCES, "Dropped items",
			"Items lying on the ground give off light.",
			Cost.LOW, false, () -> c.droppedItems, value -> c.droppedItems = value));
		all.add(new ToggleSetting(SOURCES, "Items in item frames",
			"Items in item frames give off their light.",
			Cost.LOW, false, () -> c.itemFrames, value -> c.itemFrames = value));
		all.add(new ToggleSetting(SOURCES, "Fire, glowing mobs and more",
			"Blazes, burning mobs, allays, glow squid, magma cubes, fireballs, end crystals and other glowing things give off light.",
			Cost.LOW, false, () -> c.naturalLights, value -> c.naturalLights = value));
		all.add(new ToggleSetting(SOURCES, "Light on mobs and items",
			"Players, mobs and dropped items get brighter when one of your lights is close to them.",
			Cost.NONE, false, () -> c.entityLights, value -> c.entityLights = value));
		all.add(new ToggleSetting(SOURCES, "Light on particles",
			"Smoke, sparks and other particles get brighter when one of your lights is close to them.",
			Cost.NONE, false, () -> c.particleLights, value -> c.particleLights = value));
		all.add(new SliderSetting(SOURCES, "Held light height",
			"How high your held light sits on your body.",
			Cost.NONE, false, 0.3F, 1.0F, 0.02F, "", () -> c.heldHeight, value -> c.heldHeight = value));
		all.add(new SliderSetting(SOURCES, "Fire flicker",
			"Warm lights like torches and campfires flicker. Zero removes flicker.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.flicker, value -> c.flicker = value));
		all.add(new ToggleSetting(SOURCES, "Water puts out lights",
			"Torches, campfires and similar items go dark under water.",
			Cost.NONE, false, () -> c.waterPutsOutLights, value -> c.waterPutsOutLights = value));

		all.add(new SliderSetting(PERFORMANCE, "Most lights at once",
			"How many lights can be active at once. Each pixel only looks at the lights near it, so a big number costs very little. Far away lights are joined together to save even more.",
			Cost.LOW, false, 4.0F, 256.0F, 4.0F, "", () -> c.maxLights, value -> c.maxLights = Math.round(value)));
		all.add(new ToggleSetting(PERFORMANCE, "Join faraway lights",
			"Lights that are far from you and close to each other are joined into one bigger light.",
			Cost.NONE, false, () -> c.mergeFarLights, value -> c.mergeFarLights = value));
		all.add(new ToggleSetting(PERFORMANCE, "Adaptive quality",
			"When the frame rate drops, shadows and bounce ease off a little, then come back when the game has room again.",
			Cost.NONE, false, () -> c.adaptiveQuality, value -> c.adaptiveQuality = value));
		all.add(new SliderSetting(PERFORMANCE, "Light range",
			"How far away Luceat looks for lights. Lights past this fade out smoothly.",
			Cost.LOW, false, 16.0F, 128.0F, 8.0F, " blocks", () -> c.lightRange, value -> c.lightRange = Math.round(value)));
		all.add(new SliderSetting(PERFORMANCE, "Lights with shadows",
			"Every pixel traces shadows for this many of the strongest lights near it. The other lights still shine, they just do not cast shadows.",
			Cost.HIGH, false, 0.0F, 12.0F, 1.0F, "", () -> c.shadowedLights, value -> c.shadowedLights = Math.round(value)));
		all.add(new SliderSetting(PERFORMANCE, "Shadow distance",
			"Shadows fade out on things farther from you than this. Lower is faster.",
			Cost.MEDIUM, false, 8.0F, 128.0F, 8.0F, " blocks", () -> c.shadowDistance, value -> c.shadowDistance = Math.round(value)));

		all.add(new ChoiceSetting<>(GENERAL, "When a shader pack is on",
			"Off: Luceat does nothing while a pack is on. Overwrite: switches the pack off so Luceat's lighting shows. Compatible: keeps the pack and lights held items, mobs, dropped items and particles with the game's own light, with no Luceat shader.",
			Cost.NONE, false, ShaderPackMode.values(), new String[]{"Off", "Overwrite", "Compatible"}, () -> c.shaderPacks, value -> c.shaderPacks = value));
		all.add(new ToggleSetting(GENERAL, "Update notices",
			"Checks Modrinth when the game starts and tells you in chat if a new version is out.",
			Cost.NONE, false, () -> c.updateChecker, value -> c.updateChecker = value));

		all.add(new ToggleSetting(ADVANCED, "Skip buried lights",
			"Ignores light blocks with no open side, like the inside of a lava lake. Big saving in the Nether.",
			Cost.NONE, false, () -> c.skipBuried, value -> {
				c.skipBuried = value;
				ShadowVolume.reset();
			}));
		all.add(new ToggleSetting(ADVANCED, "Hide lights behind blocks",
			"A placed light only shows where the game's own light reaches too. Stops lava glowing up through the ground.",
			Cost.NONE, false, () -> c.hideBehindBlocks, value -> c.hideBehindBlocks = value));
		all.add(new ToggleSetting(ADVANCED, "Join crowded lights",
			"Lava lakes and walls of torches turn into a few strong lights instead of hundreds.",
			Cost.NONE, false, () -> c.clusterLights, value -> c.clusterLights = value));
		all.add(new SliderSetting(ADVANCED, "Crowd size",
			"How many lights one 16 block chunk can hold before they start joining.",
			Cost.LOW, false, 4.0F, 32.0F, 1.0F, "", () -> c.clusterSize, value -> c.clusterSize = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Join lights past",
			"Distance where far lights start joining. Needs Join faraway lights.",
			Cost.LOW, false, 16.0F, 64.0F, 4.0F, " blocks", () -> c.joinBeyond, value -> c.joinBeyond = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Search time",
			"Time per frame spent looking for new light blocks. Lower is smoother, new lights show up later.",
			Cost.LOW, false, 0.1F, 3.0F, 0.1F, " ms", () -> c.scanBudget, value -> c.scanBudget = value));
		all.add(new SliderSetting(ADVANCED, "Light easing",
			"How slowly lights fade in, fade out and change size. Zero is instant and can look jumpy.",
			Cost.NONE, false, 0.0F, 2.0F, 0.1F, "x", () -> c.lightEase, value -> c.lightEase = value));
		all.add(new SliderSetting(ADVANCED, "Corner light steps",
			"How far corner light travels each frame. Lower is cheaper, higher fills rooms faster.",
			Cost.LOW, false, 1.0F, 4.0F, 1.0F, "", () -> c.spreadSteps, value -> c.spreadSteps = Math.round(value)));
		all.add(new ToggleSetting(ADVANCED, "Rest corner light",
			"Stops updating corner light while nothing moves.",
			Cost.NONE, false, () -> c.spreadRests, value -> c.spreadRests = value));
		all.add(new SliderSetting(ADVANCED, "Shadow map updates",
			"How many 16 block pieces of the shadow map are rebuilt per frame. Higher catches up faster after you build.",
			Cost.LOW, false, 1.0F, 8.0F, 1.0F, "", () -> c.shadowUpdates, value -> c.shadowUpdates = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Lower quality below",
			"If the frame rate stays under this, adaptive quality eases shadows and bounce down.",
			Cost.NONE, false, 20.0F, 90.0F, 1.0F, " fps", () -> c.slowBelowFps, value -> c.slowBelowFps = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Raise quality above",
			"If the frame rate stays over this, quality comes back.",
			Cost.NONE, false, 30.0F, 144.0F, 1.0F, " fps", () -> c.fastAboveFps, value -> c.fastAboveFps = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Lowest quality",
			"The floor for adaptive quality. At 1 it never goes down.",
			Cost.NONE, false, 0.3F, 1.0F, 0.05F, "", () -> c.lowestQuality, value -> c.lowestQuality = value));
		all.add(new ToggleSetting(ADVANCED, "Pause when it freezes",
			"If lights drag the game to a crawl for 3 seconds, Luceat pauses them and says so. /luceat resume turns them back on.",
			Cost.NONE, false, () -> c.autoPause, value -> c.autoPause = value));
		return all;
	}
}
