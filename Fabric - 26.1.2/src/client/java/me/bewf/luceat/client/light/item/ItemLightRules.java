package me.bewf.luceat.client.light.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.light.LightSpecs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemLightRules extends SimplePreparableReloadListener<List<ItemLightRule>> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "item_lights");
	private static final String DIRECTORY = "dynamiclights/item";

	private static volatile Index index = new Index(Map.of(), List.of());

	public static List<ItemLightRule> matching(ItemStack stack) {
		Index current = index;
		List<ItemLightRule> result = null;
		List<ItemLightRule> direct = current.byItem().get(stack.getItem());
		if (direct != null) {
			result = new ArrayList<>(direct);
		}
		for (ItemLightRule rule : current.tagged()) {
			if (rule.matches(stack)) {
				if (result == null) {
					result = new ArrayList<>();
				}
				result.add(rule);
			}
		}
		return result == null ? List.of() : result;
	}

	@Override
	protected List<ItemLightRule> prepare(ResourceManager manager, ProfilerFiller profiler) {
		List<ItemLightRule> rules = new ArrayList<>();
		Map<Identifier, Resource> found = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				ItemLightRule rule = parse(GsonHelper.parse(reader));
				if (rule != null) {
					rules.add(rule);
				}
			} catch (IOException | RuntimeException e) {
				Luceat.LOGGER.warn("Could not load item light {}", entry.getKey(), e);
			}
		}
		return rules;
	}

	@Override
	protected void apply(List<ItemLightRule> rules, ResourceManager manager, ProfilerFiller profiler) {
		Map<Item, List<ItemLightRule>> byItem = new HashMap<>();
		List<ItemLightRule> tagged = new ArrayList<>();
		for (ItemLightRule rule : rules) {
			for (Item item : rule.items()) {
				byItem.computeIfAbsent(item, key -> new ArrayList<>()).add(rule);
			}
			if (!rule.tags().isEmpty()) {
				tagged.add(rule);
			}
		}
		index = new Index(byItem, tagged);
		LightSpecs.clearCache();
	}

	private static ItemLightRule parse(JsonObject json) {
		if (!json.has("match") || !json.has("luminance") || !json.get("match").isJsonObject()) {
			return null;
		}
		JsonObject match = json.getAsJsonObject("match");
		if (match.has("components") || match.has("predicates") || !match.has("items")) {
			return null;
		}
		List<Item> items = new ArrayList<>();
		List<TagKey<Item>> tags = new ArrayList<>();
		for (String entry : strings(match.get("items"))) {
			if (entry.startsWith("#")) {
				Identifier tag = Identifier.tryParse(entry.substring(1));
				if (tag != null) {
					tags.add(TagKey.create(Registries.ITEM, tag));
				}
				continue;
			}
			Identifier id = Identifier.tryParse(entry);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				items.add(BuiltInRegistries.ITEM.getValue(id));
			}
		}
		if (items.isEmpty() && tags.isEmpty()) {
			return null;
		}
		ItemLightRule.Luminance luminance = luminance(json.get("luminance"));
		if (luminance == null) {
			return null;
		}
		boolean waterSensitive = GsonHelper.getAsBoolean(json, "water_sensitive", false);
		return new ItemLightRule(items, tags, luminance, waterSensitive, colour(json.get("color")));
	}

	private static ItemLightRule.Luminance luminance(JsonElement element) {
		if (element.isJsonPrimitive()) {
			return new ItemLightRule.Fixed(Math.clamp(element.getAsInt(), 0, 15));
		}
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		String type = GsonHelper.getAsString(object, "type", "");
		if (type.equals("block_self")) {
			return new ItemLightRule.BlockSelf();
		}
		if (type.equals("block") && object.has("block")) {
			return new ItemLightRule.BlockReference(ItemLightRule.blockById(object.get("block").getAsString()));
		}
		return null;
	}

	private static float[] colour(JsonElement element) {
		if (element == null) {
			return null;
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			if (array.size() != 3) {
				return null;
			}
			return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
		}
		if (element.isJsonPrimitive()) {
			String text = element.getAsString();
			if (text.length() == 7 && text.charAt(0) == '#') {
				int rgb = Integer.parseInt(text.substring(1), 16);
				return new float[]{(rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F};
			}
		}
		return null;
	}

	private static List<String> strings(JsonElement element) {
		List<String> values = new ArrayList<>();
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				values.add(child.getAsString());
			}
		} else if (element.isJsonPrimitive()) {
			values.add(element.getAsString());
		}
		return values;
	}

	private record Index(Map<Item, List<ItemLightRule>> byItem, List<ItemLightRule> tagged) {
	}
}
