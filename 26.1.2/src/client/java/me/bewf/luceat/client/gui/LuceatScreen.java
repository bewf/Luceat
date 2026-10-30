package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import me.bewf.luceat.client.compat.RenderBackend;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
import me.bewf.luceat.client.gui.glass.Glass;
import me.bewf.luceat.client.gui.glass.Icons;
import me.bewf.luceat.client.gui.glass.Motion;
import me.bewf.luceat.client.gui.glass.Paint;
import me.bewf.luceat.client.gui.glass.Pane;
import me.bewf.luceat.client.gui.glass.Surface;
import me.bewf.luceat.client.gui.glass.Texts;
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
import net.minecraft.resources.Identifier;

public final class LuceatScreen extends Screen {
	private static final String ALL = "All settings";
	private static final String SPEED = "Speed test";
	private static final String PRESETS = "Presets";
	private static final String BLOCKS = "Blocks";
	private static final String ABOUT = "About";
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo.png");
	private static final Identifier LOGO_SMALL = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo_small.png");
	private static final Object NOTICE = new Object();
	private static final int NOTICE_HEIGHT = 58;
	private static final int ROW_HEIGHT = 44;
	private static final int SLIDER_HEIGHT = 50;
	private static final int HEAD_HEIGHT = 30;
	private static final int STRIP_HEIGHT = 50;
	private static final int FOOTER_HEIGHT = 56;
	private static final int HEADER_HEIGHT = 44;
	private static final int NAV_HEIGHT = 24;
	private static final int NAV_STEP = 26;
	private static final int DROPDOWN_ROWS = 8;
	private static final int DROPDOWN_ROW = 20;
	private static final Surface.Role BODY = Surface.Role.BODY;
	private static final Surface.Role TITLE = Surface.Role.TITLE;
	private static final Surface.Role HEADING = Surface.Role.HEADING;

	private record Hit(int x0, int y0, int x1, int y1, String key, Setting setting) {
	}

	private final Screen parent;
	private final List<Setting> settings = LuceatSettings.create();
	private Map<String, Object> baseline;
	private String baselineKey;
	private SpeedTestPage speed;
	private final List<String> redo = new ArrayList<>();
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

	private final Pane chrome = new Pane();
	private final Pane content = new Pane();
	private final Pane popup = new Pane();
	private final Map<String, Motion.Smooth> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private boolean busy;
	private boolean dirty = true;
	private boolean fullView;
	private String hoverKey = "";
	private int chromeKey;
	private int contentSig;
	private boolean popupOpen;
	private String copiedNote;
	private long copiedUntil;

	private int slabX0;
	private int slabY0;
	private int slabX1;
	private int slabY1;
	private int sideX0;
	private int sideX1;
	private int contentX0;
	private int contentX1;
	private int panelX0;
	private int panelX1;
	private int previewX0;
	private int previewX1;
	private int bodyTop;
	private int bodyBottom;
	private int listLeft;
	private int listRight;
	private boolean showSide;
	private boolean compact;

	public LuceatScreen(Screen parent) {
		super(Component.literal("Luceat"));
		this.parent = parent;
	}

	public LuceatScreen showSpeedTest() {
		this.section = SPEED;
		return this;
	}

	@Override
	protected void init() {
		int w = this.width;
		int h = this.height;
		int mx = Math.max(6, Math.round(w * 0.045F));
		int my = Math.max(6, Math.round(h * 0.05F));
		slabX0 = mx;
		slabX1 = w - mx;
		slabY0 = my;
		slabY1 = h - my;
		int slabW = slabX1 - slabX0;
		compact = slabW < 700;
		sideX0 = slabX0 + 12;
		sideX1 = sideX0 + (compact ? 104 : 124);
		bodyTop = slabY0 + HEADER_HEIGHT + 8;
		bodyBottom = slabY1 - 12;
		contentX0 = sideX1 + 8;
		contentX1 = slabX1 - 12;
		int cw = contentX1 - contentX0;
		showSide = slabW >= 640;
		panelX0 = contentX0;
		panelX1 = showSide ? contentX0 + Math.max(250, Math.round(cw * 0.42F)) : contentX1;
		previewX0 = panelX1 + 8;
		previewX1 = contentX1;
		listLeft = panelX0 + 16;
		listRight = panelX1 - 16;
		if (panel == null) {
			panel = new PreviewPanel(this.font);
			blocks = new BlocksTab(this.font);
		}
		dirty = true;
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
		chrome.release();
		content.release();
		popup.release();
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
	}

	private boolean searching() {
		return search.focused() || !search.text().isEmpty();
	}

	private boolean specialPage() {
		return PRESETS.equals(section) || BLOCKS.equals(section) || ABOUT.equals(section) || SPEED.equals(section);
	}

	private boolean settingsPage() {
		return searching() || !specialPage();
	}

	private boolean panelPage() {
		return settingsPage() || PRESETS.equals(section);
	}

	private boolean previewPage() {
		return showSide && panelPage();
	}

	private boolean panelShown() {
		return panelPage() && !(fullView && showSide);
	}

	private int previewLeft() {
		return fullView ? contentX0 : previewX0;
	}

	private int guiScale() {
		try {
			return Math.max(1, (int) Math.round(this.minecraft.getWindow().getGuiScale()));
		} catch (RuntimeException error) {
			return 2;
		}
	}

	private float anim(String key, float target, float halfLife) {
		Motion.Smooth smooth = anims.computeIfAbsent(key, k -> new Motion.Smooth(target));
		float value = smooth.step(target, halfLife);
		if (value != target) {
			busy = true;
		}
		return value;
	}

	private void hit(int x0, int y0, int x1, int y1, String key, Setting setting) {
		hits.add(new Hit(x0, y0, x1, y1, key, setting));
	}

