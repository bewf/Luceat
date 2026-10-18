package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.light.ColourEstimator;
import me.bewf.luceat.client.light.block.BlockRule;
import me.bewf.luceat.client.light.block.BlockRules;
import me.bewf.luceat.client.light.item.ItemLightRule;
import me.bewf.luceat.client.light.item.ItemLightRules;

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
	private static final int BUILT_IN = 0xFF6FA8FF;
	private static final float[] FALLBACK = {1.0F, 0.7F, 0.35F};

	private enum Filter {
		ALL("All"), LIGHT("Lights up"), DARK("Doesn't light"), EDITED("Edited"), YOURS("Yours");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	private static final class Entry {
		Identifier id;
		String key;
		String name;
		String search;
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
	private String lastQuery = null;
	private Filter lastMode;
	private int lastRules = -1;
	private int slider;

	private int listX0;
	private int listY0;
	private int listX1;
	private int listY1;
	private int edX0;
	private int edX1;
	private int yToggle;
	private int yLevel;
	private int yColour;
	private int yRed;
	private int yButtons;

	public BlocksTab(Font font) {
		this.font = font;
	}

	public boolean typing() {
		return filterInput.focused();
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
	}

	private void refilter() {
		if (all == null) {
			build();
		}
		String query = filterInput.text().toLowerCase(Locale.ROOT).trim();
		int rules = BlockRules.all().size() * 31 + (int) BlockRules.all().stream().filter(BlockRule::edited).count();
		if (query.equals(lastQuery) && mode == lastMode && rules == lastRules) {
			return;
		}
		lastQuery = query;
		lastMode = mode;
		lastRules = rules;
		shown.clear();
		for (Entry entry : all) {
			if (!query.isEmpty() && !entry.search.contains(query)) {
				continue;
			}
			BlockRule rule = BlockRules.get(entry.key);
			boolean lit = entry.defaultLevel > 0 || rule != null && rule.added;
			boolean pass = switch (mode) {
				case ALL -> true;
				case LIGHT -> lit;
				case DARK -> !lit;
				case EDITED -> rule != null && rule.edited();
				case YOURS -> rule != null && rule.added;
			};
			if (pass) {
				shown.add(entry);
			}
		}
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
		return 48 + (adding ? 14 : 0);
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
		filterInput.draw(graphics, font, x0, y0, x1 - 70, y0 + 20, "Search blocks and items");
		boolean addHover = LuceatLook.inside(mx, my, x1 - 64, y0, x1, y0 + 20);
		LuceatLook.roundOutlined(graphics, x1 - 64, y0, x1, y0 + 20, adding ? LuceatLook.ACCENT_SOFT : addHover ? LuceatLook.CARD_HOVER : LuceatLook.CARD, adding ? LuceatLook.ACCENT : LuceatLook.BORDER);
		graphics.text(font, "Add rule", x1 - 64 + (64 - font.width("Add rule")) / 2, y0 + 6, adding ? LuceatLook.ACCENT : LuceatLook.SOFT, false);
		int cx = x0;
		for (Filter filter : Filter.values()) {
			int width = font.width(filter.label) + 14;
			boolean on = filter == mode;
			boolean over = LuceatLook.inside(mx, my, cx, y0 + 24, cx + width, y0 + 42);
			LuceatLook.round(graphics, cx, y0 + 24, cx + width, y0 + 42, on ? LuceatLook.ACCENT : over ? LuceatLook.CARD_HOVER : LuceatLook.CARD);
			graphics.text(font, filter.label, cx + 7, y0 + 29, on ? 0xFFFFFFFF : LuceatLook.SOFT, false);
			cx += width + 4;
		}
		String count = shown.size() + (shown.size() == 1 ? " entry" : " entries");
		if (cx + font.width(count) + 8 < x1) {
			graphics.text(font, count, x1 - font.width(count), y0 + 29, LuceatLook.MUTED, false);
		}
		int top = y0 + listTopOffset();
		if (adding) {
			graphics.text(font, "Find the block or item, click it, then press Make it glow.", x0, y0 + 48, LuceatLook.WARN, false);
		}
		scroll = Math.max(0.0F, Math.min(scroll, maxScroll()));
		graphics.enableScissor(x0, top, x1 + 2, y1);
		int y = top - Math.round(scroll);
		List<FormattedCharSequence> tip = null;
		for (Entry entry : shown) {
			if (y + ROW > top && y < y1) {
				BlockRule rule = BlockRules.get(entry.key);
				boolean over = LuceatLook.inside(mx, my, x0, y, x1, y + ROW) && my >= top;
				boolean isSelected = entry == selected;
				LuceatLook.roundOutlined(graphics, x0, y, x1, y + ROW, isSelected ? LuceatLook.ACCENT_SOFT : over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, isSelected ? LuceatLook.ACCENT_DIM : over ? LuceatLook.CARD_HOVER : LuceatLook.CARD);
				if (!entry.stack.isEmpty()) {
					graphics.fakeItem(entry.stack, x0 + 6, y + 6);
				}
				graphics.enableScissor(x0 + 26, y, Math.max(x0 + 26, x1 - 150), y + ROW);
				graphics.text(font, entry.name, x0 + 28, y + 5, LuceatLook.TEXT, false);
				graphics.text(font, entry.key, x0 + 28, y + 16, LuceatLook.MUTED, false);
				graphics.disableScissor();
				int rx = x1 - 8;
				int level = effectiveLevel(entry);
				boolean added = rule != null && rule.added;
				String status = level > 0 ? "Level " + level : rule != null && !rule.enabled ? "Off" : "No light";
				int statusColour = level > 0 ? LuceatLook.TEXT : rule != null && !rule.enabled ? LuceatLook.BAD : LuceatLook.MUTED;
				rx -= font.width(status);
				graphics.text(font, status, rx, y + 10, statusColour, false);
				if (level > 0) {
					rx -= 16;
					LuceatLook.round(graphics, rx, y + 9, rx + 11, y + 20, pack(colour(entry)));
					graphics.outline(rx, y + 9, 11, 11, 0x55FFFFFF);
				}
				if (builtIn(entry) || added) {
					String badge = added ? "Yours" : "Built in";
					int bw = font.width(badge) + 10;
					rx -= bw + 6;
					int colourBadge = added ? LuceatLook.ACCENT : BUILT_IN;
					LuceatLook.roundOutlined(graphics, rx, y + 7, rx + bw, y + 21, 0xFF16161B, colourBadge);
					graphics.text(font, badge, rx + 5, y + 11, colourBadge, false);
					if (LuceatLook.inside(mx, my, rx, y + 7, rx + bw, y + 21) && my >= top) {
						String text = added ? "A rule you made. You can delete it." : "Comes with Minecraft or Luceat. You can turn it off or change it, but you cannot delete it.";
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
			graphics.text(font, "Nothing matches.", x0 + 4, top + 6, LuceatLook.MUTED, false);
		}
		int max = maxScroll();
		if (max > 0) {
			int trackH = y1 - top;
			int thumb = Math.max(16, trackH * trackH / (trackH + max));
			int thumbTop = top + Math.round((trackH - thumb) * (scroll / max));
			graphics.fill(x1 + 3, thumbTop, x1 + 5, thumbTop + thumb, LuceatLook.TRACK);
		}
		if (tip != null) {
			graphics.setTooltipForNextFrame(font, tip, mx, my);
		}
	}

	public void drawEditor(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int mx, int my) {
		edX0 = x0 + 10;
		edX1 = x1 - 10;
		LuceatLook.round(graphics, x0, y0, x1, y1, LuceatLook.SIDEBAR);
		graphics.text(font, "Block settings", x0 + 10, y0 + 8, LuceatLook.TEXT, false);
		Entry entry = selected;
		if (entry == null) {
			drawWrapped(graphics, "Pick a block or item on the left to see and change its light. Every block is in the list, with or without light.", x0 + 10, y0 + 28, x1 - x0 - 20, LuceatLook.MUTED);
			return;
		}
		BlockRule rule = BlockRules.get(entry.key);
		boolean added = rule != null && rule.added;
		boolean lit = entry.defaultLevel > 0 || added;
		int y = y0 + 26;
		if (!entry.stack.isEmpty()) {
			graphics.fakeItem(entry.stack, x0 + 10, y);
		}
		graphics.text(font, entry.name, x0 + 32, y, LuceatLook.TEXT, false);
		graphics.text(font, entry.key, x0 + 32, y + 11, LuceatLook.MUTED, false);
		y += 26;
		String kind = entry.block == null ? "Item only. It lights when you hold it, drop it or put it in a frame." : "Block. It lights when placed, held, dropped or in a frame.";
		y = drawWrapped(graphics, kind, x0 + 10, y, x1 - x0 - 20, LuceatLook.SOFT) + 6;
		if (builtIn(entry)) {
			graphics.text(font, "Built in. You can change it but not delete it.", x0 + 10, y, BUILT_IN, false);
			y += 14;
		} else if (added) {
			graphics.text(font, "Made by you.", x0 + 10, y, LuceatLook.ACCENT, false);
			y += 14;
		}
		if (!lit) {
			y = drawWrapped(graphics, "This one gives off no light.", x0 + 10, y, x1 - x0 - 20, LuceatLook.MUTED) + 8;
			yButtons = y;
			button(graphics, edX0, y, edX1, y + 22, "Make it glow", mx, my, true);
			return;
		}
		boolean enabled = rule == null || rule.enabled;
		yToggle = y;
		graphics.text(font, "Gives light", edX0, y + 5, LuceatLook.TEXT, false);
		int tx = edX1 - 30;
		LuceatLook.round(graphics, tx, y + 2, tx + 30, y + 16, enabled ? LuceatLook.ACCENT : LuceatLook.TRACK);
		int knob = enabled ? tx + 17 : tx + 3;
		LuceatLook.round(graphics, knob, y + 4, knob + 10, y + 14, 0xFFFFFFFF);
		y += 28;
		yLevel = y;
		int level = rule != null && rule.level >= 0 ? rule.level : entry.defaultLevel;
		graphics.text(font, "Light level", edX0, y, LuceatLook.TEXT, false);
		String levelText = (rule != null && rule.level >= 0 ? "" : "Default ") + level;
		graphics.text(font, levelText, edX1 - font.width(levelText), y, rule != null && rule.level >= 0 ? LuceatLook.WARN : LuceatLook.SOFT, false);
		slider(graphics, edX0, edX1, y + 14, level / 15.0F, slider == 1);
		y += 32;
		yColour = y;
		float[] rgb = colour(entry);
		boolean customColour = rule != null && rule.colour >= 0;
		graphics.text(font, "Colour", edX0, y, LuceatLook.TEXT, false);
		LuceatLook.round(graphics, edX0 + 48, y - 2, edX0 + 70, y + 10, pack(rgb));
		graphics.outline(edX0 + 48, y - 2, 22, 12, 0x55FFFFFF);
		String auto = customColour ? "Use automatic" : "Automatic";
		int aw = font.width(auto) + 12;
		boolean autoOver = LuceatLook.inside(mx, my, edX1 - aw, y - 3, edX1, y + 11);
		LuceatLook.roundOutlined(graphics, edX1 - aw, y - 3, edX1, y + 11, customColour ? autoOver ? LuceatLook.CARD_HOVER : LuceatLook.CARD : LuceatLook.ACCENT_SOFT, customColour ? LuceatLook.BORDER : LuceatLook.ACCENT_DIM);
		graphics.text(font, auto, edX1 - aw + 6, y, customColour ? LuceatLook.SOFT : LuceatLook.ACCENT, false);
		y += 18;
		yRed = y;
		String[] names = {"Red", "Green", "Blue"};
		for (int i = 0; i < 3; i++) {
			graphics.text(font, names[i], edX0, y + i * 20, LuceatLook.SOFT, false);
			slider(graphics, edX0 + 40, edX1, y + i * 20 + 4, rgb[i], slider == 2 + i);
		}
		y += 66;
		yButtons = y;
		boolean changed = rule != null && rule.edited();
		if (added) {
			button(graphics, edX0, y, edX1, y + 22, "Delete rule", mx, my, true);
		} else {
			button(graphics, edX0, y, edX1, y + 22, "Reset to built in", mx, my, changed);
			drawWrapped(graphics, "Built in rules cannot be deleted. Turn the light off instead.", edX0, y + 30, edX1 - edX0, LuceatLook.MUTED);
		}
	}

	private void slider(GuiGraphicsExtractor graphics, int x0, int x1, int y, float fraction, boolean active) {
		graphics.fill(x0, y, x1, y + 2, LuceatLook.TRACK);
		int filled = Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, fraction)));
		graphics.fill(x0, y, x0 + filled, y + 2, LuceatLook.ACCENT);
		LuceatLook.round(graphics, x0 + filled - 4, y - 4, x0 + filled + 4, y + 6, active ? 0xFFFFFFFF : 0xFFE6C9FF);
	}

	private void button(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, String label, int mx, int my, boolean enabled) {
		boolean over = enabled && LuceatLook.inside(mx, my, x0, y0, x1, y1);
		LuceatLook.roundOutlined(graphics, x0, y0, x1, y1, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, enabled ? LuceatLook.ACCENT_DIM : LuceatLook.BORDER);
		graphics.text(font, label, x0 + (x1 - x0 - font.width(label)) / 2, y0 + (y1 - y0 - 8) / 2, enabled ? LuceatLook.TEXT : LuceatLook.MUTED, false);
	}

	private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int colour) {
		for (FormattedCharSequence line : font.split(Component.literal(text), width)) {
			graphics.text(font, line, x, y, colour, false);
			y += 10;
		}
		return y;
	}

	private void commit() {
		BlockRules.changed();
		dirty = true;
		lastRules = -1;
	}

	public boolean mouseClicked(double mx, double my) {
		boolean inFilter = LuceatLook.inside(mx, my, listX0, listY0, listX1 - 70, listY0 + 20);
		filterInput.focus(inFilter);
		if (inFilter) {
			return true;
		}
		if (LuceatLook.inside(mx, my, listX1 - 64, listY0, listX1, listY0 + 20)) {
			adding = !adding;
			if (adding) {
				mode = Filter.DARK;
				filterInput.focus(true);
			}
			scroll = 0.0F;
			return true;
		}
		int cx = listX0;
		for (Filter filter : Filter.values()) {
			int width = font.width(filter.label) + 14;
			if (LuceatLook.inside(mx, my, cx, listY0 + 24, cx + width, listY0 + 42)) {
				mode = filter;
				scroll = 0.0F;
				return true;
			}
			cx += width + 4;
		}
		int top = listY0 + listTopOffset();
		if (LuceatLook.inside(mx, my, listX0, top, listX1, listY1)) {
			int y = top - Math.round(scroll);
			for (Entry entry : shown) {
				if (my >= y && my < y + ROW) {
					selected = entry;
					return true;
				}
				y += ROW + GAP;
			}
			return true;
		}
		return clickEditor(mx, my);
	}

	private boolean clickEditor(double mx, double my) {
		if (selected == null || mx < edX0 - 10) {
			return false;
		}
		Entry entry = selected;
		BlockRule rule = BlockRules.get(entry.key);
		boolean added = rule != null && rule.added;
		boolean lit = entry.defaultLevel > 0 || added;
		if (!lit) {
			if (LuceatLook.inside(mx, my, edX0, yButtons, edX1, yButtons + 22)) {
				BlockRule made = BlockRules.edit(entry.key);
				made.added = true;
				made.level = 10;
				commit();
				adding = false;
				return true;
			}
			return false;
		}
		if (LuceatLook.inside(mx, my, edX1 - 34, yToggle, edX1, yToggle + 18)) {
			BlockRule edit = BlockRules.edit(entry.key);
			edit.enabled = !edit.enabled;
			commit();
			return true;
		}
		if (LuceatLook.inside(mx, my, edX0, yLevel + 8, edX1, yLevel + 28)) {
			slider = 1;
			dragLevel(mx);
			return true;
		}
		if (LuceatLook.inside(mx, my, edX1 - 90, yColour - 3, edX1, yColour + 11) && rule != null && rule.colour >= 0) {
			rule.colour = -1;
			commit();
			return true;
		}
		for (int i = 0; i < 3; i++) {
			int y = yRed + i * 20 + 4;
			if (LuceatLook.inside(mx, my, edX0 + 36, y - 6, edX1 + 4, y + 10)) {
				slider = 2 + i;
				dragColour(i, mx);
				return true;
			}
		}
		if (LuceatLook.inside(mx, my, edX0, yButtons, edX1, yButtons + 22)) {
			if (added) {
				BlockRules.remove(entry.key);
				commit();
			} else if (rule != null && rule.edited()) {
				rule.enabled = true;
				rule.level = -1;
				rule.colour = -1;
				commit();
			}
			return true;
		}
		return false;
	}

	private void dragLevel(double mx) {
		float fraction = (float) Math.max(0.0, Math.min(1.0, (mx - edX0) / (edX1 - edX0)));
		int level = Math.max(1, Math.round(fraction * 15.0F));
		BlockRule rule = BlockRules.edit(selected.key);
		rule.level = level == selected.defaultLevel && !rule.added ? -1 : level;
	}

	private void dragColour(int channel, double mx) {
		BlockRule rule = BlockRules.edit(selected.key);
		float[] base = rule.colour >= 0 ? rule.rgb() : colour(selected).clone();
		float fraction = (float) Math.max(0.0, Math.min(1.0, (mx - (edX0 + 40)) / (edX1 - edX0 - 40)));
		base[channel] = fraction;
		rule.colour = Math.round(base[0] * 255.0F) << 16 | Math.round(base[1] * 255.0F) << 8 | Math.round(base[2] * 255.0F);
	}

	public boolean mouseDragged(double mx) {
		if (slider == 1) {
			dragLevel(mx);
			return true;
		}
		if (slider >= 2) {
			dragColour(slider - 2, mx);
			return true;
		}
		return false;
	}

	public boolean mouseReleased() {
		if (slider != 0) {
			slider = 0;
			commit();
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double amount) {
		if (mx >= listX0 && mx <= listX1 + 8) {
			scroll -= (float) amount * 30.0F;
			return true;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		boolean used = filterInput.charTyped(event);
		if (used) {
			scroll = 0.0F;
		}
		return used;
	}

	public boolean keyPressed(KeyEvent event) {
		boolean used = filterInput.keyPressed(event);
		if (used) {
			scroll = 0.0F;
		}
		return used;
	}
}
