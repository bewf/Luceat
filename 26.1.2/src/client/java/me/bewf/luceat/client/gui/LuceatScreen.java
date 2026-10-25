package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
import me.bewf.luceat.client.gui.presets.PresetPicker;
import me.bewf.luceat.client.gui.preview.PreviewPanel;
import me.bewf.luceat.client.gui.preview.SceneSpec;
import me.bewf.luceat.client.gui.preview.Scenes;
import me.bewf.luceat.client.gui.setting.ChoiceSetting;
import me.bewf.luceat.client.gui.setting.LuceatSettings;
import me.bewf.luceat.client.gui.setting.Setting;
import me.bewf.luceat.client.gui.setting.SliderSetting;
import me.bewf.luceat.client.gui.setting.ToggleSetting;
import me.bewf.luceat.client.light.LightSpecs;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowVolume;
import me.bewf.luceat.client.shadow.ShapeMasks;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class LuceatScreen extends Screen {
	private static final String ALL = "All settings";
	private static final String SPEED = "Speed test";
	private static final String PRESETS = "Presets";
	private static final String BLOCKS = "Blocks";
	private static final String ABOUT = "About";
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo.png");
	private static final Identifier LOGO_SMALL = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo_small.png");
	private static final int CHOICE_HEIGHT = 48;
	private static final int HEAD_HEIGHT = 26;
	private static final int SIDE_ITEM = 21;
	private static final int SIDE_STEP = 23;
	private static final int ROW_HEIGHT = 34;
	private static final int ROW_GAP = 4;
	private static final int SIDEBAR_WIDTH = 116;
	private static final int HEADER_HEIGHT = 34;
	private static final int STRIP_HEIGHT = 28;
	private static final int CARD_HEIGHT = 64;
	private static final int DROPDOWN_ROWS = 8;
	private static final int DROPDOWN_ROW = 18;

	private final Screen parent;
	private final List<Setting> settings = LuceatSettings.create();
	private Map<String, Object> baseline;
	private String baselineKey;
	private SpeedTestPage speed;
	private final List<String> redo = new ArrayList<>();
	private String iconTip;
	private boolean pickerShown;
	private final PerfTest perf = new PerfTest();
	private final TextInput search = new TextInput(32);
	private final List<String> undo = new ArrayList<>();
	private PreviewPanel panel;
	private BlocksTab blocks;
	private String section = ALL;
	private float scroll;
	private int dropdownScroll;
	private Setting dragging;
	private String dragBefore;
	private Setting hovered;
	private Setting flash;
	private long flashUntil;
	private boolean rebuildPending;
	private Presets.Preset cachedPreset;
	private String cachedJson = "";

	private int left;
	private int top;
	private int right;
	private int bottom;
	private int listLeft;
	private int listRight;
	private int sideLeft;
	private boolean showSide;

	public LuceatScreen(Screen parent) {
		super(Component.literal("Luceat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int windowWidth = Math.min(this.width - 20, 940);
		int windowHeight = Math.min(this.height - 20, 520);
		left = (this.width - windowWidth) / 2;
		top = (this.height - windowHeight) / 2;
		right = left + windowWidth;
		bottom = top + windowHeight;
		showSide = windowWidth >= 700;
		int sideWidth = windowWidth >= 860 ? 310 : 270;
		sideLeft = right - sideWidth - 8;
		listLeft = left + SIDEBAR_WIDTH + 10;
		listRight = showSide ? sideLeft - 14 : right - 14;
		if (panel == null) {
			panel = new PreviewPanel(this.font);
			blocks = new BlocksTab(this.font);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		perf.cancel();
		if (speed != null) {
			speed.close();
		}
		LuceatConfig.save();
		flushBlocks();
		if (rebuildPending) {
			ShadowVolume.reset();
		}
		if (panel != null) {
			panel.close();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (SPEED.equals(section)) {
			speed().tick();
		}
		if (!pickerShown && !LuceatConfig.get().presetChosen && this.minecraft != null && this.minecraft.screen == this) {
			pickerShown = true;
			this.minecraft.setScreen(new PresetPicker(this));
		}
	}

	private SpeedTestPage speed() {
		if (speed == null) {
			speed = new SpeedTestPage();
		}
		return speed;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		graphics.fill(0, 0, this.width, this.height, LuceatLook.BACKDROP);
	}

	private boolean searching() {
		return search.focused() || !search.text().isEmpty();
	}

	private boolean specialPage() {
		return PRESETS.equals(section) || BLOCKS.equals(section) || ABOUT.equals(section) || SPEED.equals(section);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		LuceatLook.roundOutlined(graphics, left, top, right, bottom, LuceatLook.WINDOW, LuceatLook.BORDER);
		hovered = null;
		iconTip = null;
		drawSidebar(graphics, mouseX, mouseY);
		if (searching() || !specialPage()) {
			drawSettings(graphics, mouseX, mouseY);
		} else if (PRESETS.equals(section)) {
			drawPresets(graphics, mouseX, mouseY);
		} else if (BLOCKS.equals(section)) {
			blocks.drawList(graphics, listLeft, listTop(), listRight, listBottom(), mouseX, mouseY);
			if (showSide) {
				blocks.drawEditor(graphics, sideLeft, listTop(), right - 8, listBottom(), mouseX, mouseY);
			}
		} else if (SPEED.equals(section)) {
			speed().render(graphics, listLeft, listTop(), right - 8, listBottom(), mouseX, mouseY, partial);
		} else {
			drawAbout(graphics);
		}
		if (showSide && (searching() || !(BLOCKS.equals(section) || ABOUT.equals(section) || SPEED.equals(section)))) {
			drawSide(graphics, mouseX, mouseY);
		}
		drawHeader(graphics, mouseX, mouseY);
		if (search.focused()) {
			drawDropdown(graphics, mouseX, mouseY);
		}
		if (iconTip != null) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(iconTip), 200), mouseX, mouseY);
		} else if (hovered != null && dragging == null && !search.focused()) {
			showTooltip(graphics, hovered, mouseX, mouseY);
		}
		if (blocks != null && blocks.consumeDirty()) {
			ShadowVolume.reset();
		}
		perf.frame();
	}

	private int listTop() {
		return top + HEADER_HEIGHT + 10;
	}

	private int listBottom() {
		return bottom - 10;
	}

	private int toggleX() {
		String version = FabricLoader.getInstance().getModContainer("luceat").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		return left + 38 + this.font.width("Luceat") + 6 + this.font.width(version) + 16;
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(left + 1, top + 1, right - 1, top + HEADER_HEIGHT, LuceatLook.HEADER);
		graphics.fill(left + 1, top + HEADER_HEIGHT, right - 1, top + HEADER_HEIGHT + 1, LuceatLook.BORDER);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO_SMALL, left + 8, top + 5, 0.0F, 0.0F, 24, 24, 64, 64, 64, 64);
		graphics.text(this.font, "Luceat", left + 38, top + 13, LuceatLook.ACCENT, false);
		String version = FabricLoader.getInstance().getModContainer("luceat").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		graphics.text(this.font, version, left + 38 + this.font.width("Luceat") + 6, top + 13, LuceatLook.MUTED, false);

		boolean on = LuceatConfig.get().enabled;
		int tx = toggleX();
		LuceatLook.drawSwitch(graphics, tx, top + 10, on);
		graphics.text(this.font, on ? "On" : "Off", tx + 36, top + 13, on ? LuceatLook.GOOD : LuceatLook.MUTED, false);
		if (LuceatLook.inside(mouseX, mouseY, tx, top + 8, tx + 36 + this.font.width("Off") + 4, top + 28)) {
			iconTip = on ? "Luceat is on. Click to turn it off." : "Luceat is off. Click to turn it on.";
		}

		int closeX = right - 28;
		boolean closeHover = LuceatLook.inside(mouseX, mouseY, closeX, top + 7, closeX + 20, top + 27);
		LuceatLook.round(graphics, closeX, top + 7, closeX + 20, top + 27, closeHover ? 0xFF4A2030 : LuceatLook.CARD);
		graphics.text(this.font, "x", closeX + 7, top + 13, closeHover ? LuceatLook.BAD : LuceatLook.SOFT, false);

		int undoX1 = closeX - 8;
		int undoX0 = undoX1 - 52;
		boolean canUndo = !undo.isEmpty();
		boolean undoHover = canUndo && LuceatLook.inside(mouseX, mouseY, undoX0, top + 7, undoX1, top + 27);
		LuceatLook.roundOutlined(graphics, undoX0, top + 7, undoX1, top + 27, undoHover ? LuceatLook.CARD_HOVER : LuceatLook.CARD, canUndo ? LuceatLook.ACCENT_DIM : LuceatLook.BORDER);
		graphics.text(this.font, "Undo", undoX0 + (52 - this.font.width("Undo")) / 2, top + 13, canUndo ? LuceatLook.TEXT : LuceatLook.MUTED, false);

		int sx0 = searchX0();
		int sx1 = searchX1();
		search.draw(graphics, this.font, sx0, top + 7, sx1, top + 27, "Search settings");
	}

	private int searchX1() {
		return right - 28 - 8 - 52 - 8;
	}

	private int searchX0() {
		return searchX1() - 190;
	}

	private List<Setting> matches() {
		List<Setting> result = new ArrayList<>();
		String query = search.text().trim();
		for (Setting setting : settings) {
			if (query.isEmpty() || setting.matches(query)) {
				result.add(setting);
			}
		}
		return result;
	}

	private void drawDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Setting> found = matches();
		int x0 = searchX1() - 270;
		int x1 = searchX1();
		int y0 = top + 30;
		int rows = Math.min(DROPDOWN_ROWS, Math.max(1, found.size()));
		int y1 = y0 + rows * DROPDOWN_ROW + 8;
		dropdownScroll = Math.max(0, Math.min(dropdownScroll, Math.max(0, found.size() - DROPDOWN_ROWS)));
		LuceatLook.roundOutlined(graphics, x0, y0, x1, y1, 0xFF1B1B22, LuceatLook.ACCENT_DIM);
		if (found.isEmpty()) {
			graphics.text(this.font, "Nothing matches.", x0 + 10, y0 + 8, LuceatLook.MUTED, false);
			return;
		}
		for (int i = 0; i < rows; i++) {
			Setting setting = found.get(dropdownScroll + i);
			int ry = y0 + 4 + i * DROPDOWN_ROW;
			boolean over = LuceatLook.inside(mouseX, mouseY, x0 + 3, ry, x1 - 3, ry + DROPDOWN_ROW);
			if (over) {
				LuceatLook.round(graphics, x0 + 3, ry, x1 - 3, ry + DROPDOWN_ROW, LuceatLook.ACCENT_SOFT);
			}
			graphics.text(this.font, setting.label(), x0 + 10, ry + 5, over ? 0xFFFFFFFF : LuceatLook.TEXT, false);
			graphics.text(this.font, setting.section(), x1 - 10 - this.font.width(setting.section()), ry + 5, over ? LuceatLook.SOFT : LuceatLook.MUTED, false);
		}
		if (found.size() > DROPDOWN_ROWS) {
			String more = (found.size() - DROPDOWN_ROWS - dropdownScroll) + " more, scroll";
			graphics.text(this.font, more, x0 + 10, y1 - 1, LuceatLook.MUTED, false);
		}
	}

	private boolean clickDropdown(double mx, double my) {
		List<Setting> found = matches();
		int x0 = searchX1() - 270;
		int x1 = searchX1();
		int y0 = top + 30;
		int rows = Math.min(DROPDOWN_ROWS, Math.max(1, found.size()));
		if (!LuceatLook.inside(mx, my, x0, y0, x1, y0 + rows * DROPDOWN_ROW + 8)) {
			return false;
		}
		for (int i = 0; i < rows && dropdownScroll + i < found.size(); i++) {
			int ry = y0 + 4 + i * DROPDOWN_ROW;
			if (LuceatLook.inside(mx, my, x0 + 3, ry, x1 - 3, ry + DROPDOWN_ROW)) {
				jumpTo(found.get(dropdownScroll + i));
				return true;
			}
		}
		return true;
	}

	private void jumpTo(Setting setting) {
		search.focus(false);
		search.set("");
		setSection(setting.section());
		List<Object> rows = items();
		int offset = 0;
		for (Object item : rows) {
			if (item == setting) {
				break;
			}
			offset += itemHeight(item) + ROW_GAP;
		}
		scroll = Math.max(0.0F, offset - 40);
		flash = setting;
		flashUntil = System.currentTimeMillis() + 1800L;
	}

	private void setSection(String name) {
		if (SPEED.equals(section) && !SPEED.equals(name) && speed != null) {
			speed.close();
		}
		section = name;
		scroll = 0.0F;
		if (blocks != null) {
			blocks.unfocus();
		}
	}

	private List<String> sidebarNames() {
		List<String> names = new ArrayList<>();
		names.add(ALL);
		names.add(PRESETS);
		names.add("-");
		names.addAll(LuceatSettings.sections());
		names.add("-");
		names.add("~Tools");
		names.add(BLOCKS);
		names.add(SPEED);
		names.add("-");
		names.add(ABOUT);
		return names;
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR_WIDTH;
		int y = top + HEADER_HEIGHT + 10;
		for (String name : sidebarNames()) {
			if (name.equals("-")) {
				graphics.fill(x0 + 8, y + 1, x1 - 8, y + 2, LuceatLook.BORDER);
				y += 6;
				continue;
			}
			if (name.startsWith("~")) {
				graphics.text(this.font, name.substring(1), x0 + 10, y + 2, LuceatLook.MUTED, false);
				y += 14;
				continue;
			}
			boolean selected = !searching() && name.equals(section);
			boolean hover = LuceatLook.inside(mouseX, mouseY, x0, y, x1, y + SIDE_ITEM);
			if (selected) {
				LuceatLook.round(graphics, x0, y, x1, y + SIDE_ITEM, LuceatLook.ACCENT_SOFT);
				graphics.fill(x0, y + 4, x0 + 2, y + SIDE_ITEM - 4, LuceatLook.ACCENT);
			} else if (hover) {
				LuceatLook.round(graphics, x0, y, x1, y + SIDE_ITEM, LuceatLook.CARD);
			}
			graphics.text(this.font, name, x0 + 10, y + 7, selected ? LuceatLook.TEXT : hover ? LuceatLook.TEXT : LuceatLook.SOFT, false);
			y += SIDE_STEP;
		}
		long bytes = ShadowVolume.bytes() + PatternAtlas.bytes() + ShapeMasks.bytes();
		int footer = bottom - 30;
		if (footer > y + 4) {
			graphics.text(this.font, "Video memory", x0 + 4, footer, LuceatLook.MUTED, false);
			graphics.text(this.font, String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0), x0 + 4, footer + 11, bytes == 0 ? LuceatLook.MUTED : LuceatLook.TEXT, false);
		}
	}

	private boolean clickSidebar(double mx, double my) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR_WIDTH;
		int y = top + HEADER_HEIGHT + 10;
		for (String name : sidebarNames()) {
			if (name.equals("-")) {
				y += 6;
				continue;
			}
			if (name.startsWith("~")) {
				y += 14;
				continue;
			}
			if (LuceatLook.inside(mx, my, x0, y, x1, y + SIDE_ITEM)) {
				search.set("");
				setSection(name);
				return true;
			}
			y += SIDE_STEP;
		}
		return false;
	}

	private List<Setting> sectionSettings() {
		List<Setting> result = new ArrayList<>();
		for (Setting setting : settings) {
			if (setting.section().equals(section)) {
				result.add(setting);
			}
		}
		return result;
	}

	private Setting masterToggle() {
		for (Setting setting : settings) {
			if (setting.section().equals(LuceatSettings.GENERAL) && setting.label().equals("Luceat")) {
				return setting;
			}
		}
		return null;
	}

	private List<Object> items() {
		List<Object> out = new ArrayList<>();
		if (searching()) {
			out.addAll(matches());
			return out;
		}
		if (ALL.equals(section)) {
			Setting master = masterToggle();
			if (master != null) {
				out.add(master);
			}
			for (String name : LuceatSettings.sections()) {
				out.add(name);
				for (Setting setting : settings) {
					if (setting.section().equals(name) && setting != master) {
						out.add(setting);
					}
				}
			}
			return out;
		}
		out.addAll(sectionSettings());
		return out;
	}

	private int itemHeight(Object item) {
		if (item instanceof String) {
			return HEAD_HEIGHT;
		}
		return item instanceof ChoiceSetting<?> ? CHOICE_HEIGHT : ROW_HEIGHT;
	}

	private List<Setting> settingsOf(List<Object> rows) {
		List<Setting> result = new ArrayList<>();
		for (Object item : rows) {
			if (item instanceof Setting setting) {
				result.add(setting);
			}
		}
		return result;
	}

	private List<Setting> settingsIn(String sectionName) {
		List<Setting> result = new ArrayList<>();
		for (Setting setting : settings) {
			if (setting.section().equals(sectionName)) {
				result.add(setting);
			}
		}
		return result;
	}

	private Map<String, Object> baseline() {
		String key = String.valueOf(LuceatConfig.get().preset);
		if (baseline == null || !key.equals(baselineKey)) {
			baseline = new HashMap<>();
			for (Setting setting : LuceatSettings.create(Presets.baseline(LuceatConfig.get()))) {
				baseline.put(setting.label(), setting.snapshot());
			}
			baselineKey = key;
		}
		return baseline;
	}

	private boolean isDefault(Setting setting) {
		return Setting.same(setting.snapshot(), baseline().get(setting.label()));
	}

	private int changedCount(List<Setting> rows) {
		int changed = 0;
		for (Setting setting : rows) {
			if (!isDefault(setting)) {
				changed++;
			}
		}
		return changed;
	}

	private void resetSettings(List<Setting> rows) {
		pushUndo();
		for (Setting setting : rows) {
			if (!isDefault(setting)) {
				setting.restore(baseline().get(setting.label()));
				applyChanged(setting);
			}
		}
		flushRebuild();
	}

	private int totalHeight(List<Object> rows) {
		int total = 0;
		for (Object item : rows) {
			total += itemHeight(item) + ROW_GAP;
		}
		return total;
	}

	private int maxScroll(List<Object> rows) {
		return Math.max(0, totalHeight(rows) - (listBottom() - listTop() - STRIP_HEIGHT));
	}

	private String fit(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && this.font.width(cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}

	private void drawResetIcon(GuiGraphicsExtractor graphics, int x, int y, boolean enabled, boolean over, int mouseX, int mouseY, String tip) {
		int colour = !enabled ? LuceatLook.MUTED : over ? LuceatLook.ACCENT : LuceatLook.WARN;
		LuceatLook.resetIcon(graphics, x, y, colour);
		if (enabled && over && mouseX >= 0) {
			iconTip = tip;
		}
	}

	private void drawStrip(GuiGraphicsExtractor graphics, String title, int mouseX, int mouseY, List<Setting> rows) {
		graphics.text(this.font, title, listLeft + 2, listTop() + 4, LuceatLook.ACCENT, false);
		int changed = changedCount(rows);
		int bx1 = listRight;
		boolean enabled = changed > 0;
		boolean over = enabled && LuceatLook.inside(mouseX, mouseY, bx1 - 22, listTop(), bx1, listTop() + 20);
		LuceatLook.roundOutlined(graphics, bx1 - 22, listTop(), bx1, listTop() + 20, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, enabled ? LuceatLook.ACCENT_DIM : LuceatLook.BORDER);
		drawResetIcon(graphics, bx1 - 22 + (22 - LuceatLook.ICON_W) / 2, listTop() + (20 - LuceatLook.ICON_H) / 2, enabled, over, mouseX, mouseY,
			searching() ? "Put these back to the preset values" : ALL.equals(section) ? "Put every setting back to the preset values" : "Put this section back to the preset values");
		String state = changed == 0 ? "All at preset values" : changed + " changed";
		graphics.text(this.font, state, bx1 - 22 - 10 - this.font.width(state), listTop() + 6, changed == 0 ? LuceatLook.MUTED : LuceatLook.WARN, false);
	}

	private boolean stripReset(double mx, double my) {
		if (!LuceatLook.inside(mx, my, listRight - 22, listTop(), listRight, listTop() + 20)) {
			return false;
		}
		resetSettings(settingsOf(items()));
		return true;
	}

	private void drawSectionHead(GuiGraphicsExtractor graphics, String name, int y, int mouseX, int mouseY, int areaTop) {
		graphics.text(this.font, name, listLeft + 2, y + 8, LuceatLook.ACCENT, false);
		int lineX = listLeft + 4 + this.font.width(name);
		graphics.fill(lineX, y + 12, listRight - 24, y + 13, LuceatLook.BORDER);
		List<Setting> group = settingsIn(name);
		boolean enabled = changedCount(group) > 0;
		int ix = listRight - 16;
		boolean over = enabled && mouseY >= areaTop && mouseY < listBottom() && LuceatLook.inside(mouseX, mouseY, ix - 3, y + 4, ix + LuceatLook.ICON_W + 3, y + 20);
		drawResetIcon(graphics, ix, y + 9, enabled, over, mouseX, mouseY, "Put " + name.toLowerCase(Locale.ROOT) + " back to the preset values");
	}

	private void drawSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Object> rows = items();
		String title = searching() ? "Search results" : section;
		drawStrip(graphics, title, mouseX, mouseY, settingsOf(rows));
		int areaTop = listTop() + STRIP_HEIGHT;
		scroll = Math.max(0.0F, Math.min(scroll, maxScroll(rows)));
		graphics.enableScissor(listLeft - 2, areaTop, listRight + 2, listBottom());
		int y = areaTop - Math.round(scroll);
		for (Object item : rows) {
			int height = itemHeight(item);
			boolean inView = y + height > areaTop && y < listBottom();
			if (item instanceof String name) {
				if (inView) {
					drawSectionHead(graphics, name, y, mouseX, mouseY, areaTop);
				}
			} else if (item instanceof Setting setting) {
				boolean hover = inView && !search.focused() && LuceatLook.inside(mouseX, mouseY, listLeft, y, listRight, y + height) && mouseY >= areaTop && mouseY < listBottom();
				if (hover) {
					hovered = setting;
				}
				if (inView) {
					drawRow(graphics, setting, y, height, hover, mouseX, mouseY, areaTop);
				}
			}
			y += height + ROW_GAP;
		}
		graphics.disableScissor();
		if (rows.isEmpty()) {
			graphics.text(this.font, "Nothing matches that.", listLeft + 8, areaTop + 8, LuceatLook.MUTED, false);
		}
		int max = maxScroll(rows);
		if (max > 0) {
			int trackTop = areaTop;
			int trackHeight = listBottom() - areaTop;
			int thumb = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
			int thumbTop = trackTop + Math.round((trackHeight - thumb) * (scroll / max));
			graphics.fill(listRight + 4, thumbTop, listRight + 6, thumbTop + thumb, LuceatLook.TRACK);
		}
	}

	private int choiceX(ChoiceSetting<?> choice) {
		return listRight - 10 - 100;
	}

	private void drawRow(GuiGraphicsExtractor graphics, Setting setting, int y, int height, boolean hover, int mouseX, int mouseY, int areaTop) {
		boolean flashing = setting == flash && System.currentTimeMillis() < flashUntil;
		int fill = flashing ? LuceatLook.mix(LuceatLook.CARD, LuceatLook.ACCENT, 0.35F) : hover ? LuceatLook.CARD_HOVER : LuceatLook.CARD;
		LuceatLook.round(graphics, listLeft, y, listRight, y + height, fill);
		graphics.text(this.font, setting.label(), listLeft + 10, y + 7, LuceatLook.TEXT, false);
		boolean changed = !isDefault(setting);
		if (changed) {
			int rx = listLeft + 10 + this.font.width(setting.label()) + 8;
			boolean over = mouseY >= areaTop && mouseY < listBottom() && LuceatLook.inside(mouseX, mouseY, rx - 3, y + 4, rx + LuceatLook.ICON_W + 3, y + 17);
			drawResetIcon(graphics, rx, y + 7, true, over, mouseX, mouseY, "Back to the preset value");
		}
		String costText = "Cost: " + setting.cost().label();
		graphics.text(this.font, costText, listLeft + 10, y + 19, setting.cost().colour(), false);
		PerfTest.Result measured = perf.result(setting);
		if (measured != null) {
			String tested = String.format(Locale.ROOT, "tested: %s, %.1f ms", measured.cost().label().toLowerCase(Locale.ROOT), measured.millis());
			graphics.text(this.font, tested, listLeft + 16 + this.font.width(costText), y + 19, measured.cost().colour(), false);
		}
		int controlRight = listRight - 10;
		if (setting instanceof ToggleSetting toggle) {
			int x = controlRight - 30;
			LuceatLook.drawSwitch(graphics, x, y + 10, toggle.value());
			drawTestPill(graphics, setting, x - 8, y, mouseX, mouseY);
		} else if (setting instanceof SliderSetting slider) {
			int trackLeft = controlRight - 110;
			graphics.fill(trackLeft, y + 16, controlRight, y + 18, LuceatLook.TRACK);
			int filled = Math.round(110 * slider.fraction());
			graphics.fill(trackLeft, y + 16, trackLeft + filled, y + 18, LuceatLook.ACCENT);
			LuceatLook.round(graphics, trackLeft + filled - 4, y + 12, trackLeft + filled + 4, y + 22, dragging == setting ? 0xFFFFFFFF : 0xFFE6C9FF);
			String value = slider.valueText();
			graphics.text(this.font, value, trackLeft - 8 - this.font.width(value), y + 13, LuceatLook.SOFT, false);
		} else if (setting instanceof ChoiceSetting<?> choice) {
			int x = choiceX(choice);
			boolean over = LuceatLook.inside(mouseX, mouseY, x, y + 8, x + 100, y + 26) && mouseY >= areaTop && mouseY < listBottom();
			LuceatLook.roundOutlined(graphics, x, y + 8, x + 100, y + 26, over ? LuceatLook.ACCENT_DIM : LuceatLook.ACCENT_SOFT, LuceatLook.ACCENT);
			String name = choice.name(choice.index());
			graphics.text(this.font, name, x + (100 - this.font.width(name)) / 2, y + 14, 0xFFFFFFFF, false);
			String note = choice.note(choice.index());
			if (!note.isEmpty()) {
				graphics.text(this.font, fit(note, listRight - listLeft - 20), listLeft + 10, y + 33, LuceatLook.SOFT, false);
			}
			drawTestPill(graphics, setting, x - 8, y, mouseX, mouseY);
		}
	}

	private void drawTestPill(GuiGraphicsExtractor graphics, Setting setting, int rightEdge, int y, int mouseX, int mouseY) {
		if (!setting.testable()) {
			return;
		}
		boolean running = perf.running() && perf.target() == setting;
		int width = 34;
		int x = rightEdge - width;
		boolean over = LuceatLook.inside(mouseX, mouseY, x, y + 9, x + width, y + 25);
		LuceatLook.roundOutlined(graphics, x, y + 9, x + width, y + 25, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, running ? LuceatLook.ACCENT : LuceatLook.BORDER);
		String label = running ? perf.progress() + "%" : "Test";
		graphics.text(this.font, label, x + (width - this.font.width(label)) / 2, y + 13, running ? LuceatLook.ACCENT : LuceatLook.SOFT, false);
	}

	private void showTooltip(GuiGraphicsExtractor graphics, Setting setting, int mouseX, int mouseY) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.addAll(this.font.split(Component.literal(setting.label()).withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF)), 230));
		lines.addAll(this.font.split(Component.literal(setting.help()).withStyle(Style.EMPTY.withColor(0xDDDDE6)), 230));
		lines.addAll(this.font.split(Component.literal("Cost: " + setting.cost().label()).withStyle(Style.EMPTY.withColor(setting.cost().colour() & 0xFFFFFF)), 230));
		PerfTest.Result measured = perf.result(setting);
		if (measured != null) {
			lines.addAll(this.font.split(Component.literal("Last test: " + measured.text()).withStyle(Style.EMPTY.withColor(measured.cost().colour() & 0xFFFFFF)), 230));
		}
		graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	private Presets.Preset currentPreset() {
		String json = LuceatConfig.get().asJson();
		if (!json.equals(cachedJson)) {
			cachedJson = json;
			cachedPreset = Presets.current(LuceatConfig.get());
		}
		return cachedPreset;
	}

	private int presetButtonX0() {
		return listLeft + 12;
	}

	private int presetButtonY0() {
		return listTop() + STRIP_HEIGHT + 78;
	}

	private void drawPresets(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, "Presets", listLeft + 2, listTop() + 4, LuceatLook.ACCENT, false);
		Presets.Preset current = currentPreset();
		Presets.Preset named = Presets.byName(LuceatConfig.get().preset);
		int y = listTop() + STRIP_HEIGHT;
		LuceatLook.roundOutlined(graphics, listLeft, y, listRight, y + 138, LuceatLook.CARD, current != null ? LuceatLook.GOOD : LuceatLook.BORDER);
		String title = current != null ? current.name() : named != null ? named.name() + " (changed)" : "No preset chosen";
		graphics.text(this.font, title, listLeft + 12, y + 10, LuceatLook.TEXT, false);
		Presets.Preset shown = current != null ? current : named;
		if (shown != null) {
			String cost = "Cost: " + shown.cost();
			graphics.text(this.font, cost, listRight - 12 - this.font.width(cost), y + 10, costColour(shown.cost()), false);
			int lineY = y + 26;
			int count = 0;
			for (FormattedCharSequence line : this.font.split(Component.literal(shown.blurb()).withStyle(Style.EMPTY.withColor(0xB4B4C4)), listRight - listLeft - 24)) {
				if (count++ >= 3) {
					break;
				}
				graphics.text(this.font, line, listLeft + 12, lineY, 0xFFB4B4C4, false);
				lineY += 10;
			}
		} else {
			drawWrappedText(graphics, "Pick a preset to start from. You can change anything after.", listLeft + 12, y + 26, listRight - listLeft - 24, LuceatLook.SOFT);
		}
		int bx0 = presetButtonX0();
		int by0 = presetButtonY0();
		int bw = this.font.width("Choose preset") + 24;
		boolean over = LuceatLook.inside(mouseX, mouseY, bx0, by0, bx0 + bw, by0 + 22);
		LuceatLook.round(graphics, bx0, by0, bx0 + bw, by0 + 22, over ? LuceatLook.ACCENT : LuceatLook.ACCENT_DIM);
		graphics.text(this.font, "Choose preset", bx0 + 12, by0 + 7, 0xFFFFFFFF, false);
		drawWrappedText(graphics, "The preset you pick is what the reset buttons go back to.", listLeft + 2, y + 146, listRight - listLeft - 4, LuceatLook.MUTED);
	}

	private static int costColour(String cost) {
		return switch (cost) {
			case "Very light" -> LuceatLook.GOOD;
			case "Light" -> LuceatLook.GOOD;
			case "Medium" -> LuceatLook.WARN;
			case "Heavy" -> LuceatLook.ORANGE;
			default -> LuceatLook.BAD;
		};
	}

	private int drawWrappedText(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int colour) {
		for (FormattedCharSequence line : this.font.split(Component.literal(text), width)) {
			graphics.text(this.font, line, x, y, colour, false);
			y += 11;
		}
		return y;
	}

	private void drawAbout(GuiGraphicsExtractor graphics) {
		int x = listLeft + 4;
		int y = listTop() + 6;
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, 80, 80, 256, 256, 256, 256);
		graphics.text(this.font, "Luceat", x + 96, y + 18, LuceatLook.ACCENT, false);
		String version = FabricLoader.getInstance().getModContainer("luceat").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		graphics.text(this.font, "Version " + version, x + 96, y + 32, LuceatLook.SOFT, false);
		graphics.text(this.font, "Made by bewf", x + 96, y + 46, LuceatLook.SOFT, false);
		y += 94;
		String[] paragraphs = {
			"Luceat adds dynamic lights to held items, dropped items, other players, placed blocks, and more.",
			"You can customise light colours, brightness, and shadows to create a look you like. You can add lights to any block you want.",
			"You can hover a setting to see it in the live preview.",
			"Open this menu any time with /luceat or the " + LuceatMenu.keyLabel() + " key."
		};
		for (String paragraph : paragraphs) {
			y = drawWrappedText(graphics, paragraph, x, y, listRight - x, LuceatLook.SOFT) + 6;
		}
		String note = RenderBackend.note();
		y = drawWrappedText(graphics, "Renderer: " + RenderBackend.kind().label(), x, y, listRight - x, LuceatLook.ACCENT) + 2;
		if (note != null) {
			drawWrappedText(graphics, note, x, y, listRight - x, LuceatLook.SOFT);
		}
	}

	private void drawSide(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		LuceatConfig live = LuceatConfig.get();
		if (PRESETS.equals(section) && !searching()) {
			panel.setOverride(null);
			panel.setSpec(new SceneSpec(Scenes.scene("showcase"), "This is how your current settings look.", null, null, null, null));
		} else {
			panel.setOverride(null);
			if (hovered != null) {
				panel.setSpec(Scenes.forSetting(hovered.label()));
			}
		}
		panel.setInteractive(dragging != null);
		panel.draw(graphics, sideLeft, listTop(), right - 8 - sideLeft, listBottom() - listTop(), mouseX, mouseY, perf.running(), perf.note());
	}

	private void applyChanged(Setting setting) {
		if (setting.rebuildsShadows()) {
			rebuildPending = true;
		}
	}

	private void flushRebuild() {
		if (rebuildPending) {
			rebuildPending = false;
			ShadowVolume.reset();
		}
	}

	private void flushBlocks() {
		if (blocks != null && blocks.consumeDirty()) {
			ShadowVolume.reset();
		}
	}

	private void pushUndo() {
		String snapshot = LuceatConfig.snapshot();
		if (undo.isEmpty() || !undo.get(undo.size() - 1).equals(snapshot)) {
			undo.add(snapshot);
			if (undo.size() > 40) {
				undo.remove(0);
			}
		}
		redo.clear();
	}

	private void undoLast() {
		if (undo.isEmpty()) {
			return;
		}
		redo.add(LuceatConfig.snapshot());
		String previous = undo.remove(undo.size() - 1);
		LuceatConfig.restore(previous);
		LightSpecs.clearCache();
		ShadowVolume.reset();
	}

	private void redoLast() {
		if (redo.isEmpty()) {
			return;
		}
		undo.add(LuceatConfig.snapshot());
		String next = redo.remove(redo.size() - 1);
		LuceatConfig.restore(next);
		LightSpecs.clearCache();
		ShadowVolume.reset();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		if (search.focused() && clickDropdown(mx, my)) {
			return true;
		}
		boolean onSearch = LuceatLook.inside(mx, my, searchX0(), top + 7, searchX1(), top + 27);
		if (onSearch) {
			search.mouseClicked(mx, my);
			search.focus(true);
			if (blocks != null) {
				blocks.unfocus();
			}
			return true;
		}
		search.focus(false);
		int closeX = right - 28;
		if (LuceatLook.inside(mx, my, closeX, top + 7, closeX + 20, top + 27)) {
			onClose();
			return true;
		}
		int undoX1 = closeX - 8;
		if (LuceatLook.inside(mx, my, undoX1 - 52, top + 7, undoX1, top + 27)) {
			undoLast();
			return true;
		}
		int tx = toggleX();
		if (LuceatLook.inside(mx, my, tx, top + 8, tx + 36 + this.font.width("Off") + 4, top + 28)) {
			pushUndo();
			LuceatConfig.get().enabled = !LuceatConfig.get().enabled;
			return true;
		}
		if (clickSidebar(mx, my)) {
			return true;
		}
		boolean settingsPage = searching() || !specialPage();
		if (showSide && (settingsPage || PRESETS.equals(section)) && panel.mouseClicked(mx, my)) {
			return true;
		}
		if (settingsPage) {
			if (stripReset(mx, my) || clickList(mx, my)) {
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (BLOCKS.equals(section)) {
			return blocks.mouseClicked(mx, my) || super.mouseClicked(event, doubleClick);
		}
		if (PRESETS.equals(section)) {
			return clickPresets(mx, my) || super.mouseClicked(event, doubleClick);
		}
		if (SPEED.equals(section)) {
			return speed().click(mx, my, event.button()) || super.mouseClicked(event, doubleClick);
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickPresets(double mx, double my) {
		int bx0 = presetButtonX0();
		int by0 = presetButtonY0();
		int bw = this.font.width("Choose preset") + 24;
		if (LuceatLook.inside(mx, my, bx0, by0, bx0 + bw, by0 + 22)) {
			this.minecraft.setScreen(new PresetPicker(this));
			return true;
		}
		return false;
	}

	private boolean clickList(double mx, double my) {
		int areaTop = listTop() + STRIP_HEIGHT;
		if (my < areaTop || my >= listBottom()) {
			return false;
		}
		List<Object> rows = items();
		int y = areaTop - Math.round(scroll);
		for (Object item : rows) {
			int height = itemHeight(item);
			if (item instanceof String name) {
				int ix = listRight - 16;
				if (LuceatLook.inside(mx, my, ix - 3, y + 4, ix + LuceatLook.ICON_W + 3, y + 20)) {
					List<Setting> group = settingsIn(name);
					if (changedCount(group) > 0) {
						resetSettings(group);
					}
					return true;
				}
			} else if (item instanceof Setting setting && LuceatLook.inside(mx, my, listLeft, y, listRight, y + height)) {
				clickRow(setting, y, mx, my);
				return true;
			}
			y += height + ROW_GAP;
		}
		return false;
	}

	private void clickRow(Setting setting, int y, double mx, double my) {
		if (!isDefault(setting)) {
			int rx = listLeft + 10 + this.font.width(setting.label()) + 8;
			if (LuceatLook.inside(mx, my, rx - 3, y + 4, rx + LuceatLook.ICON_W + 3, y + 17)) {
				resetSettings(List.of(setting));
				return;
			}
		}
		int controlRight = listRight - 10;
		if (setting instanceof ToggleSetting toggle) {
			int x = controlRight - 30;
			int pillRight = x - 8;
			if (setting.testable() && LuceatLook.inside(mx, my, pillRight - 34, y + 9, pillRight, y + 25)) {
				perf.start(setting);
				return;
			}
			pushUndo();
			toggle.set(!toggle.value());
			applyChanged(setting);
			flushRebuild();
		} else if (setting instanceof SliderSetting slider) {
			dragBefore = LuceatConfig.snapshot();
			dragging = slider;
			dragSlider(slider, mx);
		} else if (setting instanceof ChoiceSetting<?> choice) {
			int x = choiceX(choice);
			int pillRight = x - 8;
			if (LuceatLook.inside(mx, my, pillRight - 34, y + 9, pillRight, y + 25)) {
				perf.start(setting);
				return;
			}
			if (LuceatLook.inside(mx, my, x, y + 8, x + 100, y + 26)) {
				pushUndo();
				choice.next();
				applyChanged(setting);
				flushRebuild();
			}
		}
	}

	private void dragSlider(SliderSetting slider, double mx) {
		int controlRight = listRight - 10;
		int trackLeft = controlRight - 110;
		slider.setFraction((float) ((mx - trackLeft) / 110.0));
		applyChanged(slider);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging instanceof SliderSetting slider) {
			dragSlider(slider, event.x());
			return true;
		}
		if (search.mouseDragged(event.x())) {
			return true;
		}
		if (BLOCKS.equals(section) && blocks.mouseDragged(event.x())) {
			return true;
		}
		if (panel != null && panel.mouseDragged(event.x(), event.y())) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = dragging != null;
		if (dragging != null && dragBefore != null && !dragBefore.equals(LuceatConfig.snapshot())) {
			undo.add(dragBefore);
			redo.clear();
			if (undo.size() > 40) {
				undo.remove(0);
			}
		}
		dragging = null;
		dragBefore = null;
		flushRebuild();
		boolean searchUp = search.mouseReleased();
		boolean blockUp = BLOCKS.equals(section) && blocks.mouseReleased();
		boolean panelUp = panel != null && panel.mouseReleased();
		return was || searchUp || blockUp || panelUp || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (search.focused()) {
			dropdownScroll -= (int) Math.signum(scrollY);
			return true;
		}
		if (BLOCKS.equals(section) && !searching()) {
			return blocks.mouseScrolled(x, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
		}
		if (SPEED.equals(section) && !searching()) {
			return speed().scroll(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
		}
		if (showSide && x >= sideLeft && !ABOUT.equals(section)) {
			panel.mouseScrolled(x, y, scrollY);
			return true;
		}
		if (x >= listLeft && x < listRight + 8) {
			scroll -= (float) scrollY * 24.0F;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (BLOCKS.equals(section) && !search.focused()) {
			return blocks.charTyped(event) || super.charTyped(event);
		}
		if (!search.focused() && !ABOUT.equals(section) && !SPEED.equals(section) && event.codepoint() >= 32 && event.codepoint() != 127) {
			search.focus(true);
		}
		if (search.charTyped(event)) {
			scroll = 0.0F;
			dropdownScroll = 0;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (BLOCKS.equals(section) && !search.focused() && blocks.typing()) {
			if (blocks.keyPressed(event)) {
				return true;
			}
		}
		if (event.isEscape()) {
			if (search.focused() || !search.text().isEmpty()) {
				search.focus(false);
				search.set("");
				return true;
			}
			if (BLOCKS.equals(section) && blocks.typing()) {
				blocks.unfocus();
				return true;
			}
			return super.keyPressed(event);
		}
		if (!search.focused() && event.hasControlDown()) {
			int key = event.key();
			if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
				if (event.hasShiftDown()) {
					redoLast();
				} else {
					undoLast();
				}
				return true;
			}
			if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
				redoLast();
				return true;
			}
		}
		if (!search.focused() && !search.text().isEmpty() && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
			search.focus(true);
		}
		if (search.keyPressed(event)) {
			scroll = 0.0F;
			dropdownScroll = 0;
			return true;
		}
		return super.keyPressed(event);
	}
}
