package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
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
	private static final String PRESETS = "Presets";
	private static final String BLOCKS = "Blocks";
	private static final String ABOUT = "About";
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo.png");
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
	private final Map<String, Object> defaults = new HashMap<>();
	private final PerfTest perf = new PerfTest();
	private final TextInput search = new TextInput(32);
	private final List<String> undo = new ArrayList<>();
	private PreviewPanel panel;
	private BlocksTab blocks;
	private String section = LuceatSettings.LIGHTING;
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
		for (Setting setting : LuceatSettings.create(new LuceatConfig())) {
			defaults.put(setting.label(), setting.snapshot());
		}
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		graphics.fill(0, 0, this.width, this.height, LuceatLook.BACKDROP);
	}

	private boolean searching() {
		return search.focused() || !search.text().isEmpty();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		LuceatLook.roundOutlined(graphics, left, top, right, bottom, LuceatLook.WINDOW, LuceatLook.BORDER);
		hovered = null;
		drawSidebar(graphics, mouseX, mouseY);
		Presets.Preset hoveredPreset = null;
		boolean specialTab = PRESETS.equals(section) || BLOCKS.equals(section) || ABOUT.equals(section);
		if (searching() || !specialTab) {
			drawSettings(graphics, mouseX, mouseY);
		} else if (PRESETS.equals(section)) {
			hoveredPreset = drawPresets(graphics, mouseX, mouseY);
		} else if (BLOCKS.equals(section)) {
			blocks.drawList(graphics, listLeft, listTop(), listRight, listBottom(), mouseX, mouseY);
			if (showSide) {
				blocks.drawEditor(graphics, sideLeft, listTop(), right - 8, listBottom(), mouseX, mouseY);
			}
		} else {
			drawAbout(graphics);
		}
		if (showSide && !BLOCKS.equals(section) && !ABOUT.equals(section)) {
			drawSide(graphics, mouseX, mouseY, hoveredPreset);
		}
		drawHeader(graphics, mouseX, mouseY);
		if (search.focused()) {
			drawDropdown(graphics, mouseX, mouseY);
		}
		if (hovered != null && dragging == null && !search.focused()) {
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

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(left + 1, top + 1, right - 1, top + HEADER_HEIGHT, LuceatLook.HEADER);
		graphics.fill(left + 1, top + HEADER_HEIGHT, right - 1, top + HEADER_HEIGHT + 1, LuceatLook.BORDER);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, left + 8, top + 5, 0.0F, 0.0F, 24, 24, 48, 48, 48, 48);
		graphics.text(this.font, "Luceat", left + 38, top + 13, LuceatLook.ACCENT, false);
		String version = FabricLoader.getInstance().getModContainer("luceat").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		graphics.text(this.font, version, left + 38 + this.font.width("Luceat") + 6, top + 13, LuceatLook.MUTED, false);

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
			graphics.text(this.font, setting.section(), x1 - 10 - this.font.width(setting.section()), ry + 5, LuceatLook.MUTED, false);
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
		section = setting.section();
		List<Setting> rows = sectionSettings();
		int index = Math.max(0, rows.indexOf(setting));
		scroll = Math.max(0.0F, index * (ROW_HEIGHT + ROW_GAP) - 40);
		flash = setting;
		flashUntil = System.currentTimeMillis() + 1800L;
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR_WIDTH;
		int y = top + HEADER_HEIGHT + 10;
		for (String name : sidebarNames()) {
			boolean selected = !searching() && name.equals(section);
			boolean hover = LuceatLook.inside(mouseX, mouseY, x0, y, x1, y + 24);
			if (selected) {
				LuceatLook.round(graphics, x0, y, x1, y + 24, LuceatLook.ACCENT_SOFT);
				graphics.fill(x0, y + 4, x0 + 2, y + 20, LuceatLook.ACCENT);
			} else if (hover) {
				LuceatLook.round(graphics, x0, y, x1, y + 24, LuceatLook.CARD);
			}
			graphics.text(this.font, name, x0 + 10, y + 8, selected ? LuceatLook.TEXT : LuceatLook.SOFT, false);
			y += 28;
			if (name.equals(PRESETS) || name.equals(LuceatSettings.GENERAL)) {
				graphics.fill(x0 + 8, y - 2, x1 - 8, y - 1, LuceatLook.BORDER);
				y += 4;
			}
		}
		long bytes = ShadowVolume.bytes() + PatternAtlas.bytes() + ShapeMasks.bytes();
		int footer = bottom - 36;
		graphics.text(this.font, "Video memory", x0 + 4, footer, LuceatLook.MUTED, false);
		graphics.text(this.font, String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0), x0 + 4, footer + 12, bytes == 0 ? LuceatLook.MUTED : LuceatLook.TEXT, false);
	}

	private List<String> sidebarNames() {
		List<String> names = new ArrayList<>();
		names.add(PRESETS);
		names.addAll(LuceatSettings.sections());
		names.add(BLOCKS);
		names.add(ABOUT);
		return names;
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

	private List<Setting> visible() {
		return searching() ? matches() : sectionSettings();
	}

	private boolean isDefault(Setting setting) {
		return Setting.same(setting.snapshot(), defaults.get(setting.label()));
	}

	private int maxScroll(int count) {
		return Math.max(0, count * (ROW_HEIGHT + ROW_GAP) - (listBottom() - listTop() - STRIP_HEIGHT));
	}

	private void drawStrip(GuiGraphicsExtractor graphics, String title, int mouseX, int mouseY, List<Setting> rows) {
		graphics.text(this.font, title, listLeft + 2, listTop() + 4, LuceatLook.ACCENT, false);
		int changed = 0;
		for (Setting setting : rows) {
			if (!isDefault(setting)) {
				changed++;
			}
		}
		int bx1 = listRight;
		String label = "Reset " + (searching() ? "these" : "section");
		int bw = this.font.width(label) + 16;
		boolean enabled = changed > 0;
		boolean over = enabled && LuceatLook.inside(mouseX, mouseY, bx1 - bw, listTop(), bx1, listTop() + 20);
		LuceatLook.roundOutlined(graphics, bx1 - bw, listTop(), bx1, listTop() + 20, over ? LuceatLook.CARD_HOVER : LuceatLook.CARD, enabled ? LuceatLook.ACCENT_DIM : LuceatLook.BORDER);
		graphics.text(this.font, label, bx1 - bw + 8, listTop() + 6, enabled ? LuceatLook.TEXT : LuceatLook.MUTED, false);
		String state = changed == 0 ? "All at default" : changed + (changed == 1 ? " changed" : " changed");
		graphics.text(this.font, state, bx1 - bw - 10 - this.font.width(state), listTop() + 6, changed == 0 ? LuceatLook.MUTED : LuceatLook.WARN, false);
	}

	private boolean stripReset(double mx, double my) {
		List<Setting> rows = visible();
		String label = "Reset " + (searching() ? "these" : "section");
		int bw = this.font.width(label) + 16;
		if (!LuceatLook.inside(mx, my, listRight - bw, listTop(), listRight, listTop() + 20)) {
			return false;
		}
		pushUndo();
		for (Setting setting : rows) {
			if (!isDefault(setting)) {
				setting.restore(defaults.get(setting.label()));
				applyChanged(setting);
			}
		}
		flushRebuild();
		return true;
	}

	private void drawSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Setting> rows = visible();
		drawStrip(graphics, searching() ? "Search results" : section, mouseX, mouseY, rows);
		int areaTop = listTop() + STRIP_HEIGHT;
		scroll = Math.max(0.0F, Math.min(scroll, maxScroll(rows.size())));
		graphics.enableScissor(listLeft - 2, areaTop, listRight + 2, listBottom());
		int y = areaTop - Math.round(scroll);
		for (Setting setting : rows) {
			boolean inView = y + ROW_HEIGHT > areaTop && y < listBottom();
			boolean hover = inView && !search.focused() && LuceatLook.inside(mouseX, mouseY, listLeft, y, listRight, y + ROW_HEIGHT) && mouseY >= areaTop && mouseY < listBottom();
			if (hover) {
				hovered = setting;
			}
			if (inView) {
				drawRow(graphics, setting, y, hover, mouseX, mouseY);
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();
		if (rows.isEmpty()) {
			graphics.text(this.font, "Nothing matches that.", listLeft + 8, areaTop + 8, LuceatLook.MUTED, false);
		}
		int max = maxScroll(rows.size());
		if (max > 0) {
			int trackTop = areaTop;
			int trackHeight = listBottom() - areaTop;
			int thumb = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
			int thumbTop = trackTop + Math.round((trackHeight - thumb) * (scroll / max));
			graphics.fill(listRight + 4, thumbTop, listRight + 6, thumbTop + thumb, LuceatLook.TRACK);
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, Setting setting, int y, boolean hover, int mouseX, int mouseY) {
		boolean flashing = setting == flash && System.currentTimeMillis() < flashUntil;
		int fill = flashing ? LuceatLook.mix(LuceatLook.CARD, LuceatLook.ACCENT, 0.35F) : hover ? LuceatLook.CARD_HOVER : LuceatLook.CARD;
		LuceatLook.round(graphics, listLeft, y, listRight, y + ROW_HEIGHT, fill);
		graphics.text(this.font, setting.label(), listLeft + 10, y + 7, LuceatLook.TEXT, false);
		boolean changed = !isDefault(setting);
		if (changed) {
			int rx = listLeft + 10 + this.font.width(setting.label()) + 8;
			boolean over = LuceatLook.inside(mouseX, mouseY, rx - 2, y + 4, rx + this.font.width("reset") + 3, y + 17);
			graphics.text(this.font, "reset", rx, y + 7, over ? LuceatLook.ACCENT : LuceatLook.WARN, false);
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
			boolean on = toggle.value();
			LuceatLook.round(graphics, x, y + 10, x + 30, y + 24, on ? LuceatLook.ACCENT : LuceatLook.TRACK);
			int knob = on ? x + 17 : x + 3;
			LuceatLook.round(graphics, knob, y + 12, knob + 10, y + 22, 0xFFFFFFFF);
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
			int width = 40;
			int x = controlRight - choice.count() * (width + 2) + 2;
			for (int i = 0; i < choice.count(); i++) {
				boolean selected = i == choice.index();
				int bx = x + i * (width + 2);
				boolean over = LuceatLook.inside(mouseX, mouseY, bx, y + 8, bx + width, y + 26);
				LuceatLook.round(graphics, bx, y + 8, bx + width, y + 26, selected ? LuceatLook.ACCENT : over ? LuceatLook.CARD_HOVER : LuceatLook.TRACK);
				String name = choice.name(i);
				graphics.text(this.font, name, bx + (width - this.font.width(name)) / 2, y + 13, selected ? 0xFFFFFFFF : LuceatLook.SOFT, false);
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

	private Presets.Preset drawPresets(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, "Presets", listLeft + 2, listTop() + 4, LuceatLook.ACCENT, false);
		Presets.Preset current = currentPreset();
		String state = current == null ? "Custom settings" : "Using " + current.name();
		graphics.text(this.font, state, listRight - this.font.width(state), listTop() + 4, current == null ? LuceatLook.WARN : LuceatLook.GOOD, false);
		int y = listTop() + STRIP_HEIGHT;
		Presets.Preset hoverPreset = null;
		for (Presets.Preset preset : Presets.all()) {
			boolean active = preset == current;
			boolean hover = LuceatLook.inside(mouseX, mouseY, listLeft, y, listRight, y + CARD_HEIGHT) && y + CARD_HEIGHT <= listBottom() + 4;
			if (hover) {
				hoverPreset = preset;
			}
			LuceatLook.roundOutlined(graphics, listLeft, y, listRight, y + CARD_HEIGHT, hover ? LuceatLook.CARD_HOVER : LuceatLook.CARD, active ? LuceatLook.GOOD : hover ? LuceatLook.ACCENT_DIM : LuceatLook.CARD);
			graphics.text(this.font, preset.name(), listLeft + 12, y + 9, LuceatLook.TEXT, false);
			String cost = "Cost: " + preset.cost();
			graphics.text(this.font, cost, listLeft + 12 + this.font.width(preset.name()) + 10, y + 9, costColour(preset.cost()), false);
			int bw = 56;
			int bx0 = listRight - 12 - bw;
			boolean btnOver = LuceatLook.inside(mouseX, mouseY, bx0, y + 8, bx0 + bw, y + 28);
			if (active) {
				LuceatLook.round(graphics, bx0, y + 8, bx0 + bw, y + 28, 0x3340E08A);
				graphics.text(this.font, "In use", bx0 + (bw - this.font.width("In use")) / 2, y + 14, LuceatLook.GOOD, false);
			} else {
				LuceatLook.round(graphics, bx0, y + 8, bx0 + bw, y + 28, btnOver ? LuceatLook.ACCENT : LuceatLook.ACCENT_DIM);
				graphics.text(this.font, "Use", bx0 + (bw - this.font.width("Use")) / 2, y + 14, 0xFFFFFFFF, false);
			}
			int lineY = y + 24;
			int shown = 0;
			for (FormattedCharSequence line : this.font.split(Component.literal(preset.blurb()).withStyle(Style.EMPTY.withColor(0xB4B4C4)), bx0 - listLeft - 24)) {
				if (shown++ >= 3) {
					break;
				}
				graphics.text(this.font, line, listLeft + 12, lineY, 0xFFB4B4C4, false);
				lineY += 10;
			}
			y += CARD_HEIGHT + 6;
		}
		if (current == null) {
			drawWrappedText(graphics, "Your settings do not match a preset. Picking one replaces them. You can press Undo to get them back.", listLeft + 2, y + 2, listRight - listLeft - 4, LuceatLook.MUTED);
		}
		return hoverPreset;
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, 48, 48, 48, 48, 48, 48);
		graphics.text(this.font, "Luceat", x + 60, y + 8, LuceatLook.ACCENT, false);
		graphics.text(this.font, "Made by bëwf", x + 60, y + 22, LuceatLook.SOFT, false);
		y += 62;
		String[] paragraphs = {
			"Luceat adds dynamic lights to held items, dropped items, other player, placed blocks, and more.",
			"You can customise light colours, brightness, and shadows to create a look you like. You can add lights to any block you want.",
			"You can hover a setting to see it in the live preview.",
			"Open this menu any time with /luceat or the K key.",
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

	private void drawSide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Presets.Preset hoveredPreset) {
		LuceatConfig live = LuceatConfig.get();
		if (PRESETS.equals(section) && !searching()) {
			if (hoveredPreset != null) {
				panel.setOverride(hoveredPreset.preview(live));
				LuceatConfig now = live.copy();
				panel.setSpec(Scenes.showcase("Now", hoveredPreset.name(), c -> c.copyFrom(now)));
			} else {
				panel.setOverride(null);
				panel.setSpec(new SceneSpec(Scenes.scene("showcase"), "Move over a preset to see it here next to your current settings.", null, null, null, null));
			}
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
	}

	private void undoLast() {
		if (undo.isEmpty()) {
			return;
		}
		String previous = undo.remove(undo.size() - 1);
		LuceatConfig.restore(previous);
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
		if (clickSidebar(mx, my)) {
			return true;
		}
		if (showSide && !BLOCKS.equals(section) && !ABOUT.equals(section) && panel.mouseClicked(mx, my)) {
			return true;
		}
		if (BLOCKS.equals(section) && !searching()) {
			return blocks.mouseClicked(mx, my) || super.mouseClicked(event, doubleClick);
		}
		if (PRESETS.equals(section) && !searching()) {
			return clickPresets(mx, my) || super.mouseClicked(event, doubleClick);
		}
		if (ABOUT.equals(section) && !searching()) {
			return super.mouseClicked(event, doubleClick);
		}
		if (stripReset(mx, my) || clickList(mx, my)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickSidebar(double mx, double my) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR_WIDTH;
		int y = top + HEADER_HEIGHT + 10;
		for (String name : sidebarNames()) {
			if (LuceatLook.inside(mx, my, x0, y, x1, y + 24)) {
				section = name;
				search.set("");
				scroll = 0.0F;
				if (blocks != null) {
					blocks.unfocus();
				}
				return true;
			}
			y += 28;
			if (name.equals(PRESETS) || name.equals(LuceatSettings.GENERAL)) {
				y += 4;
			}
		}
		return false;
	}

	private boolean clickPresets(double mx, double my) {
		int y = listTop() + STRIP_HEIGHT;
		for (Presets.Preset preset : Presets.all()) {
			if (LuceatLook.inside(mx, my, listLeft, y, listRight, y + CARD_HEIGHT)) {
				if (preset != currentPreset()) {
					pushUndo();
					preset.apply().accept(LuceatConfig.get());
					LightSpecs.clearCache();
					ShadowVolume.reset();
				}
				return true;
			}
			y += CARD_HEIGHT + 6;
		}
		return false;
	}

	private boolean clickList(double mx, double my) {
		int areaTop = listTop() + STRIP_HEIGHT;
		if (my < areaTop || my >= listBottom()) {
			return false;
		}
		List<Setting> rows = visible();
		int y = areaTop - Math.round(scroll);
		for (Setting setting : rows) {
			if (LuceatLook.inside(mx, my, listLeft, y, listRight, y + ROW_HEIGHT)) {
				clickRow(setting, y, mx, my);
				return true;
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		return false;
	}

	private void clickRow(Setting setting, int y, double mx, double my) {
		if (!isDefault(setting)) {
			int rx = listLeft + 10 + this.font.width(setting.label()) + 8;
			if (LuceatLook.inside(mx, my, rx - 2, y + 4, rx + this.font.width("reset") + 3, y + 17)) {
				pushUndo();
				setting.restore(defaults.get(setting.label()));
				applyChanged(setting);
				flushRebuild();
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
			int width = 40;
			int x = controlRight - choice.count() * (width + 2) + 2;
			int pillRight = x - 8;
			if (LuceatLook.inside(mx, my, pillRight - 34, y + 9, pillRight, y + 25)) {
				perf.start(setting);
				return;
			}
			for (int i = 0; i < choice.count(); i++) {
				int bx = x + i * (width + 2);
				if (LuceatLook.inside(mx, my, bx, y + 8, bx + width, y + 26)) {
					pushUndo();
					choice.choose(i);
					applyChanged(setting);
					flushRebuild();
					return;
				}
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
			if (undo.size() > 40) {
				undo.remove(0);
			}
		}
		dragging = null;
		dragBefore = null;
		flushRebuild();
		boolean blockUp = BLOCKS.equals(section) && blocks.mouseReleased();
		boolean panelUp = panel != null && panel.mouseReleased();
		return was || blockUp || panelUp || super.mouseReleased(event);
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
		if (!search.focused() && !ABOUT.equals(section) && event.codepoint() >= 32 && event.codepoint() != 127) {
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