	private Hit hitAt(double mx, double my) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x0 && mx < h.x1 && my >= h.y0 && my < h.y1) {
				return h;
			}
		}
		return null;
	}

	private Setting settingAt(double mx, double my) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.setting != null && mx >= h.x0 && mx < h.x1 && my >= h.y0 && my < h.y1) {
				return h.setting;
			}
		}
		return null;
	}

	private float hoverOf(String key) {
		return anim("h:" + key, hoverKey.equals(key) ? 1.0F : 0.0F, 0.05F);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		Motion.tick(System.nanoTime());
		int gs = guiScale();
		boolean panelNow = panelShown();
		int ck = Objects.hash(this.width, this.height, gs, panelNow, showSide);
		if (!chrome.matches(0, 0, this.width, this.height, gs) || ck != chromeKey) {
			chromeKey = ck;
			chrome.begin(0, 0, this.width, this.height, gs);
			buildChrome(chrome, panelNow);
			chrome.finish();
			dirty = true;
		}
		Hit over = hitAt(mouseX, mouseY);
		String nowHover = over == null ? "" : over.key;
		if (!nowHover.equals(hoverKey)) {
			hoverKey = nowHover;
			dirty = true;
		}
		hovered = search.focused() ? null : settingAt(mouseX, mouseY);
		if (previewPage()) {
			panel.setOverride(null);
			if (PRESETS.equals(section) && !searching()) {
				panel.setSpec(new SceneSpec(Scenes.scene("showcase"), "This is how your current settings look.", null, null, null, null));
			} else if (hovered != null) {
				panel.setSpec(Scenes.forSetting(hovered.label()));
			}
			panel.setInteractive(dragging != null);
			panel.place(previewLeft(), bodyTop, previewX1 - previewLeft(), bodyBottom - bodyTop);
		}
		int sig = Objects.hash(LuceatConfig.get().asJson().hashCode(), section, search.text(), search.focused(), Math.round(scroll), dragging != null, perf.running(), perf.running() ? perf.progress() : 0, undo.size(), redo.size(),
			flash != null && System.currentTimeMillis() < flashUntil, previewPage() ? panel.overlayHash() : 0, dropdownScroll, copiedNote != null && System.currentTimeMillis() < copiedUntil, LuceatConfig.get().enabled, perf.note(), fullView);
		if (sig != contentSig) {
			contentSig = sig;
			dirty = true;
		}
		if (dirty || busy || !content.matches(slabX0, slabY0, slabX1 - slabX0, slabY1 - slabY0, gs)) {
			busy = false;
			dirty = false;
			rebuildContent(mouseX, mouseY, gs);
		}
		chrome.draw(graphics);
		if (previewPage()) {
			panel.drawImage(graphics, perf.running());
		}
		if (popupOpen) {
			content.hideText(popupX0(), popupY0(), popupX1(), popupY1());
		} else {
			content.showAllText();
		}
		content.draw(graphics);
		drawLogo(graphics);
		search.drawGlass(graphics, this.font, searchX0() + 14, slabY0 + 11, searchX1() - 6, slabY0 + 33, "Search settings");
		if (BLOCKS.equals(section) && !searching()) {
			blocks.drawList(graphics, panelX0, bodyTop, panelX1Wide(), bodyBottom, mouseX, mouseY);
			if (showSide) {
				blocks.drawEditor(graphics, panelX1Wide() + 8, bodyTop, contentX1, bodyBottom, mouseX, mouseY);
			}
		} else if (SPEED.equals(section) && !searching()) {
			speed().render(graphics, panelX0, bodyTop, contentX1 - panelX0, bodyBottom - bodyTop, mouseX, mouseY, partial);
		} else if (ABOUT.equals(section) && !searching()) {
			drawAboutLogo(graphics);
		}
		if (popupOpen) {
			popup.draw(graphics);
		}
		if (blocks != null && blocks.consumeDirty()) {
			ShadowVolume.reset();
		}
		perf.frame();
	}

	private int panelX1Wide() {
		return showSide ? contentX1 - Math.round((contentX1 - panelX0) * 0.36F) - 8 : contentX1;
	}

	private void drawLogo(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO_SMALL, slabX0 + 16, slabY0 + 10, 0.0F, 0.0F, 24, 24, 64, 64, 64, 64);
	}

	private void buildChrome(Surface s, boolean panelNow) {
		Glass.backdrop(s, this.width, this.height);
		Glass.slab(s, slabX0, slabY0, slabX1, slabY1, 16);
		Glass.divider(s, slabX0 + 18, slabX1 - 18, slabY0 + HEADER_HEIGHT);
		if (panelNow) {
			Paint.round(s, panelX0, bodyTop, panelX1, bodyBottom, 12, Paint.argb(204, 11, 7, 22));
			Paint.outline(s, panelX0, bodyTop, panelX1, bodyBottom, 12, (x, y) -> Paint.argb(Math.round(30 * (1.0F - (y - bodyTop) / (float) (bodyBottom - bodyTop))), 205, 187, 255));
		}
	}

	private int searchX1() {
		return slabX1 - 16 - 24 - 8 - 54 - 8 - (compact ? 84 : 112) - 8;
	}

	private int searchX0() {
		return searchX1() - (compact ? 120 : 176);
	}

	private int popupX0() {
		return searchX1() - 270;
	}

	private int popupX1() {
		return searchX1();
	}

	private int popupY0() {
		return slabY0 + 38;
	}

	private int popupRows() {
		return Math.min(DROPDOWN_ROWS, Math.max(1, matches().size()));
	}

	private int popupY1() {
		return popupY0() + popupRows() * DROPDOWN_ROW + 12;
	}

	private String version() {
		return FabricLoader.getInstance().getModContainer("luceat").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	private String presetLabel() {
		Presets.Preset current = currentPreset();
		Presets.Preset named = Presets.byName(LuceatConfig.get().preset);
		if (current != null) {
			return current.name();
		}
		return named != null ? named.name() + "*" : "Preset";
	}

	private void rebuildContent(int mouseX, int mouseY, int gs) {
		hits.clear();
		content.begin(slabX0, slabY0, slabX1 - slabX0, slabY1 - slabY0, gs);
		Surface s = content;
		drawHeader(s);
		drawSidebar(s);
		if (fullView && showSide) {
			// preview only
		} else if (settingsPage()) {
			drawSettings(s);
		} else if (PRESETS.equals(section)) {
			drawPresets(s);
		} else if (ABOUT.equals(section)) {
			drawAbout(s);
		}
		if (previewPage()) {
			boolean timeHover = panel.overTime(mouseX, mouseY);
			panel.drawOverlay(s, perf.running() ? perf.note() : null, timeHover);
			String label = fullView ? "Back" : "Full view";
			int cw = s.width(BODY, label) + 34;
			int cx1 = previewX1 - 12;
			int cx0 = cx1 - cw;
			float over = hoverOf("pv:full");
			Glass.chip(s, cx0, bodyTop + 10, cx1, bodyTop + 28, over);
			Icons.draw(s, cx0 + 9, bodyTop + 14, Icons.EXPAND, Paint.mix(Glass.SOFT, Glass.TEXT, over));
			s.text(BODY, label, cx0 + 24, bodyTop + 15, Paint.mix(Glass.SOFT, Glass.TEXT, over));
			hit(previewLeft(), bodyTop, previewX1, bodyBottom, "preview", null);
			hit(cx0, bodyTop + 10, cx1, bodyTop + 28, "pv:full", null);
		}
		content.finish();
		popupOpen = search.focused();
		if (popupOpen) {
			popup.begin(popupX0(), popupY0(), popupX1() - popupX0(), popupY1() - popupY0(), gs);
			drawDropdown(popup);
			popup.finish();
		}
	}

	private void drawHeader(Surface s) {
		int x = slabX0 + 48;
		int ty = slabY0 + 11;
		String word = "LUCEAT";
		for (int i = 0; i < word.length(); i++) {
			String c = word.substring(i, i + 1);
			s.text(TITLE, c, x, ty, Glass.TEXT);
			x += s.width(TITLE, c) + 3;
		}
		x += 6;
		if (!compact) {
			s.text(BODY, version(), x, ty, Glass.FAINT);
			s.text(BODY, "Dynamic Lighting for Minecraft", slabX0 + 48, slabY0 + 24, Glass.FAINT);
			x = Math.max(x + s.width(BODY, version()), slabX0 + 48 + s.width(BODY, "Dynamic Lighting for Minecraft")) + 18;
		}
		boolean on = LuceatConfig.get().enabled;
		int tx = x + 2;
		float onAnim = anim("tg:master", on ? 1.0F : 0.0F, 0.05F);
		float powerHover = hoverOf("hd:power");
		Glass.toggle(s, tx, slabY0 + 14, onAnim, powerHover);
		s.text(BODY, on ? "On" : "Off", tx + 38, slabY0 + 18, on ? Glass.GOOD : Glass.MUTED);
		hit(tx - 4, slabY0 + 8, tx + 38 + s.width(BODY, "Off") + 6, slabY0 + 36, "hd:power", null);

		int closeX0 = slabX1 - 16 - 24;
		float closeHover = hoverOf("hd:close");
		Glass.button(s, closeX0, slabY0 + 10, closeX0 + 24, slabY0 + 34, false, closeHover, 0.0F);
		Icons.draw(s, closeX0 + 8, slabY0 + 18, Icons.CLOSE, Paint.mix(Glass.SOFT, Glass.TEXT, closeHover));
		hit(closeX0, slabY0 + 10, closeX0 + 24, slabY0 + 34, "hd:close", null);

		int undoX1 = closeX0 - 8;
		int undoX0 = undoX1 - 54;
		boolean canUndo = !undo.isEmpty();
		float undoHover = canUndo ? hoverOf("hd:undo") : 0.0F;
		Glass.button(s, undoX0, slabY0 + 10, undoX1, slabY0 + 34, false, undoHover, 0.0F);
		Glass.centred(s, BODY, "Undo", (undoX0 + undoX1) / 2, slabY0 + 18, canUndo ? Glass.TEXT : Glass.FAINT);
		hit(undoX0, slabY0 + 10, undoX1, slabY0 + 34, "hd:undo", null);

		int chipW = compact ? 84 : 112;
		int chipX1 = undoX0 - 8;
		int chipX0 = chipX1 - chipW;
		float chipHover = hoverOf("hd:preset");
		Glass.field(s, chipX0, slabY0 + 10, chipX1, slabY0 + 34, 0.0F);
		if (chipHover > 0.01F) {
			Paint.outline(s, chipX0, slabY0 + 10, chipX1, slabY0 + 34, 8, Paint.argb(Math.round(90 * chipHover), 205, 187, 255));
		}
		s.text(BODY, Glass.fit(s, BODY, presetLabel(), chipW - 30), chipX0 + 10, slabY0 + 18, Glass.TEXT);
		Icons.draw(s, chipX1 - 16, slabY0 + 20, Icons.CHEVRON, Glass.MUTED);
		hit(chipX0, slabY0 + 10, chipX1, slabY0 + 34, "hd:preset", null);

		float focus = anim("focus:search", search.focused() ? 1.0F : 0.0F, 0.05F);
		Glass.field(s, searchX0(), slabY0 + 10, searchX1(), slabY0 + 34, focus);
		Icons.draw(s, searchX0() + 8, slabY0 + 18, Icons.SEARCH, Paint.mix(Glass.MUTED, Glass.PALE, focus));
		hit(searchX0(), slabY0 + 10, searchX1(), slabY0 + 34, "hd:search", null);
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

	private static String[] iconFor(String name) {
		return switch (name) {
			case ALL -> Icons.EXPAND;
			case PRESETS -> Icons.STAR;
			case LuceatSettings.LIGHTING -> Icons.SUN;
			case LuceatSettings.SHADOWS -> Icons.SHADOW;
			case LuceatSettings.PLACED -> Icons.BULB;
			case LuceatSettings.SOURCES -> Icons.FLAME;
			case LuceatSettings.PERFORMANCE -> Icons.GAUGE;
			case LuceatSettings.GENERAL -> Icons.GEAR;
			case LuceatSettings.ADVANCED -> Icons.SLIDERS;
			case BLOCKS -> Icons.CUBE;
			case SPEED -> Icons.BOLT;
			case ABOUT -> Icons.INFO;
			default -> Icons.CUBE;
		};
	}

	private void drawSidebar(Surface s) {
		int x0 = sideX0;
		int x1 = sideX1;
		int y = bodyTop;
		for (String name : sidebarNames()) {
			if (name.equals("-")) {
				Glass.divider(s, x0 + 6, x1 - 6, y + 4);
				y += 10;
				continue;
			}
			if (name.startsWith("~")) {
				s.text(BODY, name.substring(1), x0 + 10, y + 4, Glass.FAINT);
				y += 18;
				continue;
			}
			boolean selected = !searching() && name.equals(section);
			float active = anim("nav:" + name, selected ? 1.0F : 0.0F, 0.05F);
			float hover = hoverOf("nav:" + name);
			Glass.navItem(s, x0, y, x1, y + NAV_HEIGHT, active, hover);
			int colour = Paint.mix(Paint.mix(Glass.MUTED, Glass.SOFT, hover), Glass.TEXT, active);
			Icons.draw(s, x0 + 9, y + (NAV_HEIGHT - 9) / 2, iconFor(name), Paint.mix(Glass.MUTED, Glass.PALE, Math.max(active, hover * 0.7F)));
			s.text(BODY, name, x0 + 26, y + 8, colour);
			hit(x0, y, x1, y + NAV_HEIGHT, "nav:" + name, null);
			y += NAV_STEP;
		}
		long bytes = ShadowVolume.bytes() + PatternAtlas.bytes() + ShapeMasks.bytes();
		int footer = bodyBottom - 22;
		if (footer > y + 6) {
			s.text(BODY, "Video memory", x0 + 8, footer, Glass.FAINT);
			s.text(BODY, String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0), x0 + 8, footer + 11, bytes == 0 ? Glass.MUTED : Glass.SOFT);
		}
	}

	private boolean clickSidebar(double mx, double my) {
		int x0 = sideX0;
		int x1 = sideX1;
		int y = bodyTop;
		for (String name : sidebarNames()) {
			if (name.equals("-")) {
				y += 10;
				continue;
			}
			if (name.startsWith("~")) {
				y += 18;
				continue;
			}
			if (LuceatLook.inside(mx, my, x0, y, x1, y + NAV_HEIGHT)) {
				search.set("");
				fullView = false;
				setSection(name);
				return true;
			}
			y += NAV_STEP;
		}
		return false;
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

	private void drawDropdown(Surface s) {
		List<Setting> found = matches();
		int x0 = popupX0();
		int x1 = popupX1();
		int y0 = popupY0();
		int rows = popupRows();
		int y1 = popupY1();
		dropdownScroll = Math.max(0, Math.min(dropdownScroll, Math.max(0, found.size() - DROPDOWN_ROWS)));
		Paint.vertical(s, x0, y0, x1, y1, 10, Paint.argb(250, 28, 20, 54), Paint.argb(250, 16, 11, 32));
		Paint.outline(s, x0, y0, x1, y1, 10, Paint.argb(110, 205, 187, 255));
		if (found.isEmpty()) {
			s.text(BODY, "Nothing matches.", x0 + 12, y0 + 10, Glass.MUTED);
			return;
		}
		for (int i = 0; i < rows; i++) {
			Setting setting = found.get(dropdownScroll + i);
			int ry = y0 + 6 + i * DROPDOWN_ROW;
			String key = "dd:" + (dropdownScroll + i);
			float over = hoverOf(key);
			if (over > 0.01F) {
				Paint.round(s, x0 + 4, ry, x1 - 4, ry + DROPDOWN_ROW, 7, Paint.argb(Math.round(60 * over), 123, 99, 255));
			}
			s.text(BODY, Glass.fit(s, BODY, setting.label(), x1 - x0 - 24 - s.width(BODY, setting.section())), x0 + 12, ry + 6, Paint.mix(Glass.SOFT, Glass.TEXT, over));
			Glass.right(s, BODY, setting.section(), x1 - 12, ry + 6, Glass.MUTED);
			hit(x0 + 4, ry, x1 - 4, ry + DROPDOWN_ROW, key, null);
		}
		if (found.size() > DROPDOWN_ROWS) {
			String more = (found.size() - DROPDOWN_ROWS - dropdownScroll) + " more, scroll";
			s.text(BODY, more, x0 + 12, y1 - 12, Glass.FAINT);
		}
	}

	private boolean clickDropdown(double mx, double my) {
		List<Setting> found = matches();
		int x0 = popupX0();
		int x1 = popupX1();
		int y0 = popupY0();
		int rows = popupRows();
		if (!LuceatLook.inside(mx, my, x0, y0, x1, popupY1())) {
			return false;
		}
		for (int i = 0; i < rows && dropdownScroll + i < found.size(); i++) {
			int ry = y0 + 6 + i * DROPDOWN_ROW;
			if (LuceatLook.inside(mx, my, x0 + 4, ry, x1 - 4, ry + DROPDOWN_ROW)) {
				jumpTo(found.get(dropdownScroll + i));
				return true;
			}
		}
		return true;
	}

	private void jumpTo(Setting setting) {
		search.focus(false);
		search.set("");
		fullView = false;
		setSection(setting.section());
		List<Object> rows = items();
		int offset = 0;
		for (Object item : rows) {
			if (item == setting) {
				break;
			}
			offset += itemHeight(item);
		}
		scroll = Math.max(0.0F, offset - 40);
		flash = setting;
		flashUntil = System.currentTimeMillis() + 1800L;
		dirty = true;
	}

	private void setSection(String name) {
		if (SPEED.equals(section) && !SPEED.equals(name) && speed != null) {
			speed.close();
		}
		section = name;
		scroll = 0.0F;
		dirty = true;
		if (blocks != null) {
			blocks.unfocus();
		}
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
				if (name.equals(LuceatSettings.ADVANCED)) {
					continue;
				}
				out.add(name);
				for (Setting setting : settings) {
					if (setting.section().equals(name) && setting != master) {
						out.add(setting);
					}
				}
			}
			return out;
		}
		if (LuceatSettings.ADVANCED.equals(section)) {
			out.add(NOTICE);
		}
		out.addAll(sectionSettings());
		return out;
	}

	private int itemHeight(Object item) {
		if (item instanceof String) {
			return HEAD_HEIGHT;
		}
		if (item == NOTICE) {
			return NOTICE_HEIGHT;
		}
		return item instanceof SliderSetting ? SLIDER_HEIGHT : ROW_HEIGHT;
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
		dirty = true;
	}

	private int totalHeight(List<Object> rows) {
		int total = 0;
		for (Object item : rows) {
			total += itemHeight(item);
		}
		return total;
	}

	private int areaTop() {
		return bodyTop + STRIP_HEIGHT;
	}

	private int areaBottom() {
		return bodyBottom - (footerShown() ? FOOTER_HEIGHT : 8);
	}

	private boolean footerShown() {
		return bodyBottom - bodyTop > 300;
	}

	private int maxScroll(List<Object> rows) {
		return Math.max(0, totalHeight(rows) - (areaBottom() - areaTop()));
	}

	private static String sectionNote(String name, boolean searching, int count) {
		if (searching) {
			return count + (count == 1 ? " result" : " results");
		}
		return switch (name) {
			case ALL -> "Every setting in one list.";
			case LuceatSettings.LIGHTING -> "How light looks and behaves.";
			case LuceatSettings.SHADOWS -> "How light gets blocked.";
			case LuceatSettings.PLACED -> "Lights from blocks you place.";
			case LuceatSettings.SOURCES -> "What gives off light.";
			case LuceatSettings.PERFORMANCE -> "Trade looks for speed.";
			case LuceatSettings.GENERAL -> "Basics and extras.";
			case LuceatSettings.ADVANCED -> "For tuning. Defaults are fine.";
			default -> "";
		};
	}

	private void drawResetIcon(Surface s, int x, int y, boolean enabled, float over) {
		int colour = !enabled ? Glass.FAINT : Paint.mix(Glass.WARN, Glass.PALE, over);
		Icons.draw(s, x, y, Icons.RESET, colour);
	}

	private void drawStrip(Surface s, String title, List<Setting> rows) {
		s.text(HEADING, title, listLeft, bodyTop + 11, Glass.TEXT);
		int changed = changedCount(rows);
		boolean enabled = changed > 0;
		String note = sectionNote(section, searching(), rows.size());
		String state = changed == 0 ? "All at preset values" : changed + " changed";
		s.text(BODY, note, listLeft, bodyTop + 31, Glass.MUTED);
		int bx1 = listRight;
		String key = "strip:reset";
		float over = enabled ? hoverOf(key) : 0.0F;
		int bx0 = bx1 - 22;
		Glass.button(s, bx0, bodyTop + 10, bx1, bodyTop + 30, false, over, 0.0F);
		drawResetIcon(s, bx0 + (22 - Icons.width(Icons.RESET)) / 2, bodyTop + 10 + (20 - Icons.height(Icons.RESET)) / 2, enabled, over);
		Glass.right(s, BODY, state, bx0 - 8, bodyTop + 16, changed == 0 ? Glass.FAINT : Glass.WARN);
		hit(bx0, bodyTop + 10, bx1, bodyTop + 30, key, null);
	}

	private boolean stripReset(double mx, double my) {
		if (!LuceatLook.inside(mx, my, listRight - 22, bodyTop + 10, listRight, bodyTop + 30)) {
			return false;
		}
		resetSettings(settingsOf(items()));
		return true;
	}

	private void drawSectionHead(Surface s, String name, int y) {
		s.text(TITLE, name, listLeft, y + 12, Glass.LAVENDER);
		int lineX = listLeft + s.width(TITLE, name) + 10;
		Paint.horizontal(s, lineX, y + 17, listRight - 24, y + 18, Paint.argb(40, 205, 187, 255), Paint.argb(0, 205, 187, 255), 6);
		List<Setting> group = settingsIn(name);
		boolean enabled = changedCount(group) > 0;
		int ix = listRight - 12;
		String key = "head:" + name;
		float over = enabled ? hoverOf(key) : 0.0F;
		drawResetIcon(s, ix, y + 12, enabled, over);
		int top = Math.max(y + 8, areaTop());
		int bottom = Math.min(y + 26, areaBottom());
		if (bottom > top) {
			hit(ix - 4, top, ix + Icons.width(Icons.RESET) + 4, bottom, key, null);
		}
	}

	private void drawSettings(Surface s) {
		List<Object> rows = items();
		String title = searching() ? "Search results" : section;
		drawStrip(s, title, settingsOf(rows));
		int top = areaTop();
		int bottom = areaBottom();
		scroll = Math.max(0.0F, Math.min(scroll, maxScroll(rows)));
		s.clip(panelX0, top, panelX1, bottom);
		int y = top - Math.round(scroll);
		boolean first = true;
		for (Object item : rows) {
			int height = itemHeight(item);
			boolean inView = y + height > top && y < bottom;
			if (inView) {
				if (item instanceof String name) {
					drawSectionHead(s, name, y);
				} else if (item == NOTICE) {
					drawNotice(s, y);
				} else if (item instanceof Setting setting) {
					drawRow(s, setting, y, height, top, bottom, first);
				}
			}
			first = false;
			y += height;
		}
		s.unclip();
		if (rows.isEmpty()) {
			s.text(BODY, "Nothing matches that.", listLeft, top + 8, Glass.MUTED);
		}
		int max = maxScroll(rows);
		if (max > 0) {
			int trackHeight = bottom - top;
			int thumb = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
			int thumbTop = top + Math.round((trackHeight - thumb) * (scroll / max));
			Paint.round(s, panelX1 - 7, thumbTop, panelX1 - 5, thumbTop + thumb, 1, Paint.argb(90, 205, 187, 255));
		}
		if (footerShown()) {
			drawFooter(s);
		}
	}

	private void drawFooter(Surface s) {
		int y0 = bodyBottom - FOOTER_HEIGHT + 4;
		Glass.divider(s, panelX0 + 10, panelX1 - 10, y0 - 2);
		Setting shown = hovered;
		if (shown == null) {
			s.text(BODY, "Hover a setting to see what it does.", listLeft, y0 + 10, Glass.FAINT);
			return;
		}
		s.text(TITLE, shown.label(), listLeft, y0 + 4, Glass.LAVENDER);
		String costText = "Cost: " + shown.cost().label();
		Glass.right(s, BODY, costText, listRight, y0 + 4, shown.cost().colour());
		List<String> lines = Glass.wrap(s, BODY, shown.help(), listRight - listLeft, 2);
		int ty = y0 + 18;
		for (String line : lines) {
			s.text(BODY, line, listLeft, ty, Glass.SOFT);
			ty += 11;
		}
		PerfTest.Result measured = perf.result(shown);
		if (measured != null) {
			s.text(BODY, "Last test: " + measured.text(), listLeft, ty, measured.cost().colour());
		}
	}

	private void drawNotice(Surface s, int y) {
		int x0 = listLeft - 4;
		int x1 = listRight + 4;
		Paint.round(s, x0, y + 4, x1, y + NOTICE_HEIGHT - 4, 10, Paint.argb(40, 255, 200, 87));
		Paint.outline(s, x0, y + 4, x1, y + NOTICE_HEIGHT - 4, 10, Paint.argb(70, 255, 200, 87));
		s.text(TITLE, "Heads up", x0 + 10, y + 10, Glass.WARN);
		List<String> lines = Glass.wrap(s, BODY, "This page is the complicated one. The defaults are already good, so most people never need to touch it. If something goes odd, hit the reset arrow at the top.", x1 - x0 - 20, 3);
		int ty = y + 23;
		for (String line : lines) {
			s.text(BODY, line, x0 + 10, ty, Glass.SOFT);
			ty += 10;
		}
	}

	private int toggleX() {
		return listRight - 30;
	}

	private int choiceX() {
		return listRight - 96;
	}

	private int trackLeft() {
		return listLeft + 6;
	}

	private int trackRight() {
		return listRight - 6;
	}

	private int testRight(Setting setting) {
		return (setting instanceof ToggleSetting ? toggleX() : choiceX()) - 8;
	}

	private void drawRow(Surface s, Setting setting, int y, int height, int areaTop, int areaBottom, boolean first) {
		String id = setting.label();
		String rowKey = "row:" + id;
		float hover = hoverOf(rowKey);
		float hoverAny = Math.max(hover, anim("h:ctl:" + id, hoverKey.endsWith(":" + id) ? 1.0F : 0.0F, 0.05F));
		boolean flashing = setting == flash && System.currentTimeMillis() < flashUntil;
		int vy0 = Math.max(y, areaTop);
		int vy1 = Math.min(y + height, areaBottom);
		if (hoverAny > 0.01F || flashing) {
			int a = Math.round(18 * hoverAny);
			if (flashing) {
				a = Math.max(a, 60);
			}
			Paint.round(s, listLeft - 8, y + 1, listRight + 8, y + height - 1, 9, Paint.argb(a, flashing ? 123 : 205, flashing ? 99 : 187, 255));
		}
		if (!first && hoverAny < 0.5F) {
			Glass.divider(s, listLeft - 2, listRight + 2, y);
		}
		boolean slider = setting instanceof SliderSetting;
		int labelY = slider ? y + 9 : y + 8;
		s.text(BODY, setting.label(), listLeft, labelY, Paint.mix(Glass.TEXT, 0xFFFFFFFF, hoverAny * 0.4F));
		int cursor = listLeft + s.width(BODY, setting.label()) + 8;
		Paint.round(s, cursor, labelY + 3, cursor + 4, labelY + 7, 2, setting.cost().colour());
		cursor += 12;
		if (!isDefault(setting)) {
			String rkey = "rst:" + id;
			float over = hoverOf(rkey);
			drawResetIcon(s, cursor, labelY + 1, true, over);
			clampedHit(cursor - 3, y + 4, cursor + Icons.width(Icons.RESET) + 3, y + 18, rkey, setting, areaTop, areaBottom);
		}
		if (vy1 > vy0) {
			hit(listLeft - 8, vy0, listRight + 8, vy1, rowKey, setting);
		}
		if (setting instanceof ToggleSetting toggle) {
			int x = toggleX();
			int cy = y + (height - 16) / 2;
			float on = anim("tg:" + id, toggle.value() ? 1.0F : 0.0F, 0.05F);
			float over = hoverOf("tg:" + id);
			Glass.toggle(s, x, cy, on, Math.max(over, hover));
			clampedHit(x - 2, cy - 4, x + 32, cy + 20, "tg:" + id, setting, areaTop, areaBottom);
			s.text(BODY, Glass.fit(s, BODY, setting.help(), x - 20 - listLeft - (setting.testable() ? 46 : 0)), listLeft, y + 23, Glass.MUTED);
			drawTestPill(s, setting, testRight(setting), y, height, areaTop, areaBottom);
		} else if (slider) {
			SliderSetting sl = (SliderSetting) setting;
			float value = anim("sl:" + id, sl.fraction(), dragging == setting ? 0.012F : 0.045F);
			Glass.right(s, BODY, sl.valueText(), listRight, labelY, Paint.mix(Glass.SOFT, Glass.TEXT, Math.max(hoverAny, dragging == setting ? 1.0F : 0.0F)));
			int cy = y + 34;
			Glass.slider(s, trackLeft(), cy, trackRight(), value, hoverAny, dragging == setting ? 1.0F : 0.0F);
			clampedHit(listLeft - 8, cy - 10, listRight + 8, cy + 10, "sl:" + id, setting, areaTop, areaBottom);
		} else if (setting instanceof ChoiceSetting<?> choice) {
			int x = choiceX();
			int cy = y + (height - 22) / 2;
			float over = hoverOf("ch:" + id);
			Glass.field(s, x, cy, x + 96, cy + 22, 0.0F);
			if (over > 0.01F) {
				Paint.outline(s, x, cy, x + 96, cy + 22, 8, Paint.argb(Math.round(110 * over), 205, 187, 255));
			}
			s.text(BODY, Glass.fit(s, BODY, choice.name(choice.index()), 62), x + 10, cy + 7, Glass.TEXT);
			Icons.draw(s, x + 96 - 16, cy + 9, Icons.CHEVRON, Paint.mix(Glass.MUTED, Glass.PALE, over));
			clampedHit(x, cy, x + 96, cy + 22, "ch:" + id, setting, areaTop, areaBottom);
			String note = choice.note(choice.index());
			String line = note.isEmpty() ? setting.help() : note;
			s.text(BODY, Glass.fit(s, BODY, line, x - 20 - listLeft - (setting.testable() ? 46 : 0)), listLeft, y + 23, Glass.MUTED);
			drawTestPill(s, setting, testRight(setting), y, height, areaTop, areaBottom);
		}
	}

	private void clampedHit(int x0, int y0, int x1, int y1, String key, Setting setting, int areaTop, int areaBottom) {
		int top = Math.max(y0, areaTop);
		int bottom = Math.min(y1, areaBottom);
		if (bottom > top) {
			hit(x0, top, x1, bottom, key, setting);
		}
	}

	private void drawTestPill(Surface s, Setting setting, int rightEdge, int y, int height, int areaTop, int areaBottom) {
		if (!setting.testable()) {
			return;
		}
		boolean running = perf.running() && perf.target() == setting;
		int width = 38;
		int x = rightEdge - width;
		int cy = y + (height - 16) / 2;
		String key = "test:" + setting.label();
		float over = hoverOf(key);
		Paint.round(s, x, cy, x + width, cy + 16, 8, Paint.argb(Math.round(70 + 50 * over), 40, 30, 80));
		Paint.outline(s, x, cy, x + width, cy + 16, 8, running ? Paint.argb(200, 178, 140, 255) : Paint.argb(Math.round(50 + 80 * over), 205, 187, 255));
		String label = running ? perf.progress() + "%" : "Test";
		Glass.centred(s, BODY, label, x + width / 2, cy + 4, running ? Glass.LAVENDER : Glass.SOFT);
		clampedHit(x, cy, x + width, cy + 16, key, setting, areaTop, areaBottom);
	}

	private Presets.Preset currentPreset() {
		String json = LuceatConfig.get().asJson();
		if (!json.equals(cachedJson)) {
			cachedJson = json;
			cachedPreset = Presets.current(LuceatConfig.get());
		}
		return cachedPreset;
	}

	private void drawPresets(Surface s) {
		s.text(HEADING, "Presets", listLeft, bodyTop + 11, Glass.TEXT);
		s.text(BODY, "Pick a starting point.", listLeft, bodyTop + 31, Glass.MUTED);
		Presets.Preset current = currentPreset();
		Presets.Preset named = Presets.byName(LuceatConfig.get().preset);
		int y = areaTop() + 2;
		int x0 = listLeft - 4;
		int x1 = listRight + 4;
		Paint.round(s, x0, y, x1, y + 108, 12, Paint.argb(90, 40, 30, 80));
		Paint.outline(s, x0, y, x1, y + 108, 12, current != null ? Paint.argb(110, 111, 227, 160) : Paint.argb(50, 205, 187, 255));
		String title = current != null ? current.name() : named != null ? named.name() + " (changed)" : "No preset chosen";
		s.text(TITLE, title, x0 + 14, y + 12, Glass.TEXT);
		Presets.Preset shown = current != null ? current : named;
		int lineY = y + 30;
		if (shown != null) {
			Glass.right(s, BODY, "Cost: " + shown.cost(), x1 - 14, y + 12, costColour(shown.cost()));
			for (String line : Glass.wrap(s, Surface.Role.QUOTE, shown.blurb(), x1 - x0 - 28, 4)) {
				s.text(Surface.Role.QUOTE, line, x0 + 14, lineY, Glass.SOFT);
				lineY += 12;
			}
		} else {
			for (String line : Glass.wrap(s, BODY, "Pick a preset to start from. You can change anything after.", x1 - x0 - 28, 3)) {
				s.text(BODY, line, x0 + 14, lineY, Glass.SOFT);
				lineY += 11;
			}
		}
		int by0 = y + 120;
		int bw = s.width(BODY, "Choose preset") + 28;
		float over = hoverOf("preset:choose");
		Glass.button(s, x0, by0, x0 + bw, by0 + 24, true, over, 0.0F);
		Glass.centred(s, BODY, "Choose preset", x0 + bw / 2, by0 + 7, 0xFFFFFFFF);
		hit(x0, by0, x0 + bw, by0 + 24, "preset:choose", null);
		int ny = by0 + 36;
		for (String line : Glass.wrap(s, BODY, "The preset you pick is what the reset buttons go back to.", x1 - x0, 2)) {
			s.text(BODY, line, x0, ny, Glass.FAINT);
			ny += 11;
		}
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

	private void drawAbout(Surface s) {
		int x = panelX0 + 4;
		int y = bodyTop + 6;
		s.text(HEADING, "Luceat", x + 100, y + 12, Glass.TEXT);
		s.text(BODY, "Version " + version(), x + 100, y + 34, Glass.SOFT);
		s.text(BODY, "Made by bewf", x + 100, y + 48, Glass.SOFT);
		y += 96;
		int width = contentX1 - x - 20;
		String[] paragraphs = {
			"Luceat adds dynamic lights to held items, dropped items, other players, placed blocks, and more.",
			"You can customise light colours, brightness, and shadows to create a look you like. You can add lights to any block you want.",
			"Hover a setting to see it in the live preview.",
			"Open this menu any time with /luceat or the " + LuceatMenu.keyLabel() + " key."
		};
		for (String paragraph : paragraphs) {
			for (String line : Glass.wrap(s, BODY, paragraph, width, 4)) {
				s.text(BODY, line, x, y, Glass.SOFT);
				y += 11;
			}
			y += 5;
		}
		s.text(BODY, "Renderer: " + RenderBackend.kind().label(), x, y, Glass.LAVENDER);
		y += 12;
		String note = RenderBackend.note();
		if (note != null) {
			for (String line : Glass.wrap(s, BODY, note, width, 3)) {
				s.text(BODY, line, x, y, Glass.SOFT);
				y += 11;
			}
		}
		y += 8;
		String[] labels = {"Modrinth page", "More by bewf", "Report a problem"};
		int bx = x;
		for (int i = 0; i < labels.length; i++) {
			int bw = s.width(BODY, labels[i]) + 28;
			String key = "link:" + i;
			float over = hoverOf(key);
			Glass.button(s, bx, y, bx + bw, y + 24, i == 0, over, 0.0F);
			Glass.centred(s, BODY, labels[i], bx + bw / 2, y + 7, i == 0 ? 0xFFFFFFFF : Glass.TEXT);
			hit(bx, y, bx + bw, y + 24, key, null);
			bx += bw + 8;
		}
		if (copiedNote != null && System.currentTimeMillis() < copiedUntil) {
			s.text(BODY, copiedNote, x, y + 34, Glass.GOOD);
		}
	}

	private void drawAboutLogo(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, panelX0 + 4, bodyTop + 6, 0.0F, 0.0F, 80, 80, 256, 256, 256, 256);
	}

	private void clickLink(int index) {
		String url = index == 0 ? Links.MOD : index == 1 ? Links.AUTHOR : Links.ISSUES;
		if (!Links.open(url)) {
			copiedNote = "Link copied. Paste it into your browser.";
			copiedUntil = System.currentTimeMillis() + 4000L;
		}
		dirty = true;
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
		dirty = true;
		double mx = event.x();
		double my = event.y();
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		if (search.focused() && clickDropdown(mx, my)) {
			return true;
		}
		if (LuceatLook.inside(mx, my, searchX0(), slabY0 + 10, searchX1(), slabY0 + 34)) {
			search.mouseClicked(mx, my);
			search.focus(true);
			if (blocks != null) {
				blocks.unfocus();
			}
			return true;
		}
		search.focus(false);
		int closeX0 = slabX1 - 16 - 24;
		if (LuceatLook.inside(mx, my, closeX0, slabY0 + 10, closeX0 + 24, slabY0 + 34)) {
			onClose();
			return true;
		}
		int undoX1 = closeX0 - 8;
		if (LuceatLook.inside(mx, my, undoX1 - 54, slabY0 + 10, undoX1, slabY0 + 34)) {
			undoLast();
			return true;
		}
		int chipX1 = undoX1 - 54 - 8;
		if (LuceatLook.inside(mx, my, chipX1 - (compact ? 84 : 112), slabY0 + 10, chipX1, slabY0 + 34)) {
			this.minecraft.setScreen(new PresetPicker(this));
			return true;
		}
		Hit top = hitAt(mx, my);
		if (top != null && top.key.equals("hd:power")) {
			pushUndo();
			LuceatConfig.get().enabled = !LuceatConfig.get().enabled;
			return true;
		}
		if (top != null && top.key.equals("pv:full")) {
			fullView = !fullView;
			return true;
		}
		if (clickSidebar(mx, my)) {
			return true;
		}
		if (previewPage() && panel.mouseClicked(mx, my)) {
			return true;
		}
		if (fullView && showSide && panelPage()) {
			return super.mouseClicked(event, doubleClick);
		}
		if (settingsPage()) {
			if (stripReset(mx, my) || clickList(mx, my)) {
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (BLOCKS.equals(section)) {
			return blocks.mouseClicked(mx, my) || super.mouseClicked(event, doubleClick);
		}
		if (PRESETS.equals(section)) {
			if (top != null && top.key.equals("preset:choose")) {
				this.minecraft.setScreen(new PresetPicker(this));
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (ABOUT.equals(section) && top != null && top.key.startsWith("link:")) {
			clickLink(top.key.charAt(5) - '0');
			return true;
		}
		if (SPEED.equals(section)) {
			return speed().click(mx, my, event.button()) || super.mouseClicked(event, doubleClick);
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickList(double mx, double my) {
		int top = areaTop();
		int bottom = areaBottom();
		if (my < top || my >= bottom) {
			return false;
		}
		List<Object> rows = items();
		int y = top - Math.round(scroll);
		for (Object item : rows) {
			int height = itemHeight(item);
			if (item instanceof String name) {
				int ix = listRight - 12;
				if (LuceatLook.inside(mx, my, ix - 4, y + 8, ix + Icons.width(Icons.RESET) + 4, y + 26)) {
					List<Setting> group = settingsIn(name);
					if (changedCount(group) > 0) {
						resetSettings(group);
					}
					return true;
				}
			} else if (item instanceof Setting setting && LuceatLook.inside(mx, my, listLeft - 8, y, listRight + 8, y + height)) {
				clickRow(setting, y, height, mx, my);
				return true;
			}
			y += height;
		}
		return false;
	}

	private void clickRow(Setting setting, int y, int height, double mx, double my) {
		if (!isDefault(setting)) {
			int rx = listLeft + Texts.width(BODY, setting.label()) + 8 + 12;
			if (LuceatLook.inside(mx, my, rx - 3, y + 4, rx + Icons.width(Icons.RESET) + 3, y + 18)) {
				resetSettings(List.of(setting));
				return;
			}
		}
		if (setting instanceof ToggleSetting toggle) {
			int x = toggleX();
			int pillRight = testRight(setting);
			int cy = y + (height - 16) / 2;
			if (setting.testable() && LuceatLook.inside(mx, my, pillRight - 38, cy, pillRight, cy + 16)) {
				perf.start(setting);
				return;
			}
			if (LuceatLook.inside(mx, my, x - 6, cy - 6, listRight + 8, cy + 22)) {
				pushUndo();
				toggle.set(!toggle.value());
				applyChanged(setting);
				flushRebuild();
			}
		} else if (setting instanceof SliderSetting slider) {
			if (my >= y + 20) {
				dragBefore = LuceatConfig.snapshot();
				dragging = slider;
				dragSlider(slider, mx);
			}
		} else if (setting instanceof ChoiceSetting<?> choice) {
			int x = choiceX();
			int pillRight = testRight(setting);
			int cy = y + (height - 22) / 2;
			if (setting.testable() && LuceatLook.inside(mx, my, pillRight - 38, cy, pillRight, cy + 22)) {
				perf.start(setting);
				return;
			}
			if (LuceatLook.inside(mx, my, x, cy, x + 96, cy + 22)) {
				pushUndo();
				choice.next();
				applyChanged(setting);
				flushRebuild();
			}
		}
	}

	private void dragSlider(SliderSetting slider, double mx) {
		int left = trackLeft();
		int right = trackRight();
		slider.setFraction((float) ((mx - left) / (right - left)));
		applyChanged(slider);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		dirty = true;
		if (dragging instanceof SliderSetting slider) {
			dragSlider(slider, event.x());
			return true;
		}
		if (search.mouseDragged(event.x())) {
			return true;
		}
		if (BLOCKS.equals(section) && blocks.mouseDragged(event.x(), event.y())) {
			return true;
		}
		if (panel != null && panel.mouseDragged(event.x(), event.y())) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dirty = true;
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
		dirty = true;
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
		if (previewPage() && x >= previewLeft()) {
			panel.mouseScrolled(x, y, scrollY);
			return true;
		}
		if (settingsPage() && !fullView && x >= panelX0 && x < panelX1) {
			scroll -= (float) scrollY * 24.0F;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		dirty = true;
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
		dirty = true;
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
			if (fullView) {
				fullView = false;
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
