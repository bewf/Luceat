package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.light.ColourEstimator;
import me.bewf.luceat.client.light.block.BlockRule;
import me.bewf.luceat.client.light.block.BlockRules;
import me.bewf.luceat.client.light.item.ItemLightRule;
import me.bewf.luceat.client.light.item.ItemLightRules;

import me.bewf.luceat.client.gui.glass.Icons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BlocksTab {
	private static final int ROW = 28;
	private static final int GAP = 2;
	private static final int BUILT_IN = 0xFFC4BCE8;
	private static final float[] FALLBACK = {1.0F, 0.7F, 0.35F};

	private enum Filter {
		ALL("All"), LIGHT("Lights up"), EDITED("Edited"), YOURS("Yours");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	private enum Sort {
		NAME("Name"), LEVEL("Light level"), BUILT_IN("Built in first"), EDITED("Edited first"), COLOUR("Colour");

		final String label;

		Sort(String label) {
			this.label = label;
		}
	}

	private static final class Entry {
		Identifier id;
		String key;
		String name;
		String search;
		String mod;
		Block block;
		Item item;
		ItemStack stack = ItemStack.EMPTY;
		int defaultLevel;
		float[] auto;
	}

	private final Font font;
	private final TextInput filterInput = new TextInput(40);
	private List<Entry> all;
	private final List<Entry> shown = new ArrayList<>();
	private Filter mode = Filter.LIGHT;
	private Entry selected;
	private float scroll;
	private boolean dirty;
	private boolean adding;
	private String modFilter;
	private String lastMod;
	private boolean modOpen;
	private float modScroll;
	private List<String> mods = new ArrayList<>();
	private final java.util.Map<String, String> modNames = new java.util.HashMap<>();
	private final TextInput addInput = new TextInput(40);
	private float addScroll;
	private final List<Entry> addResults = new ArrayList<>();
	private String lastAddQuery;
	private boolean draggingBar;
	private int barGrab;
	private int barTrackTop;
	private int barTrackH;
	private int barThumbTop;
	private int barThumb;
	private int barMax;
	private int modX0;
	private int modX1;
	private String lastQuery = null;
	private Filter lastMode;
	private int lastRules = -1;
	private int slider;
	private Sort sort = Sort.NAME;
	private Sort lastSort;
	private final ColourPicker picker = new ColourPicker();
	private final java.util.Set<String> collapsed = new java.util.HashSet<>();
	private final List<Hit> hits = new ArrayList<>();
	private Slide activeSlide;
	private float editorScroll;
	private int editorHeight;
	private int edTop;
	private int edBottom;

	private int listX0;
	private int listY0;
	private int listX1;
	private int listY1;
	private int edX0;
	private int edX1;

	public BlocksTab(Font font) {
		this.font = font;
	}

	public boolean typing() {
		return filterInput.focused() || adding || modOpen;
	}

	public void unfocus() {
		filterInput.focus(false);
	}

	public boolean consumeDirty() {
		boolean was = dirty;
		dirty = false;
		return was;
	}

	private void build() {
		all = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			BlockState base = block.defaultBlockState();
			if (base.isAir()) {
				continue;
			}
			Entry entry = new Entry();
			entry.id = id;
			entry.key = id.toString();
			entry.mod = id.getNamespace();
			entry.block = block;
			Item item = block.asItem();
			if (item != Items.AIR) {
				entry.item = item;
				entry.stack = new ItemStack(item);
			}
			entry.name = block.getName().getString();
			int level = 0;
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				level = Math.max(level, state.getLightEmission());
			}
			entry.defaultLevel = level;
			all.add(entry);
		}
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (BuiltInRegistries.BLOCK.containsKey(id)) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item == Items.AIR) {
				continue;
			}
			Entry entry = new Entry();
			entry.id = id;
			entry.key = id.toString();
			entry.mod = id.getNamespace();
			entry.item = item;
			entry.stack = new ItemStack(item);
			entry.name = entry.stack.getHoverName().getString();
			int level = 0;
			for (ItemLightRule rule : ItemLightRules.matching(entry.stack)) {
				level = Math.max(level, rule.level(entry.stack));
			}
			entry.defaultLevel = level;
			all.add(entry);
		}
		for (Entry entry : all) {
			entry.search = (entry.name + " " + entry.key).toLowerCase(Locale.ROOT);
		}
		all.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Entry entry : all) {
			seen.add(entry.mod);
		}
		mods = new ArrayList<>(seen);
		mods.sort((a, b) -> {
			if (a.equals("minecraft") != b.equals("minecraft")) {
				return a.equals("minecraft") ? -1 : 1;
			}
			return modName(a).compareToIgnoreCase(modName(b));
		});
	}

	private String modName(String namespace) {
		return modNames.computeIfAbsent(namespace, key -> {
			if (key.equals("minecraft")) {
				return "Minecraft";
			}
			return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(key).map(c -> c.getMetadata().getName()).orElse(key);
		});
	}

	private void refilter() {
		if (all == null) {
			build();
		}
		String query = filterInput.text().toLowerCase(Locale.ROOT).trim();
		int rules = BlockRules.all().size() * 31 + (int) BlockRules.all().stream().filter(BlockRule::edited).count();
		if (query.equals(lastQuery) && mode == lastMode && sort == lastSort && rules == lastRules && java.util.Objects.equals(modFilter, lastMod)) {
			return;
		}
		lastQuery = query;
		lastMode = mode;
		lastSort = sort;
		lastRules = rules;
		lastMod = modFilter;
		shown.clear();
		for (Entry entry : all) {
			if (!query.isEmpty() && !entry.search.contains(query)) {
				continue;
			}
			if (modFilter != null && !modFilter.equals(entry.mod)) {
				continue;
			}
			BlockRule rule = BlockRules.get(entry.key);
			boolean lit = entry.defaultLevel > 0 || rule != null && rule.added;
			boolean pass = switch (mode) {
				case ALL -> true;
				case LIGHT -> lit;
				case EDITED -> rule != null && rule.edited();
				case YOURS -> rule != null && rule.added;
			};
			if (pass) {
				shown.add(entry);
			}
		}
		shown.sort(comparator());
	}

	private String sortLabel() {
		return "Sort: " + sort.label;
	}

	private java.util.Comparator<Entry> comparator() {
		java.util.Comparator<Entry> byName = (a, b) -> a.name.compareToIgnoreCase(b.name);
		return switch (sort) {
			case NAME -> byName;
			case LEVEL -> ((java.util.Comparator<Entry>) (a, b) -> Integer.compare(effectiveLevel(b), effectiveLevel(a))).thenComparing(byName);
			case BUILT_IN -> ((java.util.Comparator<Entry>) (a, b) -> Boolean.compare(builtIn(b), builtIn(a))).thenComparing(byName);
			case EDITED -> ((java.util.Comparator<Entry>) (a, b) -> Integer.compare(editRank(b), editRank(a))).thenComparing(byName);
			case COLOUR -> ((java.util.Comparator<Entry>) (a, b) -> Float.compare(hueKey(a), hueKey(b))).thenComparing(byName);
		};
	}

	private int editRank(Entry entry) {
		BlockRule rule = BlockRules.get(entry.key);
		return rule == null ? 0 : rule.added ? 2 : rule.edited() ? 3 : 0;
	}

	// unlit entries sink to the end, lit ones go round the colour wheel
	private float hueKey(Entry entry) {
		if (effectiveLevel(entry) <= 0) {
			return 10.0F;
		}
		return ColourPicker.toHsv(pack(colour(entry)))[0];
	}

	private int effectiveLevel(Entry entry) {
		BlockRule rule = BlockRules.get(entry.key);
		if (rule == null) {
			return entry.defaultLevel;
		}
		if (!rule.enabled) {
			return 0;
		}
		return rule.level >= 0 ? rule.level : entry.defaultLevel;
	}

	private float[] colour(Entry entry) {
		BlockRule rule = BlockRules.get(entry.key);
		if (rule != null && rule.colour >= 0) {
			return rule.rgb();
		}
		if (entry.auto == null) {
			float[] estimate = null;
			try {
				estimate = entry.block != null ? ColourEstimator.estimateBlock(entry.block.defaultBlockState()) : ColourEstimator.estimate(entry.stack);
			} catch (RuntimeException ignored) {
			}
			entry.auto = estimate == null ? FALLBACK : estimate;
		}
		return entry.auto;
	}

	private static int pack(float[] rgb) {
		return 0xFF000000 | Math.round(rgb[0] * 255.0F) << 16 | Math.round(rgb[1] * 255.0F) << 8 | Math.round(rgb[2] * 255.0F);
	}

	private boolean builtIn(Entry entry) {
		return entry.defaultLevel > 0;
	}

	private int listTopOffset() {
		return 72;
	}

	private int maxScroll() {
		return Math.max(0, shown.size() * (ROW + GAP) - (listY1 - listY0 - listTopOffset()));
	}

	public void drawList(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int mx, int my) {
		listX0 = x0;
		listY0 = y0;
		listX1 = x1;
		listY1 = y1;
		refilter();
		boolean free = !adding && !modOpen;
		filterInput.draw(graphics, font, x0, y0, x1 - 70, y0 + 20, "Search blocks and items");
		boolean addHover = free && LuceatLook.inside(mx, my, x1 - 64, y0, x1, y0 + 20);
		LuceatLook.roundOutlined(graphics, x1 - 64, y0, x1, y0 + 20, adding ? 0x66BE3FFF : addHover ? LuceatLook.CARD_HOVER : LuceatLook.CARD, adding ? LuceatLook.ACCENT : addHover ? 0x80E4D6FF : LuceatLook.BORDER);
		ink(graphics, "Add rule", x1 - 64 + (64 - inkWidth("Add rule")) / 2, y0 + 6, adding ? LuceatLook.ACCENT : LuceatLook.SOFT, false);
		int cx = x0;
		for (Filter filter : Filter.values()) {
			int width = inkWidth(filter.label) + 14;
			boolean on = filter == mode;
			boolean over = free && LuceatLook.inside(mx, my, cx, y0 + 24, cx + width, y0 + 42);
			LuceatLook.roundOutlined(graphics, cx, y0 + 24, cx + width, y0 + 42, on ? 0xB0BE3FFF : over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, on ? 0xCCE4D6FF : over ? 0x80E4D6FF : LuceatLook.BORDER);
			ink(graphics, filter.label, cx + 7, y0 + 29, on ? 0xFFFFFFFF : LuceatLook.SOFT, false);
			cx += width + 4;
		}
		String count = shown.size() + (shown.size() == 1 ? " entry" : " entries");
		int countW = inkWidth(count);
		if (cx + countW + 12 < x1) {
			ink(graphics, count, x1 - countW, y0 + 29, LuceatLook.MUTED, false);
		}
		String modText = "Mod: " + (modFilter == null ? "All" : modName(modFilter));
		int sortW = inkWidth(sortLabel()) + 14;
		int modMax = Math.max(80, x1 - x0 - sortW - 12);
		modText = fitText(modText, Math.min(190, modMax) - 28);
		modX0 = x0;
		modX1 = x0 + inkWidth(modText) + 28;
		boolean modOver = free && LuceatLook.inside(mx, my, modX0, y0 + 48, modX1, y0 + 66);
		LuceatLook.roundOutlined(graphics, modX0, y0 + 48, modX1, y0 + 66, modOpen ? LuceatLook.ACCENT_SOFT : modOver ? LuceatLook.CARD_HOVER : LuceatLook.CARD, modOpen || modFilter != null ? LuceatLook.ACCENT_DIM : LuceatLook.BORDER);
		ink(graphics, modText, modX0 + 7, y0 + 53, modFilter != null ? LuceatLook.TEXT : LuceatLook.SOFT, false);
		Icons.sprite(graphics, modX1 - 16, y0 + 52, 10, Icons.CHEVRON, LuceatLook.SOFT);
		String sortText = sortLabel();
		boolean sortOver = free && LuceatLook.inside(mx, my, x1 - sortW, y0 + 48, x1, y0 + 66);
		LuceatLook.roundOutlined(graphics, x1 - sortW, y0 + 48, x1, y0 + 66, sortOver ? LuceatLook.CARD_HOVER : LuceatLook.CARD, LuceatLook.BORDER);
		ink(graphics, sortText, x1 - sortW + 7, y0 + 53, LuceatLook.SOFT, false);
		int top = y0 + listTopOffset();
		scroll = Math.max(0.0F, Math.min(scroll, maxScroll()));
		graphics.enableScissor(x0, top, x1 + 2, y1);
		int y = top - Math.round(scroll);
		List<FormattedCharSequence> tip = null;
		for (Entry entry : shown) {
			if (y + ROW > top && y < y1) {
				BlockRule rule = BlockRules.get(entry.key);
				boolean over = free && LuceatLook.inside(mx, my, x0, y, x1, y + ROW) && my >= top;
				boolean isSelected = entry == selected;
				LuceatLook.roundOutlined(graphics, x0, y, x1, y + ROW, isSelected ? 0x66BE3FFF : over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, isSelected ? 0xCCB28CFF : over ? 0x80E4D6FF : LuceatLook.BORDER);
				if (!entry.stack.isEmpty()) {
					graphics.fakeItem(entry.stack, x0 + 6, y + 6);
				}
				graphics.enableScissor(x0 + 26, y, Math.max(x0 + 26, x1 - 150), y + ROW);
				ink(graphics, entry.name, x0 + 28, y + 5, LuceatLook.TEXT, false);
				ink(graphics, entry.key, x0 + 28, y + 16, LuceatLook.MUTED, false);
				graphics.disableScissor();
				int rx = x1 - 8;
				int level = effectiveLevel(entry);
				boolean added = rule != null && rule.added;
				String status = level > 0 ? "Level " + level : rule != null && !rule.enabled ? "Off" : "No light";
				int statusColour = level > 0 ? isSelected ? 0xFFFFFFFF : LuceatLook.TEXT : rule != null && !rule.enabled ? LuceatLook.BAD : isSelected ? 0xFFDCCBF5 : 0xFF8E8EA0;
				rx -= inkWidth(status);
				ink(graphics, status, rx, y + 10, statusColour, false);
				if (level > 0) {
					rx -= 16;
					LuceatLook.swatch(graphics, rx, y + 9, rx + 11, y + 20, pack(colour(entry)) & 0xFFFFFF, false);
				}
				if (builtIn(entry) || added) {
					String badge = added ? "Yours" : "Built in";
					int bw = inkWidth(badge) + 10;
					rx -= bw + 6;
					int colourBadge = added ? LuceatLook.ACCENT : BUILT_IN;
					LuceatLook.tag(graphics, rx, y + 7, rx + bw, y + 21, colourBadge);
					ink(graphics, badge, rx + 5, y + 11, added ? 0xFFD9C8FF : 0xFFE4E0F6, false);
					if (free && LuceatLook.inside(mx, my, rx, y + 7, rx + bw, y + 21) && my >= top) {
						String text = added ? "A rule you made." : "Comes with the game or Luceat.";
						tip = font.split(Component.literal(text).withStyle(Style.EMPTY.withColor(0xDDDDE6)), 200);
					}
				}
				if (rule != null && rule.edited() && !added) {
					graphics.fill(x0 + 2, y + 6, x0 + 4, y + ROW - 6, LuceatLook.WARN);
				}
			}
			y += ROW + GAP;
		}
		graphics.disableScissor();
		if (shown.isEmpty()) {
			ink(graphics, "Nothing matches.", x0 + 4, top + 6, LuceatLook.MUTED, false);
		}
		barMax = maxScroll();
		barTrackTop = top;
		barTrackH = y1 - top;
		if (barMax > 0) {
			barThumb = Math.max(24, barTrackH * barTrackH / (barTrackH + barMax));
			barThumbTop = top + Math.round((barTrackH - barThumb) * (scroll / barMax));
			boolean barOver = free && LuceatLook.inside(mx, my, x1 + 1, barThumbTop, x1 + 8, barThumbTop + barThumb);
			LuceatLook.round(graphics, x1 + 3, top, x1 + 7, y1, 0x55120D20);
			LuceatLook.round(graphics, x1 + 3, barThumbTop, x1 + 7, barThumbTop + barThumb, draggingBar ? LuceatLook.ACCENT : barOver ? 0xFF8F78E8 : LuceatLook.ACCENT_DIM);
		}
		if (tip != null) {
			graphics.setTooltipForNextFrame(font, tip, Math.round(mx * factor), Math.round(my * factor));
		}
	}

	private String fitText(String text, int width) {
		if (inkWidth(text) <= width) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && inkWidth(cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}

	private int[] addBox(int ax0, int ay0, int ax1, int ay1) {
		int w = Math.max(220, Math.min(380, ax1 - ax0 - 24));
		int h = Math.max(160, Math.min(ay1 - ay0 - 16, 340));
		int x0 = ax0 + (ax1 - ax0 - w) / 2;
		int y0 = ay0 + (ay1 - ay0 - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] modBox() {
		int rows = Math.min(8, mods.size() + 1);
		int x0 = modX0;
		int y0 = listY0 + 68;
		return new int[] {x0, y0, x0 + 200, y0 + rows * 20 + 8};
	}

	private void refreshAddResults() {
		String query = addInput.text().toLowerCase(Locale.ROOT).trim();
		if (query.equals(lastAddQuery)) {
			return;
		}
		lastAddQuery = query;
		addResults.clear();
		if (all == null) {
			build();
		}
		for (Entry entry : all) {
			if (entry.defaultLevel > 0) {
				continue;
			}
			BlockRule rule = BlockRules.get(entry.key);
			if (rule != null && rule.added) {
				continue;
			}
			if (!query.isEmpty() && !entry.search.contains(query)) {
				continue;
			}
			addResults.add(entry);
		}
	}

	public void drawOverlays(GuiGraphicsExtractor graphics, int ax0, int ay0, int ax1, int ay1, int mx, int my) {
		if (modOpen) {
			int[] box = modBox();
			LuceatLook.roundOutlined(graphics, box[0], box[1], box[2], box[3], LuceatLook.WINDOW, LuceatLook.ACCENT_DIM);
			graphics.enableScissor(box[0] + 2, box[1] + 4, box[2] - 2, box[3] - 4);
			int total = mods.size() + 1;
			float max = Math.max(0, total * 20 - (box[3] - box[1] - 8));
			modScroll = Math.max(0.0F, Math.min(modScroll, max));
			int y = box[1] + 4 - Math.round(modScroll);
			for (int i = 0; i < total; i++) {
				String ns = i == 0 ? null : mods.get(i - 1);
				boolean on = java.util.Objects.equals(ns, modFilter);
				boolean over = LuceatLook.inside(mx, my, box[0] + 2, y, box[2] - 2, y + 20) && my >= box[1] + 4 && my < box[3] - 4;
				if (on || over) {
					LuceatLook.round(graphics, box[0] + 4, y + 1, box[2] - 4, y + 19, on ? LuceatLook.ACCENT : LuceatLook.CARD_HOVER);
				}
				ink(graphics, fitText(ns == null ? "All mods" : modName(ns), box[2] - box[0] - 24), box[0] + 12, y + 6, on ? 0xFFFFFFFF : LuceatLook.TEXT, false);
				y += 20;
			}
			graphics.disableScissor();
		}
		if (!adding) {
			return;
		}
		refreshAddResults();
		graphics.fill(ax0, ay0, ax1, ay1, 0xC0080511);
		int[] box = addBox(ax0, ay0, ax1, ay1);
		LuceatLook.roundOutlined(graphics, box[0], box[1], box[2], box[3], LuceatLook.WINDOW, LuceatLook.ACCENT_DIM);
		ink(graphics, "Add a light rule", box[0] + 14, box[1] + 12, LuceatLook.TEXT, false);
		boolean closeOver = LuceatLook.inside(mx, my, box[2] - 30, box[1] + 6, box[2] - 8, box[1] + 28);
		Icons.sprite(graphics, box[2] - 26, box[1] + 11, 12, Icons.CLOSE, closeOver ? LuceatLook.TEXT : LuceatLook.SOFT);
		ink(graphics, fitText("Pick a block or item that gives no light yet.", box[2] - box[0] - 28), box[0] + 14, box[1] + 28, LuceatLook.MUTED, false);
		addInput.draw(graphics, font, box[0] + 12, box[1] + 44, box[2] - 12, box[1] + 66, "Search blocks and items");
		int listTop = box[1] + 74;
		int listBottom = box[3] - 12;
		int rowH = 26;
		float max = Math.max(0, addResults.size() * rowH - (listBottom - listTop));
		addScroll = Math.max(0.0F, Math.min(addScroll, max));
		graphics.enableScissor(box[0] + 12, listTop, box[2] - 12, listBottom);
		int y = listTop - Math.round(addScroll);
		for (Entry entry : addResults) {
			if (y + rowH > listTop && y < listBottom) {
				boolean over = LuceatLook.inside(mx, my, box[0] + 12, y, box[2] - 12, y + rowH - 2) && my >= listTop && my < listBottom;
				LuceatLook.round(graphics, box[0] + 12, y, box[2] - 12, y + rowH - 2, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD);
				if (!entry.stack.isEmpty()) {
					graphics.fakeItem(entry.stack, box[0] + 18, y + 4);
				}
				int textRight = box[2] - 74;
				graphics.enableScissor(box[0] + 38, y, textRight, y + rowH);
				ink(graphics, entry.name, box[0] + 40, y + 4, LuceatLook.TEXT, false);
				ink(graphics, entry.key, box[0] + 40, y + 14, LuceatLook.MUTED, false);
				graphics.disableScissor();
				if (over) {
					LuceatLook.roundOutlined(graphics, box[2] - 66, y + 4, box[2] - 18, y + rowH - 6, LuceatLook.ACCENT, LuceatLook.ACCENT);
					ink(graphics, "Add", box[2] - 42 - inkWidth("Add") / 2, y + 9, 0xFFFFFFFF, false);
				}
			}
			y += rowH;
		}
		graphics.disableScissor();
		if (addResults.isEmpty()) {
			ink(graphics, "Nothing matches.", box[0] + 16, listTop + 6, LuceatLook.MUTED, false);
		}
	}

	private void openAdd() {
		adding = true;
		modOpen = false;
		addScroll = 0.0F;
		lastAddQuery = null;
		addInput.set("");
		addInput.focus(true);
		filterInput.focus(false);
	}

	private void addRule(Entry entry) {
		BlockRule made = BlockRules.edit(entry.key);
		made.added = true;
		made.level = 10;
		commit();
		adding = false;
		addInput.focus(false);
		selected = entry;
		editorScroll = 0.0F;
		mode = Filter.YOURS;
		modFilter = null;
		scroll = 0.0F;
	}

	private boolean clickAdd(double mx, double my) {
		int[] box = addBox(areaX0, areaY0, areaX1, areaY1);
		if (!LuceatLook.inside(mx, my, box[0], box[1], box[2], box[3])) {
			adding = false;
			addInput.focus(false);
			return true;
		}
		if (LuceatLook.inside(mx, my, box[2] - 30, box[1] + 6, box[2] - 8, box[1] + 28)) {
			adding = false;
			addInput.focus(false);
			return true;
		}
		if (LuceatLook.inside(mx, my, box[0] + 12, box[1] + 44, box[2] - 12, box[1] + 66)) {
			addInput.focus(true);
			addInput.mouseClicked(mx, my);
			return true;
		}
		int listTop = box[1] + 74;
		int listBottom = box[3] - 12;
		if (my >= listTop && my < listBottom) {
			int index = (int) Math.floor((my - listTop + addScroll) / 26.0);
			if (index >= 0 && index < addResults.size()) {
				addRule(addResults.get(index));
			}
		}
		return true;
	}

	private boolean clickMods(double mx, double my) {
		int[] box = modBox();
		if (LuceatLook.inside(mx, my, box[0], box[1], box[2], box[3])) {
			int index = (int) Math.floor((my - box[1] - 4 + modScroll) / 20.0);
			if (index >= 0 && index <= mods.size()) {
				modFilter = index == 0 ? null : mods.get(index - 1);
				scroll = 0.0F;
				modOpen = false;
			}
			return true;
		}
		modOpen = false;
		return LuceatLook.inside(mx, my, modX0, listY0 + 48, modX1, listY0 + 66);
	}

	private int areaX0;
	private int areaY0;
	private int areaX1;
	private int areaY1;

	private record Hit(int x0, int y0, int x1, int y1, Runnable click, Slide slide) {
	}

	private record Slide(int id, int x0, int x1, java.util.function.DoubleConsumer set) {
	}

	private void hit(int x0, int y0, int x1, int y1, Runnable click) {
		if (y1 > edTop && y0 < edBottom) {
			hits.add(new Hit(x0, Math.max(y0, edTop), x1, Math.min(y1, edBottom), click, null));
		}
	}

	private void slideHit(int id, int x0, int y0, int x1, int y1, java.util.function.DoubleConsumer set) {
		if (y1 > edTop && y0 < edBottom) {
			hits.add(new Hit(x0, Math.max(y0, edTop), x1, Math.min(y1, edBottom), null, new Slide(id, x0, x1, set)));
		}
	}

	private int header(GuiGraphicsExtractor graphics, String name, int y, int mx, int my) {
		boolean closed = collapsed.contains(name);
		boolean over = LuceatLook.inside(mx, my, edX0, y, edX1, y + 16) && my >= edTop && my < edBottom;
		LuceatLook.round(graphics, edX0, y, edX1, y + 16, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD);
		ink(graphics, closed ? ">" : "v", edX0 + 6, y + 4, LuceatLook.ACCENT, false);
		ink(graphics, name, edX0 + 18, y + 4, LuceatLook.TEXT, false);
		hit(edX0, y, edX1, y + 16, () -> {
			if (!collapsed.remove(name)) {
				collapsed.add(name);
			}
		});
		return y + 20;
	}

	private int toggleRow(GuiGraphicsExtractor graphics, String label, boolean on, int y, Runnable flip) {
		ink(graphics, label, edX0, y + 5, LuceatLook.TEXT, false);
		int tx = edX1 - 30;
		LuceatLook.capsule(graphics, tx, y + 2, tx + 30, y + 16, on ? LuceatLook.ACCENT : LuceatLook.TRACK);
		int knob = on ? tx + 17 : tx + 3;
		LuceatLook.capsule(graphics, knob, y + 4, knob + 10, y + 14, 0xFFFFFFFF);
		hit(edX0, y, edX1, y + 18, flip);
		return y + 22;
	}

	private int cycleRow(GuiGraphicsExtractor graphics, String label, String value, boolean custom, int y, int mx, int my, Runnable next) {
		ink(graphics, label, edX0, y + 5, LuceatLook.TEXT, false);
		int w = inkWidth(value) + 14;
		int bx0 = edX1 - w;
		boolean over = LuceatLook.inside(mx, my, bx0, y, edX1, y + 18) && my >= edTop && my < edBottom;
		LuceatLook.roundOutlined(graphics, bx0, y, edX1, y + 18, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, custom ? LuceatLook.WARN : LuceatLook.BORDER);
		ink(graphics, value, bx0 + 7, y + 5, custom ? LuceatLook.WARN : LuceatLook.SOFT, false);
		hit(bx0, y, edX1, y + 18, next);
		return y + 24;
	}

	private int sliderRow(GuiGraphicsExtractor graphics, String label, String value, boolean custom, float fraction, int y, int id, Runnable reset, java.util.function.DoubleConsumer set) {
		ink(graphics, label, edX0, y, LuceatLook.TEXT, false);
		ink(graphics, value, edX1 - inkWidth(value), y, custom ? LuceatLook.WARN : LuceatLook.SOFT, false);
		if (reset != null) {
			hit(edX0, y - 2, edX0 + inkWidth(label) + 4, y + 10, reset);
		}
		slider(graphics, edX0, edX1, y + 16, fraction, slider == id);
		slideHit(id, edX0, y + 10, edX1, y + 28, set);
		return y + 34;
	}

	private static String tri(int value, String on, String off) {
		return value < 0 ? "Automatic" : value > 0 ? on : off;
	}

	private static int next(int value) {
		return value < 0 ? 1 : value > 0 ? 0 : -1;
	}

	private void setRule(java.util.function.Consumer<BlockRule> change) {
		change.accept(BlockRules.edit(selected.key));
		commit();
	}

	public void drawEditor(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int mx, int my) {
		edX0 = x0 + 10;
		edX1 = x1 - 14;
		edTop = y0 + 24;
		edBottom = y1 - 4;
		hits.clear();
		LuceatLook.round(graphics, x0, y0, x1, y1, LuceatLook.SIDEBAR);
		ink(graphics, "Block settings", x0 + 10, y0 + 8, LuceatLook.TEXT, false);
		Entry entry = selected;
		if (entry == null) {
			drawWrapped(graphics, "Pick a block or item on the left to see and change its light. Every block is in the list, with or without light.", x0 + 10, y0 + 28, x1 - x0 - 20, LuceatLook.MUTED);
			return;
		}
		BlockRule rule = BlockRules.get(entry.key);
		boolean added = rule != null && rule.added;
		boolean lit = entry.defaultLevel > 0 || added;
		drawTrash(graphics, x1, y0, mx, my, added);
		editorScroll = Math.max(0.0F, Math.min(editorScroll, Math.max(0, editorHeight - (edBottom - edTop))));
		graphics.enableScissor(x0, edTop, x1, edBottom);
		int y = edTop - Math.round(editorScroll);
		int start = y;
		if (!entry.stack.isEmpty()) {
			graphics.fakeItem(entry.stack, x0 + 10, y);
		}
		ink(graphics, entry.name, x0 + 32, y, LuceatLook.TEXT, false);
		ink(graphics, entry.key, x0 + 32, y + 11, LuceatLook.SOFT, false);
		y += 26;
		String kind = entry.block == null ? "Item only. It lights when you hold it, drop it or put it in a frame." : "Block. It lights when placed, held, dropped or in a frame.";
		y = drawWrapped(graphics, kind, x0 + 10, y, x1 - x0 - 20, LuceatLook.SOFT) + 6;
		if (builtIn(entry)) {
			ink(graphics, "Built in", x0 + 10, y, BUILT_IN, false);
			y += 14;
		} else if (added) {
			ink(graphics, "Made by you.", x0 + 10, y, LuceatLook.ACCENT, false);
			y += 14;
		}
		if (!lit) {
			y = drawWrapped(graphics, "This one gives off no light.", x0 + 10, y, x1 - x0 - 20, LuceatLook.MUTED) + 8;
			button(graphics, edX0, y, edX1, y + 22, "Make it glow", mx, my, true);
			hit(edX0, y, edX1, y + 22, () -> {
				BlockRule made = BlockRules.edit(entry.key);
				made.added = true;
				made.level = 10;
				commit();
				adding = false;
			});
			graphics.disableScissor();
			editorHeight = y + 30 - start;
			return;
		}
		boolean enabled = rule == null || rule.enabled;
		y = header(graphics, "Light", y, mx, my);
		if (!collapsed.contains("Light")) {
			y = toggleRow(graphics, "Gives light", enabled, y, () -> setRule(r -> r.enabled = !r.enabled));
			int level = rule != null && rule.level >= 0 ? rule.level : entry.defaultLevel;
			boolean customLevel = rule != null && rule.level >= 0;
			y = sliderRow(graphics, "Light level", (customLevel ? "" : "Default ") + level, customLevel, level / 15.0F, y + 2, 1,
				customLevel ? () -> setRule(r -> r.level = -1) : null,
				v -> {
					int picked = Math.max(1, Math.round((float) v * 15.0F));
					BlockRule edit = BlockRules.edit(entry.key);
					edit.level = picked == entry.defaultLevel && !edit.added ? -1 : picked;
				});
			float[] rgb = colour(entry);
			boolean customColour = rule != null && rule.colour >= 0;
			ink(graphics, "Colour", edX0, y + 3, LuceatLook.TEXT, false);
			int sx = edX0 + 50;
			boolean swatchOver = LuceatLook.inside(mx, my, sx, y, sx + 34, y + 16) && my >= edTop && my < edBottom;
			LuceatLook.swatch(graphics, sx, y, sx + 34, y + 16, pack(rgb) & 0xFFFFFF, swatchOver);
			hit(sx, y, sx + 34, y + 16, () -> {
				picker.open(pack(colour(entry)) & 0xFFFFFF, rgbValue -> {
					BlockRule edit = BlockRules.edit(entry.key);
					edit.colour = rgbValue & 0xFFFFFF;
					commit();
				});
			});
			ink(graphics, String.format(Locale.ROOT, "#%06X", pack(rgb) & 0xFFFFFF), sx + 40, y + 4, LuceatLook.SOFT, false);
			String auto = customColour ? "Use automatic" : "Automatic";
			int aw = inkWidth(auto) + 12;
			boolean autoOver = LuceatLook.inside(mx, my, edX1 - aw, y + 20, edX1, y + 36) && my >= edTop && my < edBottom;
			LuceatLook.roundOutlined(graphics, edX1 - aw, y + 20, edX1, y + 36, customColour ? autoOver ? LuceatLook.CARD_HOVER : LuceatLook.CARD : LuceatLook.ACCENT_SOFT, customColour ? LuceatLook.BORDER : LuceatLook.ACCENT_DIM);
			ink(graphics, auto, edX1 - aw + 6, y + 24, customColour ? LuceatLook.SOFT : LuceatLook.ACCENT, false);
			if (customColour) {
				hit(edX1 - aw, y + 20, edX1, y + 36, () -> setRule(r -> r.colour = -1));
			}
			y += 44;
		}
		y = header(graphics, "Behaviour", y, mx, my);
		if (!collapsed.contains("Behaviour")) {
			boolean customRadius = rule != null && Math.abs(rule.radius - 1.0F) > 0.001F;
			float radius = rule == null ? 1.0F : rule.radius;
			y = sliderRow(graphics, "Reach", String.format(Locale.ROOT, "x%.2f", radius), customRadius, (radius - 0.25F) / 2.25F, y + 2, 5,
				customRadius ? () -> setRule(r -> r.radius = 1.0F) : null,
				v -> {
					float picked = 0.25F + (float) v * 2.25F;
					picked = Math.abs(picked - 1.0F) < 0.04F ? 1.0F : Math.round(picked * 20.0F) / 20.0F;
					BlockRules.edit(entry.key).radius = picked;
				});
			int flicker = rule == null ? -1 : rule.flicker;
			y = cycleRow(graphics, "Flicker", tri(flicker, "On", "Off"), flicker >= 0, y, mx, my, () -> setRule(r -> r.flicker = next(r.flicker)));
			int shadows = rule == null ? -1 : rule.shadows;
			y = cycleRow(graphics, "Casts shadows", tri(shadows, "On", "Off"), shadows >= 0, y, mx, my, () -> setRule(r -> r.shadows = next(r.shadows)));
			int water = rule == null ? -1 : rule.water;
			y = cycleRow(graphics, "In water", tri(water, "Goes out", "Stays lit"), water >= 0, y, mx, my, () -> setRule(r -> r.water = next(r.water)));
			boolean customShine = rule != null && rule.shine >= 0.0F;
			float shine = customShine ? rule.shine : 0.0F;
			y = sliderRow(graphics, "Shiny", customShine ? Math.round(shine * 100.0F) + "%" : "Automatic", customShine, shine, y + 2, 6,
				customShine ? () -> setRule(r -> r.shine = -1.0F) : null,
				v -> BlockRules.edit(entry.key).shine = Math.round((float) v * 20.0F) / 20.0F);
		}
		if (!collapsed.contains("Behaviour")) {
			boolean customHeight = rule != null && rule.heldHeight >= 0.0F;
			float height = customHeight ? rule.heldHeight : 0.0F;
			y = sliderRow(graphics, "Height when held", customHeight ? Math.round(height * 100.0F) + "%" : "Automatic", customHeight, (height - 0.3F) / 1.0F, y + 2, 7,
				customHeight ? () -> setRule(r -> r.heldHeight = -1.0F) : null,
				v -> BlockRules.edit(entry.key).heldHeight = Math.round((0.3F + (float) v * 1.0F) * 50.0F) / 50.0F);
		}
		y = header(graphics, "Where it glows", y, mx, my);
		if (!collapsed.contains("Where it glows")) {
			boolean placed = rule == null || rule.whenPlaced;
			boolean held = rule == null || rule.whenHeld;
			boolean dropped = rule == null || rule.whenDropped;
			boolean framed = rule == null || rule.whenFramed;
			if (entry.block != null) {
				y = toggleRow(graphics, "Placed in the world", placed, y + 2, () -> setRule(r -> r.whenPlaced = !r.whenPlaced));
			}
			y = toggleRow(graphics, "In your hand", held, y + (entry.block != null ? 0 : 2), () -> setRule(r -> r.whenHeld = !r.whenHeld));
			y = toggleRow(graphics, "Dropped on the ground", dropped, y, () -> setRule(r -> r.whenDropped = !r.whenDropped));
			y = toggleRow(graphics, "In item frames", framed, y, () -> setRule(r -> r.whenFramed = !r.whenFramed));
		}
		y += 4;
		boolean changed = rule != null && rule.edited();
		if (added) {
			y += 4;
		} else {
			button(graphics, edX0, y, edX1, y + 22, "Reset to built in", mx, my, changed);
			if (changed) {
				hit(edX0, y, edX1, y + 22, () -> {
					BlockRules.remove(entry.key);
					commit();
				});
			}
			y += 30;
		}
		graphics.disableScissor();
		editorHeight = y - start;
		if (picker.isOpen()) {
			int ph = ColourPicker.HEIGHT;
			int pw = ColourPicker.WIDTH;
			int popX = x0 - pw - 6 >= 4 ? x0 - pw - 6 : x0 + 4;
			int popY = Math.max(listY0, Math.min(listY1 - ph, edTop + 40));
			picker.draw(graphics, font, popX, popY, mx, my);
		}
	}

	private int trashX0;
	private int trashY0;
	private boolean trashActive;

	private void drawTrash(GuiGraphicsExtractor graphics, int x1, int y0, int mx, int my, boolean show) {
		trashActive = show;
		if (!show) {
			return;
		}
		trashX0 = x1 - 26;
		trashY0 = y0 + 4;
		boolean over = LuceatLook.inside(mx, my, trashX0 - 3, trashY0 - 3, trashX0 + 15, trashY0 + 17);
		int shake = over ? Math.round((float) Math.sin(System.nanoTime() / 3.0E7) * 1.6F) : 0;
		int x = trashX0 + shake;
		int y = trashY0;
		int colour = over ? LuceatLook.BAD : LuceatLook.SOFT;
		int lift = over ? 2 : 0;
		Icons.sprite(graphics, x - 1, y - lift, 16, Icons.TRASH, colour);
		if (over) {
			graphics.setTooltipForNextFrame(font, font.split(Component.literal("Delete this rule"), 120), Math.round(mx * factor), Math.round(my * factor));
		}
	}

	private void slider(GuiGraphicsExtractor graphics, int x0, int x1, int y, float fraction, boolean active) {
		graphics.fill(x0, y, x1, y + 2, LuceatLook.TRACK);
		int filled = Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, fraction)));
		graphics.fill(x0, y, x0 + filled, y + 2, LuceatLook.ACCENT);
		LuceatLook.capsule(graphics, x0 + filled - 4, y - 4, x0 + filled + 4, y + 6, active ? 0xFFFFFFFF : 0xFFE6C9FF);
	}

	private void button(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, String label, int mx, int my, boolean enabled) {
		boolean over = enabled && LuceatLook.inside(mx, my, x0, y0, x1, y1) && my >= edTop && my < edBottom;
		LuceatLook.roundOutlined(graphics, x0, y0, x1, y1, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, enabled ? LuceatLook.ACCENT_DIM : LuceatLook.BORDER);
		ink(graphics, label, x0 + (x1 - x0 - inkWidth(label)) / 2, y0 + (y1 - y0 - 8) / 2, enabled ? LuceatLook.TEXT : LuceatLook.MUTED, false);
	}

	private float factor = 1.0F;

	public void setFactor(float value) {
		factor = value;
	}

	public void setArea(int x0, int y0, int x1, int y1) {
		areaX0 = x0;
		areaY0 = y0;
		areaX1 = x1;
		areaY1 = y1;
	}

	private void ink(GuiGraphicsExtractor graphics, String text, int x, int y, int colour, boolean shadow) {
		LuceatFonts.draw(graphics, font, LuceatFonts.body(text), x, y, colour, shadow);
	}

	private void ink(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y, int colour, boolean shadow) {
		graphics.text(font, text, x, y, colour, shadow);
	}

	private int inkWidth(String text) {
		return LuceatFonts.width(font, LuceatFonts.body(text));
	}

	private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int colour) {
		for (FormattedCharSequence line : font.split(LuceatFonts.body(text), width)) {
			ink(graphics, line, x, y, colour, false);
			y += 10;
		}
		return y;
	}

	private void commit() {
		BlockRules.changed();
		dirty = true;
		lastRules = -1;
		lastAddQuery = null;
	}

	public boolean mouseClicked(double mx, double my) {
		if (adding) {
			return clickAdd(mx, my);
		}
		if (modOpen) {
			return clickMods(mx, my);
		}
		if (picker.isOpen()) {
			boolean inside = picker.contains(mx, my);
			if (picker.mouseClicked(mx, my) || inside) {
				return true;
			}
		}
		boolean inFilter = LuceatLook.inside(mx, my, listX0, listY0, listX1 - 70, listY0 + 20);
		filterInput.focus(inFilter);
		if (inFilter) {
			return true;
		}
		if (LuceatLook.inside(mx, my, listX1 - 64, listY0, listX1, listY0 + 20)) {
			openAdd();
			return true;
		}
		if (LuceatLook.inside(mx, my, modX0, listY0 + 48, modX1, listY0 + 66)) {
			modOpen = true;
			modScroll = 0.0F;
			return true;
		}
		int sortW = inkWidth(sortLabel()) + 14;
		if (LuceatLook.inside(mx, my, listX1 - sortW, listY0 + 48, listX1, listY0 + 66)) {
			sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
			scroll = 0.0F;
			return true;
		}
		int cx = listX0;
		for (Filter filter : Filter.values()) {
			int width = inkWidth(filter.label) + 14;
			if (LuceatLook.inside(mx, my, cx, listY0 + 24, cx + width, listY0 + 42)) {
				mode = filter;
				scroll = 0.0F;
				return true;
			}
			cx += width + 4;
		}
		if (barMax > 0 && LuceatLook.inside(mx, my, listX1 + 1, barTrackTop, listX1 + 9, barTrackTop + barTrackH)) {
			boolean onThumb = my >= barThumbTop && my < barThumbTop + barThumb;
			barGrab = onThumb ? (int) (my - barThumbTop) : barThumb / 2;
			draggingBar = true;
			moveBar(my);
			return true;
		}
		int top = listY0 + listTopOffset();
		if (LuceatLook.inside(mx, my, listX0, top, listX1, listY1)) {
			int y = top - Math.round(scroll);
			for (Entry entry : shown) {
				if (my >= y && my < y + ROW) {
					if (selected != entry) {
						picker.close();
						editorScroll = 0.0F;
					}
					selected = entry;
					return true;
				}
				y += ROW + GAP;
			}
			return true;
		}
		return clickEditor(mx, my);
	}

	private void moveBar(double my) {
		int range = Math.max(1, barTrackH - barThumb);
		float fraction = (float) ((my - barGrab - barTrackTop) / range);
		scroll = Math.max(0.0F, Math.min(1.0F, fraction)) * barMax;
	}

	private boolean clickEditor(double mx, double my) {
		if (selected == null || mx < edX0 - 10) {
			return false;
		}
		if (trashActive && LuceatLook.inside(mx, my, trashX0 - 3, trashY0 - 3, trashX0 + 15, trashY0 + 17)) {
			BlockRules.remove(selected.key);
			commit();
			return true;
		}
		for (Hit hit : hits) {
			if (LuceatLook.inside(mx, my, hit.x0(), hit.y0(), hit.x1(), hit.y1())) {
				if (hit.slide() != null) {
					activeSlide = hit.slide();
					slider = hit.slide().id();
					drag(mx);
				} else {
					hit.click().run();
				}
				return true;
			}
		}
		return false;
	}

	private void drag(double mx) {
		if (activeSlide == null) {
			return;
		}
		double fraction = Math.max(0.0, Math.min(1.0, (mx - activeSlide.x0()) / (double) (activeSlide.x1() - activeSlide.x0())));
		activeSlide.set().accept(fraction);
	}

	public boolean mouseDragged(double mx, double my) {
		if (draggingBar) {
			moveBar(my);
			return true;
		}
		if (picker.isOpen() && picker.mouseDragged(mx, my)) {
			return true;
		}
		if (activeSlide != null) {
			drag(mx);
			return true;
		}
		return false;
	}

	public boolean mouseReleased() {
		if (draggingBar) {
			draggingBar = false;
			return true;
		}
		boolean pickerUp = picker.isOpen() && picker.mouseReleased();
		if (activeSlide != null) {
			activeSlide = null;
			slider = 0;
			commit();
			return true;
		}
		return pickerUp;
	}

	public boolean mouseScrolled(double mx, double amount) {
		if (adding) {
			addScroll -= (float) amount * 30.0F;
			return true;
		}
		if (modOpen) {
			modScroll -= (float) amount * 30.0F;
			return true;
		}
		if (selected != null && mx >= edX0 - 10 && mx > listX1 + 8) {
			editorScroll -= (float) amount * 30.0F;
			return true;
		}
		if (mx >= listX0 && mx <= listX1 + 8) {
			scroll -= (float) amount * 30.0F;
			return true;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		if (adding) {
			boolean typed = addInput.charTyped(event);
			if (typed) {
				addScroll = 0.0F;
			}
			return typed;
		}
		if (picker.isOpen() && picker.typing()) {
			return picker.charTyped(event);
		}
		boolean used = filterInput.charTyped(event);
		if (used) {
			scroll = 0.0F;
		}
		return used;
	}

	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() && (adding || modOpen)) {
			adding = false;
			modOpen = false;
			addInput.focus(false);
			return true;
		}
		if (adding) {
			boolean used = addInput.keyPressed(event);
			if (used) {
				addScroll = 0.0F;
			}
			return used;
		}
		if (picker.isOpen() && picker.typing()) {
			return picker.keyPressed(event);
		}
		boolean used = filterInput.keyPressed(event);
		if (used) {
			scroll = 0.0F;
		}
		return used;
	}
}
