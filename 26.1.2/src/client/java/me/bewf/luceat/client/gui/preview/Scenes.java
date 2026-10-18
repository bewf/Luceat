package me.bewf.luceat.client.gui.preview;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;
import me.bewf.luceat.client.gui.preview.Light.Source;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class Scenes {
	private static final Map<String, Scene> CACHE = new HashMap<>();
	private static final Map<String, Supplier<Scene>> MAKERS = new HashMap<>();

	private static final float[] WARM = {1.0F, 0.72F, 0.38F};
	private static final float[] SOUL = {0.35F, 0.7F, 1.0F};
	private static final float[] RED = {1.0F, 0.25F, 0.2F};
	private static final float[] GREEN = {0.3F, 1.0F, 0.4F};
	private static final float[] PURPLE = {0.7F, 0.35F, 1.0F};

	static {
		MAKERS.put("room", Scenes::room);
		MAKERS.put("shadows", Scenes::shadows);
		MAKERS.put("hall", Scenes::hall);
		MAKERS.put("many", Scenes::many);
		MAKERS.put("soft", Scenes::soft);
		MAKERS.put("tunnel", Scenes::tunnel);
		MAKERS.put("foliage", Scenes::foliage);
		MAKERS.put("glass", Scenes::glass);
		MAKERS.put("thin", Scenes::thin);
		MAKERS.put("plants", Scenes::plants);
		MAKERS.put("placed", Scenes::placed);
		MAKERS.put("levels", Scenes::levels);
		MAKERS.put("held", Scenes::held);
		MAKERS.put("others", Scenes::others);
		MAKERS.put("dropped", Scenes::dropped);
		MAKERS.put("frames", Scenes::frames);
		MAKERS.put("natural", Scenes::natural);
		MAKERS.put("water", Scenes::water);
		MAKERS.put("enchant", Scenes::enchant);
		MAKERS.put("colours", Scenes::colours);
		MAKERS.put("day", Scenes::room);
		MAKERS.put("showcase", Scenes::showcase);
	}

	private Scenes() {
	}

	public static void clear() {
		CACHE.clear();
	}

	public static Scene scene(String id) {
		Scene known = CACHE.get(id);
		if (known == null) {
			known = MAKERS.getOrDefault(id, Scenes::room).get();
			CACHE.put(id, known);
		}
		return known;
	}

	public static SceneSpec fallback() {
		return new SceneSpec(scene("room"), "A small room with one lamp. Drag the light around.", null, null, null, null);
	}

	public static SceneSpec forSetting(String label) {
		if (label == null) {
			return fallback();
		}
		return switch (label) {
			case "Luceat" -> pair("room", "Without Luceat the room is dark. With it the lamp lights the room.", c -> c.enabled = false);
			case "Light strength" -> pair("room", "Light strength changes how bright every light is.", c -> c.brightness = 0.6F);
			case "Brightness limit" -> pair("many", "The limit stops stacked lights from turning white.", c -> c.maxBrightness = 3.0F);
			case "Fade in daylight" -> new SceneSpec(scene("day"), "In daylight the lamp fades out so it does not look odd.", c -> c.daylightFade = 0.0F, "Off", "On", 0.85F);
			case "Surface direction" -> pair("room", "Surfaces facing the light are brighter than surfaces facing away.", c -> c.directionalShading = 0.0F);
			case "Colours from textures" -> pair("colours", "Each light takes its colour from the item texture.", c -> c.textureColours = false);
			case "Glow on enchanted items" -> pair("enchant", "Enchanted items give a soft purple glow.", c -> c.enchantGlow = false);
			case "Shadows", "Shadow darkness" -> pair("shadows", "Blocks stop light and leave a shadow behind them.", c -> {
				if (label.equals("Shadows")) {
					c.shadows = false;
				} else {
					c.shadowStrength = 0.3F;
				}
			});
			case "Shadow distance" -> pair("hall", "Shadows only appear close to you. Far away lights stay simple and cheap.", c -> c.shadowDistance = 8);
			case "Soft edges" -> pair("soft", "Soft edges blur the shadow so it looks more natural.", c -> c.shadowSoftness = 0.0F);
			case "Keep lights out of walls" -> pair("tunnel", "In a tight tunnel the held light stays clear of the wall so the light does not flicker.", c -> c.keepLightOutOfWalls = false);
			case "Leaves and holes", "Hole strength" -> pair("foliage", "Leaves let some light through the holes.", c -> {
				if (label.equals("Hole strength")) {
					c.cutoutOpacity = 0.3F;
				} else {
					c.foliageShadows = ShadowQuality.OFF;
				}
			});
			case "Coloured glass" -> pair("glass", "Coloured glass tints the light that passes through.", c -> c.glassTint = false);
			case "Fences, panes, slabs and more" -> pair("thin", "Thin blocks like fences, panes and slabs cast their real shape.", c -> c.thinShadows = false);
			case "Grass and flowers" -> pair("plants", "Grass and flowers cast small shadows.", c -> c.plantShadows = false);
			case "Placed lights", "Placed light strength" -> pair("placed", "Lamps, torches and other light blocks light the world.", c -> {
				if (label.equals("Placed lights")) {
					c.placedLights = false;
				} else {
					c.placedStrength = 0.4F;
				}
			});
			case "Replace normal light" -> pair("placed", "Placed lights replace the flat vanilla light with real light.", c -> c.vanillaReplace = 0.0F);
			case "Smallest light" -> pair("levels", "Lights below the smallest level are ignored. Cheap for lots of small lights.", c -> c.placedMinLevel = 1);
			case "Your held lights", "Held light height" -> pair("held", "Your held light sits in front of you at hand height.", c -> {
				if (label.equals("Your held lights")) {
					c.heldLights = false;
				} else {
					c.heldHeight = 0.3F;
				}
			});
			case "Other players", "Mobs holding items" -> pair("others", "Other players and mobs light up when they hold a light.", c -> {
				if (label.equals("Other players")) {
					c.otherPlayers = false;
				} else {
					c.mobs = false;
				}
			});
			case "Dropped items" -> pair("dropped", "Dropped items glow. Items close together join into one light.", c -> c.droppedItems = false);
			case "Items in item frames" -> pair("frames", "Items in item frames light the wall around them.", c -> c.itemFrames = false);
			case "Fire, glowing mobs and more", "Fire flicker" -> pair("natural", "Fire, blazes, magma cubes and more give off light.", c -> {
				if (label.equals("Fire flicker")) {
					c.flicker = 0.0F;
				} else {
					c.naturalLights = false;
				}
			});
			case "Water puts out lights" -> pair("water", "A light under water goes out.", c -> c.waterPutsOutLights = false);
			case "Most lights at once" -> pair("many", "Only the closest lights are used. More lights costs more.", c -> c.maxLights = 4);
			case "Light range" -> pair("hall", "Lights further away than the range are skipped.", c -> c.lightRange = 8);
			default -> fallback();
		};
	}

	private static SceneSpec pair(String id, String caption, Consumer<LuceatConfig> baseline) {
		return new SceneSpec(scene(id), caption, baseline, "Off", "On", null);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static BlockState with(BlockState state, String name, String value) {
		Property property = state.getBlock().getStateDefinition().getProperty(name);
		if (property == null) {
			return state;
		}
		Optional parsed = property.getValue(value);
		return parsed.isPresent() ? state.setValue(property, (Comparable) parsed.get()) : state;
	}

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static Scene.Builder stage(String id, String title, int w, int h, int d) {
		Scene.Builder s = new Scene.Builder(id, title, w, h, d);
		s.fill(0, 0, 0, w - 1, 0, d - 1, b(Blocks.STONE_BRICKS));
		s.fill(0, 1, d - 1, w - 1, h - 1, d - 1, b(Blocks.STONE_BRICKS));
		s.fill(0, 1, 0, 0, h - 1, d - 1, b(Blocks.STONE_BRICKS));
		s.fill(w - 1, 1, 0, w - 1, h - 1, d - 1, b(Blocks.STONE_BRICKS));
		return s;
	}

	private static Light lamp(Source source, double x, double y, double z, int level, float[] c) {
		return Light.of(source, x, y, z, level, c[0], c[1], c[2]);
	}

	public static SceneSpec showcase(String leftLabel, String rightLabel, Consumer<LuceatConfig> baseline) {
		return new SceneSpec(scene("showcase"), "Your settings on the left and the preset on the right.", baseline, leftLabel, rightLabel, null);
	}

	private static Scene showcase() {
		Scene.Builder s = stage("showcase", "Showcase", 13, 5, 9);
		BlockState fence = b(Blocks.OAK_FENCE);
		s.set(2, 1, 4, fence).set(3, 1, 4, with(with(fence, "east", "true"), "west", "true")).set(4, 1, 4, with(fence, "west", "true"));
		s.fill(6, 1, 5, 6, 3, 5, b(Blocks.STONE_BRICKS));
		BlockState leaves = with(b(Blocks.OAK_LEAVES), "persistent", "true");
		s.fill(8, 1, 4, 9, 2, 5, leaves);
		s.fill(11, 1, 3, 11, 2, 3, b(Blocks.RED_STAINED_GLASS));
		s.set(1, 1, 6, b(Blocks.SHORT_GRASS)).set(2, 1, 6, b(Blocks.POPPY)).set(3, 1, 6, b(Blocks.FERN));
		s.light(lamp(Source.ALWAYS, 5.5, 2.4, 2.0, 14, WARM).asFire());
		s.light(lamp(Source.PLACED, 10.5, 1.6, 6.5, 12, SOUL));
		s.eye = new double[]{6.5, 3.4, -3.4};
		s.target = new double[]{6.5, 0.9, 4.5};
		return s.caption("").movable().build();
	}

	private static Scene room() {
		Scene.Builder s = stage("room", "Room", 9, 5, 9);
		s.fill(5, 1, 3, 6, 2, 4, b(Blocks.OAK_PLANKS));
		s.fill(1, 1, 5, 2, 1, 6, b(Blocks.WHITE_WOOL));
		s.fill(3, 1, 7, 3, 3, 7, b(Blocks.COBBLESTONE));
		s.light(lamp(Source.ALWAYS, 4.5, 2.2, 4.5, 14, WARM));
		return s.caption("Drag the light around.").movable().build();
	}

	private static Scene shadows() {
		Scene.Builder s = stage("shadows", "Shadows", 11, 5, 9);
		s.fill(5, 1, 4, 5, 3, 4, b(Blocks.STONE_BRICKS));
		s.fill(3, 1, 6, 4, 2, 7, b(Blocks.OAK_PLANKS));
		s.fill(7, 1, 6, 8, 1, 7, b(Blocks.COBBLESTONE));
		s.light(lamp(Source.ALWAYS, 5.5, 2.2, 2.2, 14, WARM));
		return s.caption("The pillar blocks the light. Drag the light to move the shadow.").movable().build();
	}

	private static Scene hall() {
		Scene.Builder s = stage("hall", "Hall", 5, 4, 26);
		s.eye = new double[]{2.5, 2.2, -2.5};
		s.target = new double[]{2.5, 1.4, 14.0};
		s.fov = 62.0;
		for (int z = 3; z < 26; z += 5) {
			s.fill(2, 1, z + 1, 2, 2, z + 1, b(Blocks.COBBLESTONE));
			s.light(lamp(Source.ALWAYS, 2.5, 2.4, z + 0.5, 12, WARM));
		}
		s.rangeScale = 0.3F;
		s.shadowScale = 0.3F;
		return s.caption("A long hall with a light every few blocks.").build();
	}

	private static Scene many() {
		Scene.Builder s = stage("many", "Many lights", 13, 4, 13);
		s.capLights = true;
		int n = 0;
		for (int z = 2; z < 12; z += 3) {
			for (int x = 2; x < 12; x += 3) {
				float[] c = n % 3 == 0 ? WARM : n % 3 == 1 ? SOUL : GREEN;
				s.light(lamp(Source.ALWAYS, x + 0.5, 1.6, z + 0.5, 8, c));
				s.set(x, 1, z, b(Blocks.COBBLESTONE_WALL));
				n++;
			}
		}
		s.eye = new double[]{6.5, 5.0, -3.0};
		s.target = new double[]{6.5, 0.5, 7.0};
		return s.caption("Lots of lights. Only the closest ones are used.").build();
	}

	private static Scene soft() {
		Scene.Builder s = stage("soft", "Soft edges", 9, 4, 8);
		s.fill(4, 1, 3, 4, 2, 3, b(Blocks.STONE_BRICKS));
		s.eye = new double[]{4.5, 3.0, -2.0};
		s.target = new double[]{4.5, 0.6, 4.5};
		s.light(lamp(Source.ALWAYS, 4.5, 2.8, 1.4, 13, WARM));
		return s.caption("Look at the edge of the shadow.").movable().build();
	}

	private static Scene tunnel() {
		Scene.Builder s = new Scene.Builder("tunnel", "Tunnel", 5, 4, 16);
		s.fill(0, 0, 0, 4, 0, 15, b(Blocks.STONE));
		s.fill(0, 1, 0, 1, 3, 15, b(Blocks.STONE));
		s.fill(3, 1, 0, 4, 3, 15, b(Blocks.STONE));
		s.fill(2, 3, 0, 2, 3, 15, b(Blocks.STONE));
		s.fill(2, 1, 15, 2, 3, 15, b(Blocks.STONE));
		s.eye = new double[]{2.5, 1.7, -2.5};
		s.target = new double[]{2.5, 1.5, 8.0};
		s.fov = 66.0;
		s.keepAway = true;
		s.light(lamp(Source.HELD, 2.5, 1.5, 4.0, 14, SOUL));
		return s.caption("A tunnel one block wide. The light moves along it.").movable().build();
	}

	private static Scene foliage() {
		Scene.Builder s = stage("foliage", "Leaves", 9, 5, 8);
		s.fill(3, 1, 3, 3, 2, 3, b(Blocks.OAK_LOG));
		BlockState leaves = with(b(Blocks.OAK_LEAVES), "persistent", "true");
		s.fill(2, 3, 2, 4, 3, 4, leaves);
		s.fill(3, 4, 3, 3, 4, 3, leaves);
		s.fill(5, 1, 5, 6, 2, 6, leaves);
		s.light(lamp(Source.ALWAYS, 3.5, 4.8, 3.5, 14, WARM));
		s.eye = new double[]{4.5, 3.3, -2.5};
		s.target = new double[]{4.0, 1.0, 4.5};
		return s.caption("Light falls through the holes in the leaves.").movable().build();
	}

	private static Scene glass() {
		Scene.Builder s = stage("glass", "Glass", 9, 4, 8);
		s.fill(2, 1, 3, 3, 2, 3, b(Blocks.RED_STAINED_GLASS));
		s.fill(5, 1, 3, 6, 2, 3, b(Blocks.BLUE_STAINED_GLASS));
		s.light(lamp(Source.ALWAYS, 4.5, 1.8, 1.4, 14, new float[]{1.0F, 1.0F, 0.9F}));
		s.eye = new double[]{4.5, 3.0, -2.0};
		s.target = new double[]{4.5, 0.7, 4.5};
		return s.caption("The light turns red and blue behind the glass.").movable().build();
	}

	private static Scene thin() {
		Scene.Builder s = stage("thin", "Thin blocks", 11, 4, 8);
		BlockState fence = b(Blocks.OAK_FENCE);
		s.set(1, 1, 3, fence).set(2, 1, 3, with(with(fence, "east", "true"), "west", "true")).set(3, 1, 3, with(fence, "west", "true"));
		BlockState bars = b(Blocks.IRON_BARS);
		s.set(5, 1, 3, bars).set(5, 2, 3, bars);
		BlockState pane = b(Blocks.GLASS_PANE);
		s.set(7, 1, 3, pane).set(7, 2, 3, pane);
		s.set(9, 1, 3, with(b(Blocks.OAK_SLAB), "type", "top")).set(9, 1, 4, with(b(Blocks.OAK_SLAB), "type", "bottom"));
		s.light(lamp(Source.ALWAYS, 5.5, 1.8, 1.0, 13, WARM));
		s.eye = new double[]{5.5, 3.0, -2.2};
		s.target = new double[]{5.5, 0.7, 4.5};
		return s.caption("Fences, bars, panes and slabs each cast their own shadow.").movable().build();
	}

	private static Scene plants() {
		Scene.Builder s = new Scene.Builder("plants", "Plants", 9, 4, 8);
		s.fill(0, 0, 0, 8, 0, 7, b(Blocks.GRASS_BLOCK));
		s.fill(0, 1, 7, 8, 3, 7, b(Blocks.STONE_BRICKS));
		BlockState[] list = {b(Blocks.SHORT_GRASS), b(Blocks.POPPY), b(Blocks.DANDELION), b(Blocks.FERN)};
		for (int x = 1; x < 8; x++) {
			for (int z = 3; z < 6; z++) {
				s.set(x, 1, z, list[(x * 3 + z) % list.length]);
			}
		}
		s.light(lamp(Source.ALWAYS, 4.5, 1.15, 1.6, 12, WARM));
		s.eye = new double[]{4.5, 2.6, -1.5};
		s.target = new double[]{4.5, 0.8, 4.5};
		return s.caption("A low light behind the plants. Look at the small shadows.").movable().build();
	}

	private static Scene placed() {
		Scene.Builder s = stage("placed", "Placed lights", 11, 4, 8);
		s.vanilla = true;
		s.set(2, 1, 4, b(Blocks.LANTERN)).set(4, 1, 4, b(Blocks.TORCH)).set(6, 1, 4, b(Blocks.GLOWSTONE)).set(8, 1, 4, b(Blocks.SEA_LANTERN));
		s.light(lamp(Source.PLACED, 2.5, 1.4, 4.5, 15, WARM).named("Lantern"));
		s.light(lamp(Source.PLACED, 4.5, 1.7, 4.5, 14, WARM).asFire().named("Torch"));
		s.light(lamp(Source.PLACED, 6.5, 1.5, 4.5, 15, new float[]{1.0F, 0.85F, 0.5F}).named("Glowstone"));
		s.light(lamp(Source.PLACED, 8.5, 1.5, 4.5, 15, new float[]{0.6F, 0.9F, 1.0F}).named("Sea lantern"));
		s.eye = new double[]{5.5, 3.0, -1.5};
		s.target = new double[]{5.5, 0.8, 4.5};
		return s.caption("Real light blocks. Each has its own colour.").build();
	}

	private static Scene levels() {
		Scene.Builder s = stage("levels", "Light levels", 11, 4, 8);
		int[] levels = {1, 3, 5, 8, 11, 15};
		for (int i = 0; i < levels.length; i++) {
			double x = 1.5 + i * 1.6;
			s.set((int) x, 1, 4, b(Blocks.CANDLE));
			s.light(lamp(Source.PLACED, x, 1.3, 4.5, levels[i], WARM).named("L" + levels[i]));
		}
		s.eye = new double[]{5.5, 3.0, -1.5};
		s.target = new double[]{5.5, 0.8, 4.5};
		return s.caption("Lights of level 1, 3, 5, 8, 11 and 15.").build();
	}

	private static Scene held() {
		Scene.Builder s = stage("held", "Held light", 9, 4, 9);
		s.fill(3, 1, 6, 5, 2, 6, b(Blocks.COBBLESTONE));
		s.fill(6, 1, 3, 6, 1, 3, b(Blocks.OAK_PLANKS));
		s.eye = new double[]{4.5, 3.0, -2.5};
		s.target = new double[]{4.5, 1.0, 4.5};
		s.light(lamp(Source.HELD, 4.5, 1.0, 3.0, 14, WARM).asFire().named("You"));
		return s.caption("The light you hold. Drag it forward and back.").movable().build();
	}

	private static Scene others() {
		Scene.Builder s = stage("others", "Other players", 11, 4, 9);
		s.fill(5, 1, 6, 5, 2, 6, b(Blocks.COBBLESTONE));
		s.eye = new double[]{5.5, 3.0, -2.5};
		s.target = new double[]{5.5, 1.0, 4.5};
		s.light(lamp(Source.PLAYER, 2.5, 1.6, 3.5, 14, WARM).named("Player"));
		s.light(lamp(Source.MOB, 8.5, 1.4, 3.5, 12, SOUL).named("Zombie"));
		return s.caption("A player and a mob, both holding lights.").build();
	}

	private static Scene dropped() {
		Scene.Builder s = stage("dropped", "Dropped items", 11, 4, 8);
		s.eye = new double[]{5.5, 3.0, -1.5};
		s.target = new double[]{5.5, 0.8, 4.5};
		s.light(lamp(Source.DROPPED, 2.2, 1.2, 3.5, 6, WARM));
		s.light(lamp(Source.DROPPED, 2.6, 1.2, 3.9, 6, WARM));
		s.light(lamp(Source.DROPPED, 7.5, 1.2, 3.5, 9, SOUL).named("Pile"));
		return s.caption("Items close together join into one stronger light.").movable().build();
	}

	private static Scene frames() {
		Scene.Builder s = stage("frames", "Item frames", 11, 4, 6);
		s.eye = new double[]{5.5, 2.4, -2.0};
		s.target = new double[]{5.5, 1.6, 5.0};
		s.light(lamp(Source.FRAME, 3.5, 1.8, 4.6, 8, WARM));
		s.light(lamp(Source.FRAME, 7.5, 1.8, 4.6, 8, GREEN));
		return s.caption("Items in frames light the wall.").build();
	}

	private static Scene natural() {
		Scene.Builder s = stage("natural", "Fire and mobs", 11, 4, 8);
		s.eye = new double[]{5.5, 3.0, -1.5};
		s.target = new double[]{5.5, 0.8, 4.5};
		s.light(lamp(Source.NATURAL, 2.5, 1.6, 4.0, 10, new float[]{1.0F, 0.55F, 0.2F}).asFire().named("Blaze"));
		s.light(lamp(Source.NATURAL, 5.5, 1.3, 4.0, 8, new float[]{0.35F, 0.8F, 1.0F}).named("Allay"));
		s.light(lamp(Source.NATURAL, 8.5, 1.2, 4.0, 8, new float[]{1.0F, 0.4F, 0.1F}).asFire().named("Magma cube"));
		return s.caption("Blazes, allays and magma cubes glow.").build();
	}

	private static Scene water() {
		Scene.Builder s = stage("water", "Water", 9, 4, 8);
		s.fill(2, 1, 2, 6, 1, 5, b(Blocks.LIGHT_BLUE_STAINED_GLASS));
		s.set(4, 1, 4, b(Blocks.STONE_BRICKS));
		s.eye = new double[]{4.5, 3.2, -1.5};
		s.target = new double[]{4.5, 0.8, 4.0};
		s.light(lamp(Source.HELD, 3.5, 1.0, 3.5, 13, WARM).asWet().named("Torch under water"));
		s.light(lamp(Source.HELD, 7.5, 1.4, 3.5, 13, WARM).named("Torch in air"));
		return s.caption("The left light is under water.").build();
	}

	private static Scene enchant() {
		Scene.Builder s = stage("enchant", "Enchanted", 9, 4, 8);
		s.eye = new double[]{4.5, 3.0, -1.5};
		s.target = new double[]{4.5, 0.8, 4.5};
		s.light(lamp(Source.ENCHANT, 4.5, 1.2, 3.5, 6, PURPLE).named("Enchanted sword"));
		return s.caption("A soft purple glow around an enchanted item.").build();
	}

	private static Scene colours() {
		Scene.Builder s = stage("colours", "Colours", 11, 4, 8);
		s.eye = new double[]{5.5, 3.0, -1.5};
		s.target = new double[]{5.5, 0.8, 4.5};
		s.light(lamp(Source.HELD, 2.5, 1.4, 4.0, 12, RED).asTextured().named("Redstone"));
		s.light(lamp(Source.HELD, 5.5, 1.4, 4.0, 12, GREEN).asTextured().named("Copper"));
		s.light(lamp(Source.HELD, 8.5, 1.4, 4.0, 12, SOUL).asTextured().named("Soul"));
		return s.caption("Each light uses the colour of its item.").build();
	}
}
