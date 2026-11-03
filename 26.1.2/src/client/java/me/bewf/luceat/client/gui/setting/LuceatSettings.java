package me.bewf.luceat.client.gui.setting;

import java.util.ArrayList;
import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShaderPackMode;
import me.bewf.luceat.client.config.ShadowQuality;
import me.bewf.luceat.client.light.LightSpecs;
import me.bewf.luceat.client.light.LightSpread;
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
		return List.of(GENERAL, LIGHTING, SHADOWS, PLACED, SOURCES, PERFORMANCE, ADVANCED);
	}

	public static List<Setting> create() {
		return create(LuceatConfig.get());
	}

	public static List<Setting> create(LuceatConfig c) {
		List<Setting> all = new ArrayList<>();

		all.add(new ToggleSetting(GENERAL, "Luceat",
			"Turns Luceat on or off. Off gives you the normal Minecraft look.",
			Cost.MEDIUM, false, () -> c.enabled, value -> c.enabled = value));

		all.add(new SliderSetting(LIGHTING, "Light strength",
			"Sets how bright lights are.",
			Cost.NONE, false, 0.0F, 3.0F, 0.05F, "x", () -> c.brightness, value -> c.brightness = value));
		all.add(new SliderSetting(LIGHTING, "Brightness limit",
			"Stops stacked lights from adding up to pure white.",
			Cost.NONE, false, 0.3F, 2.0F, 0.05F, "x", () -> c.maxBrightness, value -> c.maxBrightness = value));
		all.add(new SliderSetting(LIGHTING, "Fade in daylight",
			"Fades Luceat's light out in daylight.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.daylightFade, value -> c.daylightFade = value));
		all.add(new SliderSetting(LIGHTING, "Surface direction",
			"Lights surfaces that face a light more than surfaces that face away. At zero everything gets the same light.",
			Cost.LOW, false, 0.0F, 1.0F, 0.05F, "", () -> c.directionalShading, value -> c.directionalShading = value));
		all.add(new SliderSetting(LIGHTING, "Light bounce",
			"Lets shadows keep some light, as if it bounced off nearby walls. At zero shadows go fully dark.",
			Cost.LOW, false, 0.0F, 1.0F, 0.05F, "", () -> c.lightBounce, value -> c.lightBounce = value));
		all.add(new ToggleSetting(LIGHTING, "Light around corners",
			"Spreads light around corners and through gaps, so the room next door glows a little. Needs OpenGL 4.3.",
			Cost.MEDIUM, false, () -> c.lightSpread, value -> c.lightSpread = value).blockedBy(LightSpread::blocker));
		all.add(new SliderSetting(LIGHTING, "Corner light strength",
			"Sets how strong light around corners is.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.spillStrength, value -> c.spillStrength = value));
		all.add(new SliderSetting(LIGHTING, "Shiny surfaces",
			"Makes polished blocks, metal and water catch the light. At zero nothing shines.",
			Cost.LOW, true, 0.0F, 1.0F, 0.05F, "", () -> c.shine, value -> c.shine = value));
		all.add(new ToggleSetting(LIGHTING, "Colours from textures",
			"Takes the glow colour from the item's texture, so a resource pack changes the colours too.",
			Cost.NONE, false, () -> c.textureColours, value -> {
				c.textureColours = value;
				LightSpecs.clearCache();
			}));
		all.add(new ToggleSetting(LIGHTING, "Glow on enchanted items",
			"Gives enchanted items a faint purple glow in your hand.",
			Cost.LOW, false, () -> c.enchantGlow, value -> c.enchantGlow = value));

		all.add(new ToggleSetting(SHADOWS, "Sun shadows",
			"Lets trees, roofs and cliffs cast sun shadows on the ground near you.",
			Cost.HIGH, false, () -> c.sunShadows, value -> c.sunShadows = value));
		all.add(new SliderSetting(SHADOWS, "Sun shadow strength",
			"Sets how dark sun shadows are.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.sunShadowStrength, value -> c.sunShadowStrength = value));
		all.add(new ToggleSetting(SHADOWS, "Water light patterns",
			"Draws moving streaks of sunlight on the bottom of pools and the sea.",
			Cost.LOW, false, () -> c.caustics, value -> c.caustics = value));
		all.add(new ToggleSetting(SHADOWS, "Shadows",
			"Makes lights cast shadows, so light stops at blocks.",
			Cost.HIGH, false, () -> c.shadows, value -> c.shadows = value));
		all.add(new SliderSetting(SHADOWS, "Shadow darkness",
			"Sets how dark shadows get. At zero light ignores walls.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.shadowStrength, value -> c.shadowStrength = value));
		all.add(new ToggleSetting(SHADOWS, "Keep lights out of walls",
			"Moves a light out of the wall it sits in, so the wall does not go dark.",
			Cost.LOW, false, () -> c.keepLightOutOfWalls, value -> c.keepLightOutOfWalls = value));
		all.add(new SliderSetting(SHADOWS, "Soft edges",
			"Blurs shadow edges. Zero is sharp and fastest. Higher is softer and slower.",
			Cost.MEDIUM, false, 0.0F, 1.0F, 0.05F, "", () -> c.shadowSoftness, value -> c.shadowSoftness = value));
		all.add(new ToggleSetting(SHADOWS, "Fences, panes, slabs and more",
			"Lets fences, bars, doors, stairs and slabs cast their real shape.",
			Cost.MEDIUM, true, () -> c.thinShadows, value -> c.thinShadows = value));
		all.add(new ToggleSetting(SHADOWS, "Grass and flowers",
			"Lets plants cast a soft shadow.",
			Cost.LOW, true, () -> c.plantShadows, value -> c.plantShadows = value));
		all.add(new ChoiceSetting<>(SHADOWS, "Leaves and holes",
			"Sets how leaves, grates and other blocks with holes block light. Click to change.",
			Cost.MEDIUM, true, ShadowQuality.values(), new String[]{"Off", "Fast", "Fancy"}, () -> c.foliageShadows, value -> c.foliageShadows = value)
			.withNotes("Leaves and grates block light like solid blocks.", "Leaves and grates dim light by how see-through they are.", "Leaves and grates let light through their real holes."));
		all.add(new ToggleSetting(SHADOWS, "Coloured glass",
			"Tints light with the colour of the stained glass it passes through.",
			Cost.LOW, true, () -> c.glassTint, value -> c.glassTint = value));
		all.add(new SliderSetting(SHADOWS, "Hole strength",
			"Sets how solid leaves and grates count. Lower lets more light through.",
			Cost.NONE, true, 0.3F, 1.0F, 0.05F, "", () -> c.cutoutOpacity, value -> c.cutoutOpacity = value));

		all.add(new ToggleSetting(PLACED, "Placed lights",
			"Lights up the area around blocks that glow, in their own colour, with shadows.",
			Cost.HIGH, false, () -> c.placedLights, value -> {
				c.placedLights = value;
				ShadowVolume.reset();
			}));
		all.add(new SliderSetting(PLACED, "Placed light strength",
			"Sets how bright placed lights are.",
			Cost.NONE, false, 0.0F, 2.0F, 0.05F, "x", () -> c.placedStrength, value -> c.placedStrength = value));
		all.add(new SliderSetting(PLACED, "Placed light reach",
			"Sets how far placed light travels before it fades.",
			Cost.LOW, false, 0.8F, 1.6F, 0.05F, "x", () -> c.placedReach, value -> {
				c.placedReach = value;
				ShadowVolume.reset();
			}));
		all.add(new ToggleSetting(PLACED, "Blocks from other mods",
			"Gives glowing blocks from other mods a Luceat light, coloured from their texture.",
			Cost.LOW, false, () -> c.autoLights, value -> {
				c.autoLights = value;
				ShadowVolume.reset();
			}));
		all.add(new SliderSetting(PLACED, "Replace normal light",
			"Sets how much of Minecraft's block light Luceat replaces. At zero placed lights look vanilla.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.vanillaReplace, value -> c.vanillaReplace = value));
		all.add(new SliderSetting(PLACED, "Smallest light",
			"Skips light blocks dimmer than this, like candles and small buds.",
			Cost.LOW, false, 1.0F, 15.0F, 1.0F, "", () -> c.placedMinLevel, value -> {
				c.placedMinLevel = Math.round(value);
				ShadowVolume.reset();
			}));

		all.add(new ToggleSetting(SOURCES, "Your held lights",
			"Lights the world around you with the items in your hands.",
			Cost.LOW, false, () -> c.heldLights, value -> c.heldLights = value));
		all.add(new ToggleSetting(SOURCES, "Other players",
			"Lights the world around other players with the items they hold.",
			Cost.LOW, false, () -> c.otherPlayers, value -> c.otherPlayers = value));
		all.add(new ToggleSetting(SOURCES, "Mobs holding items",
			"Lets mobs that hold a light give it off.",
			Cost.LOW, false, () -> c.mobs, value -> c.mobs = value));
		all.add(new ToggleSetting(SOURCES, "Dropped items",
			"Lets items on the ground give off light.",
			Cost.LOW, false, () -> c.droppedItems, value -> c.droppedItems = value));
		all.add(new ToggleSetting(SOURCES, "Items in item frames",
			"Lets items in item frames give off light.",
			Cost.LOW, false, () -> c.itemFrames, value -> c.itemFrames = value));
		all.add(new ToggleSetting(SOURCES, "Fire, glowing mobs and more",
			"Lets blazes, burning mobs, allays, glow squid, magma cubes, fireballs, end crystals and similar mobs and objects give off light.",
			Cost.LOW, false, () -> c.naturalLights, value -> c.naturalLights = value));
		all.add(new ToggleSetting(SOURCES, "Light on mobs and items",
			"Lights up players, mobs and dropped items near a light.",
			Cost.NONE, false, () -> c.entityLights, value -> c.entityLights = value));
		all.add(new ToggleSetting(SOURCES, "Light on particles",
			"Lights up smoke, sparks and other particles near a light.",
			Cost.NONE, false, () -> c.particleLights, value -> c.particleLights = value));
		all.add(new SliderSetting(SOURCES, "Held light height",
			"Sets how high your held light sits on your body.",
			Cost.NONE, false, 0.3F, 1.0F, 0.02F, "", () -> c.heldHeight, value -> c.heldHeight = value));
		all.add(new SliderSetting(SOURCES, "Fire flicker",
			"Makes warm lights like torches and campfires flicker. Zero stops it.",
			Cost.NONE, false, 0.0F, 1.0F, 0.05F, "", () -> c.flicker, value -> c.flicker = value));
		all.add(new ToggleSetting(SOURCES, "Water puts out lights",
			"Puts out torches, campfires and similar items under water.",
			Cost.NONE, false, () -> c.waterPutsOutLights, value -> c.waterPutsOutLights = value));

		all.add(new SliderSetting(PERFORMANCE, "Most lights at once",
			"Sets how many lights can be active at once. Each pixel only checks nearby lights, so a high number costs little.",
			Cost.LOW, false, 4.0F, 256.0F, 4.0F, "", () -> c.maxLights, value -> c.maxLights = Math.round(value)));
		all.add(new ToggleSetting(PERFORMANCE, "Join faraway lights",
			"Joins lights that are far from you and close together into one bigger light.",
			Cost.NONE, false, () -> c.mergeFarLights, value -> c.mergeFarLights = value));
		all.add(new ToggleSetting(PERFORMANCE, "Adaptive quality",
			"Eases shadows and bounce down when the frame rate drops, then brings them back.",
			Cost.NONE, false, () -> c.adaptiveQuality, value -> c.adaptiveQuality = value));
		all.add(new SliderSetting(PERFORMANCE, "Light range",
			"Sets how far Luceat looks for lights. Lights past it fade out.",
			Cost.LOW, false, 16.0F, 128.0F, 8.0F, " blocks", () -> c.lightRange, value -> c.lightRange = Math.round(value)));
		all.add(new SliderSetting(PERFORMANCE, "Lights with shadows",
			"Sets how many of the strongest nearby lights cast shadows. The rest still shine, without shadows.",
			Cost.HIGH, false, 0.0F, 12.0F, 1.0F, "", () -> c.shadowedLights, value -> c.shadowedLights = Math.round(value)));
		all.add(new SliderSetting(PERFORMANCE, "Shadow distance",
			"Fades shadows out past this distance. Lower is faster.",
			Cost.MEDIUM, false, 8.0F, 128.0F, 8.0F, " blocks", () -> c.shadowDistance, value -> c.shadowDistance = Math.round(value)));

		all.add(new ChoiceSetting<>(GENERAL, "When a shader pack is on",
			"Off: Luceat stays out of the way. Overwrite: turns the pack off so Luceat shows. Compatible: keeps the pack and lights held items, mobs, dropped items and particles with the game's own light.",
			Cost.NONE, false, ShaderPackMode.values(), new String[]{"Disable", "Overwrite", "Compatible"}, () -> c.shaderPacks, value -> c.shaderPacks = value)
			.withNotes("Disable Luceat while a shader pack is on.", "Turn the shader pack off so Luceat's lighting shows.", "Keep the shader pack and light held items, mobs, dropped items and particles with the game's own light."));
		all.add(new ToggleSetting(GENERAL, "Update notices",
			"Checks Modrinth at startup and tells you in chat about new versions.",
			Cost.NONE, false, () -> c.updateChecker, value -> c.updateChecker = value));

		all.add(new ToggleSetting(ADVANCED, "Skip buried lights",
			"Ignores light blocks with no open side, like the inside of a lava lake. Saves a lot in the Nether.",
			Cost.NONE, false, () -> c.skipBuried, value -> {
				c.skipBuried = value;
				ShadowVolume.reset();
			}));
		all.add(new ToggleSetting(ADVANCED, "Hide lights behind blocks",
			"Shows a placed light only where the game's own light reaches. Stops lava glowing through the ground.",
			Cost.NONE, false, () -> c.hideBehindBlocks, value -> c.hideBehindBlocks = value));
		all.add(new ToggleSetting(ADVANCED, "Join crowded lights",
			"Turns lava lakes and walls of torches into a few strong lights instead of hundreds.",
			Cost.NONE, false, () -> c.clusterLights, value -> c.clusterLights = value));
		all.add(new SliderSetting(ADVANCED, "Crowd size",
			"Sets how many lights a 16 block chunk holds before they join.",
			Cost.LOW, false, 4.0F, 32.0F, 1.0F, "", () -> c.clusterSize, value -> c.clusterSize = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Join lights past",
			"Sets the distance where far lights start joining. Needs Join faraway lights.",
			Cost.LOW, false, 16.0F, 64.0F, 4.0F, " blocks", () -> c.joinBeyond, value -> c.joinBeyond = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Search time",
			"Sets the time per frame spent finding new light blocks. Lower is smoother but new lights show up later.",
			Cost.LOW, false, 0.1F, 3.0F, 0.1F, " ms", () -> c.scanBudget, value -> c.scanBudget = value));
		all.add(new SliderSetting(ADVANCED, "Light easing",
			"Sets how slowly lights fade in, fade out and change size. Zero is instant and can look jumpy.",
			Cost.NONE, false, 0.0F, 2.0F, 0.1F, "x", () -> c.lightEase, value -> c.lightEase = value));
		all.add(new SliderSetting(ADVANCED, "Corner light steps",
			"Sets how far corner light travels each frame. Lower is cheaper, higher fills rooms faster.",
			Cost.LOW, false, 1.0F, 4.0F, 1.0F, "", () -> c.spreadSteps, value -> c.spreadSteps = Math.round(value)));
		all.add(new ToggleSetting(ADVANCED, "Rest corner light",
			"Stops updating corner light while nothing moves.",
			Cost.NONE, false, () -> c.spreadRests, value -> c.spreadRests = value));
		all.add(new SliderSetting(ADVANCED, "Shadow map updates",
			"Sets how many 16 block pieces of the shadow map rebuild per frame. Higher catches up faster after you build.",
			Cost.LOW, false, 1.0F, 8.0F, 1.0F, "", () -> c.shadowUpdates, value -> c.shadowUpdates = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Lower quality below",
			"Lowers quality when the frame rate stays under this.",
			Cost.NONE, false, 20.0F, 90.0F, 1.0F, " fps", () -> c.slowBelowFps, value -> c.slowBelowFps = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Raise quality above",
			"Raises quality again when the frame rate stays over this.",
			Cost.NONE, false, 30.0F, 144.0F, 1.0F, " fps", () -> c.fastAboveFps, value -> c.fastAboveFps = Math.round(value)));
		all.add(new SliderSetting(ADVANCED, "Lowest quality",
			"Sets the lowest quality adaptive quality can reach. At 1 it never drops.",
			Cost.NONE, false, 0.3F, 1.0F, 0.05F, "", () -> c.lowestQuality, value -> c.lowestQuality = value));
		all.add(new ToggleSetting(ADVANCED, "Pause when it freezes",
			"Pauses lights if the game stays under 4 fps for several seconds. /luceat resume turns them back on. Off by default.",
			Cost.NONE, false, () -> c.pauseOnFreeze, value -> c.pauseOnFreeze = value));
		word(all);
		return all;
	}

	private static void word(List<Setting> all) {
		String[][] table = {
			{"Luceat", "Luceat", "Luceat Enabled", "Luceat Disabled"},
			{"Light strength", "Light Strength", "How bright every light is.", null},
			{"Brightness limit", "Brightness Cap", "The brightest a spot can get when lights stack up.", null},
			{"Fade in daylight", "Daylight Fade", "How much Luceat's light fades out in daylight.", null},
			{"Surface direction", "Surface Shading", "How much more light a surface gets when it faces the light.", null},
			{"Light bounce", "Light Bounce", "How much light stays in the shadows to simulate light bounce.", null},
			{"Light around corners", "Corner Light", "Light spreads around corners and through gaps.", "Light stops at corners."},
			{"Corner light strength", "Corner Strength", "How strong the light around corners is.", null},
			{"Shiny surfaces", "Shiny Surfaces", "How much polished blocks, metal and water catch the light.", null},
			{"Colours from textures", "Texture Colours", "Lights take their colour from the item's texture, so resource packs change it.", "Lights use fixed colours."},
			{"Glow on enchanted items", "Enchant Glow", "Enchanted items in your hand give off a faint purple glow.", "Enchanted items don't glow."},
			{"Sun shadows", "Sun Shadows", "Trees, roofs and cliffs cast sun shadows near you.", "The sun casts no shadows."},
			{"Sun shadow strength", "Sun Shadow Darkness", "How dark sun shadows are.", null},
			{"Water light patterns", "Water Patterns", "Moving streaks of sunlight show on pool and sea floors.", "Water floors stay plain."},
			{"Shadows", "Light Shadows", "Lights cast shadows. Blocks stop their light.", "Light passes through blocks."},
			{"Shadow darkness", "Shadow Darkness", "How dark shadows from lights get.", null},
			{"Keep lights out of walls", "Keep Out of Walls", "A light stuck inside a wall moves out, so the wall doesn't go dark.", "Lights stay exactly where they are, even inside walls."},
			{"Soft edges", "Soft Shadows", "How blurry shadow edges are. Higher looks softer and costs more.", null},
			{"Fences, panes, slabs and more", "Thin Block Shadows", "Fences, bars, doors, stairs and slabs cast their real shape.", "Fences, bars, doors, stairs and slabs cast no shadow."},
			{"Grass and flowers", "Plant Shadows", "Grass and flowers cast soft shadows.", "Plants cast no shadows."},
			{"Leaves and holes", "Leaf Shadows", "How leaves, grates and other blocks with holes block light.", null},
			{"Coloured glass", "Stained Glass", "Light takes the colour of the stained glass it passes through.", "Stained glass doesn't tint light."},
			{"Hole strength", "Leaf Density", "How solid leaves and grates count. Lower lets more light through.", null},
			{"Placed lights", "Placed Lights", "Glowing blocks light up their surroundings in their own colour, with shadows.", "Glowing blocks use the game's normal light."},
			{"Placed light strength", "Placed Strength", "How bright placed lights are.", null},
			{"Placed light reach", "Placed Reach", "How far placed light travels before it fades.", null},
			{"Blocks from other mods", "Modded Blocks", "Glowing blocks from other mods get a Luceat light, coloured from their texture.", "Glowing blocks from other mods use the game's normal light."},
			{"Replace normal light", "Replace Vanilla Light", "How much of Minecraft's flat block light Luceat replaces.", null},
			{"Smallest light", "Minimum Level", "Skips light blocks dimmer than this level, like candles and small buds.", null},
			{"Your held lights", "Held Items", "Items in your hands light up the world around you.", "Items in your hands give no light."},
			{"Other players", "Other Players", "Items other players hold light up their surroundings.", "Other players' items give no light."},
			{"Mobs holding items", "Mob Items", "Mobs holding a light item give it off.", "Mobs give no light from items."},
			{"Dropped items", "Dropped Items", "Items on the ground give off light.", "Items on the ground give no light."},
			{"Items in item frames", "Item Frames", "Items in item frames light up the wall around them.", "Items in item frames give no light."},
			{"Fire, glowing mobs and more", "Fire and Mobs", "Blazes, burning mobs, allays, glow squid, magma cubes, fireballs and end crystals give off light.", "Fire and glowing mobs give no light."},
			{"Light on mobs and items", "Lit Entities", "Players, mobs and dropped items get lit by nearby lights.", "Players, mobs and dropped items ignore Luceat's light."},
			{"Light on particles", "Lit Particles", "Smoke, sparks and other particles get lit by nearby lights.", "Particles ignore Luceat's light."},
			{"Held light height", "Held Light Height", "How high your held light sits on your body.", null},
			{"Fire flicker", "Flicker", "How much warm lights like torches and campfires flicker.", null},
			{"Water puts out lights", "Water Puts Out Lights", "Torches, campfires and similar items go out under water.", "Lights keep burning under water."},
			{"Most lights at once", "Light Limit", "The most lights active at once. Each pixel checks only nearby ones, so a high number costs little.", null},
			{"Join faraway lights", "Merge Far Lights", "Far away lights that sit close together join into one bigger light.", "Far away lights stay separate."},
			{"Adaptive quality", "Adaptive Quality", "Eases shadows and bounce down when your frame rate drops, then brings them back.", "Quality stays fixed whatever your frame rate does."},
			{"Light range", "Light Range", "How far Luceat looks for lights. Lights past it fade out.", null},
			{"Lights with shadows", "Shadowed Lights", "How many of the strongest nearby lights cast shadows. The rest shine without them.", null},
			{"Shadow distance", "Shadow Distance", "Shadows fade out past this distance. Lower is faster.", null},
			{"When a shader pack is on", "Shader Pack Settings", "What Luceat does while a shader pack is on.", null},
			{"Update notices", "Update Notices", "Checks Modrinth at startup and tells you in chat if a new update is available.", "No update checks. You won't hear about new versions."},
			{"Skip buried lights", "Skip Buried Lights", "Ignores light blocks with no open side, like the inside of a lava lake.", "Every light block counts, even buried ones."},
			{"Hide lights behind blocks", "Hide Behind Walls", "A placed light shows only where the game's own light reaches, so lava can't glow through the ground.", "Placed light can show through walls and ground."},
			{"Join crowded lights", "Merge Crowds", "Lava lakes and walls of torches become a few strong lights instead of hundreds.", "Every light in a crowd stays separate."},
			{"Crowd size", "Crowd Size", "How many lights a 16 block chunk holds before they join.", null},
			{"Join lights past", "Merge Distance", "How far away lights start joining. Needs Merge Far Lights.", null},
			{"Search time", "Search Time", "Time per frame spent finding new light blocks. Lower is smoother, but new lights show up later.", null},
			{"Light easing", "Fade Speed", "How slowly lights fade in, fade out and change size. At zero they change instantly.", null},
			{"Corner light steps", "Spread Speed", "How far corner light travels each frame. Higher fills rooms faster and costs more.", null},
			{"Rest corner light", "Pause Corner Light", "Corner light stops updating while nothing moves.", "Corner light updates every frame."},
			{"Shadow map updates", "Shadow Refresh", "How many 16 block pieces of the shadow map rebuild each frame. Higher catches up faster after you build.", null},
			{"Lower quality below", "Drop Below", "Quality drops when your frame rate stays under this.", null},
			{"Raise quality above", "Recover Above", "Quality comes back when your frame rate stays over this.", null},
			{"Lowest quality", "Quality Floor", "The lowest quality adaptive quality can reach. At 1 it never drops.", null},
			{"Pause when it freezes", "Freeze Guard", "Pauses lights if the game stays under 4 fps for several seconds. /luceat resume brings them back.", "Lights keep running through a freeze."},
		};
		for (String[] row : table) {
			for (Setting setting : all) {
				if (setting.id().equals(row[0])) {
					setting.text(row[1], row[2], row[3]);
				}
			}
		}
	}
}
