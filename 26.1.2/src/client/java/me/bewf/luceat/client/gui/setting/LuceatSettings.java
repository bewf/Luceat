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

	private LuceatSettings() {
	}

	public static List<String> sections() {
		return List.of(LIGHTING, SHADOWS, PLACED, SOURCES, PERFORMANCE, GENERAL);
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
			"Off: leaves, grates and similar blocks work like solid blocks. Fast: they dim the light by how much of the block is see through. Fancy: they cast the real shape of the leaves or holes.",
			Cost.MEDIUM, true, ShadowQuality.values(), new String[]{"Off", "Fast", "Fancy"}, () -> c.foliageShadows, value -> c.foliageShadows = value));
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
		all.add(new SliderSetting(PERFORMANCE, "Light range",
			"How far away Luceat looks for lights. Lights past this fade out smoothly.",
			Cost.LOW, false, 16.0F, 128.0F, 8.0F, " blocks", () -> c.lightRange, value -> c.lightRange = Math.round(value)));
		all.add(new SliderSetting(PERFORMANCE, "Lights with shadows",
			"Every pixel traces shadows for this many of the strongest lights near it. The other lights still shine, they just do not cast shadows.",
			Cost.HIGH, false, 0.0F, 8.0F, 1.0F, "", () -> c.shadowedLights, value -> c.shadowedLights = Math.round(value)));
		all.add(new SliderSetting(PERFORMANCE, "Shadow distance",
			"Shadows fade out on things farther from you than this. Lower is faster.",
			Cost.MEDIUM, false, 8.0F, 128.0F, 8.0F, " blocks", () -> c.shadowDistance, value -> c.shadowDistance = Math.round(value)));

		all.add(new ChoiceSetting<>(GENERAL, "Shader packs",
			"A shader pack draws the world its own way, so Luceat's light does not show while one is on. Leave them: Luceat does nothing. Turn off: Luceat switches the pack off for you while Luceat is on. This needs Iris.",
			Cost.NONE, false, ShaderPackMode.values(), new String[]{"Leave them", "Turn off"}, () -> c.shaderPacks, value -> c.shaderPacks = value));
		all.add(new ToggleSetting(GENERAL, "Update notices",
			"Checks Modrinth when the game starts and tells you in chat if a new version is out.",
			Cost.NONE, false, () -> c.updateChecker, value -> c.updateChecker = value));
		return all;
	}
}
