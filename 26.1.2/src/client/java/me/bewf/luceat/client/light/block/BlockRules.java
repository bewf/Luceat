package me.bewf.luceat.client.light.block;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import me.bewf.luceat.client.config.LuceatConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.bewf.luceat.Luceat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockRules {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, BlockRule> RULES = new LinkedHashMap<>();
	private static final Map<Block, Boolean> EMITS = new IdentityHashMap<>();
	private static final Map<Block, float[]> COLOURS = new IdentityHashMap<>();
	private static volatile Map<Block, BlockRule> byBlock = Map.of();
	private static volatile Map<Item, BlockRule> byItem = Map.of();

	private static final class Stored {
		List<BlockRule> rules = new ArrayList<>();
	}

	private BlockRules() {
	}

	public static void load() {
		Path path = path();
		RULES.clear();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				Stored stored = GSON.fromJson(reader, Stored.class);
				if (stored != null && stored.rules != null) {
					for (BlockRule rule : stored.rules) {
						if (rule != null && rule.id != null && rule.keep()) {
							RULES.put(rule.id, rule);
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				Luceat.LOGGER.warn("Could not read luceat-blocks.json", e);
			}
		}
		rebuild();
	}

	public static void save() {
		Stored stored = new Stored();
		for (BlockRule rule : RULES.values()) {
			if (rule.keep()) {
				stored.rules.add(rule);
			}
		}
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(stored, writer);
		} catch (IOException e) {
			Luceat.LOGGER.warn("Could not write luceat-blocks.json", e);
		}
	}

	public static BlockRule get(String id) {
		return RULES.get(id);
	}

	public static Collection<BlockRule> all() {
		return RULES.values();
	}

	public static BlockRule edit(String id) {
		return RULES.computeIfAbsent(id, BlockRule::new);
	}

	public static void remove(String id) {
		RULES.remove(id);
	}

	public static void changed() {
		RULES.values().removeIf(rule -> !rule.keep());
		rebuild();
		save();
	}

	public static void snapshotInto(List<BlockRule> out) {
		for (BlockRule rule : RULES.values()) {
			BlockRule copy = new BlockRule(rule.id);
			copy.enabled = rule.enabled;
			copy.level = rule.level;
			copy.colour = rule.colour;
			copy.added = rule.added;
			copy.radius = rule.radius;
			copy.flicker = rule.flicker;
			copy.shadows = rule.shadows;
			copy.water = rule.water;
			copy.shine = rule.shine;
			copy.whenPlaced = rule.whenPlaced;
			copy.whenHeld = rule.whenHeld;
			copy.whenDropped = rule.whenDropped;
			copy.whenFramed = rule.whenFramed;
			out.add(copy);
		}
	}

	public static void replaceAll(List<BlockRule> rules) {
		RULES.clear();
		for (BlockRule rule : rules) {
			RULES.put(rule.id, rule);
		}
		changed();
	}

	public static void rebuild() {
		Map<Block, BlockRule> blocks = new IdentityHashMap<>();
		Map<Item, BlockRule> items = new IdentityHashMap<>();
		EMITS.clear();
		COLOURS.clear();
		for (BlockRule rule : RULES.values()) {
			Identifier id = Identifier.tryParse(rule.id);
			if (id == null) {
				continue;
			}
			if (BuiltInRegistries.BLOCK.containsKey(id)) {
				blocks.put(BuiltInRegistries.BLOCK.getValue(id), rule);
			}
			if (BuiltInRegistries.ITEM.containsKey(id)) {
				items.put(BuiltInRegistries.ITEM.getValue(id), rule);
			}
		}
		byBlock = blocks;
		byItem = items;
	}

	public static boolean emitsByDefault(Block block) {
		Boolean known = EMITS.get(block);
		if (known == null) {
			known = block.getStateDefinition().getPossibleStates().stream().anyMatch(state -> state.getLightEmission() > 0);
			EMITS.put(block, known);
		}
		return known;
	}

	private static final Map<Block, Boolean> VANILLA = new java.util.IdentityHashMap<>();

	// blocks from other mods only count when the automatic setting is on, unless they have a rule of their own
	public static boolean allowed(Block block) {
		if (LuceatConfig.get().autoLights) {
			return true;
		}
		Boolean known = VANILLA.get(block);
		if (known == null) {
			known = "minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
			VANILLA.put(block, known);
		}
		return known;
	}

	public static int emission(BlockState state) {
		int natural = state.getLightEmission();
		Map<Block, BlockRule> map = byBlock;
		BlockRule rule = map.isEmpty() ? null : map.get(state.getBlock());
		if (rule == null) {
			return natural > 0 && !allowed(state.getBlock()) ? 0 : natural;
		}
		if (!rule.enabled) {
			return 0;
		}
		if (rule.level < 0) {
			return natural;
		}
		if (emitsByDefault(state.getBlock())) {
			return natural > 0 ? rule.level : 0;
		}
		return rule.level;
	}

	public static BlockRule rule(BlockState state) {
		Map<Block, BlockRule> map = byBlock;
		return map.isEmpty() ? null : map.get(state.getBlock());
	}

	public static float[] colour(BlockState state) {
		Map<Block, BlockRule> map = byBlock;
		if (map.isEmpty()) {
			return null;
		}
		BlockRule rule = map.get(state.getBlock());
		if (rule == null || rule.colour < 0) {
			return null;
		}
		return COLOURS.computeIfAbsent(state.getBlock(), key -> rule.rgb());
	}

	public static BlockRule forItem(Item item) {
		Map<Item, BlockRule> map = byItem;
		return map.isEmpty() ? null : map.get(item);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("luceat-blocks.json");
	}
}
