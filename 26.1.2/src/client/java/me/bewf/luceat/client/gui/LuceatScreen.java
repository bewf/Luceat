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
import me.bewf.luceat.client.gui.glass.Bar;
import me.bewf.luceat.client.gui.glass.Glass;
import me.bewf.luceat.client.gui.glass.Icons;
import me.bewf.luceat.client.gui.glass.LuceatBlur;
import me.bewf.luceat.client.gui.glass.Motion;
import me.bewf.luceat.client.gui.glass.Optics;
import me.bewf.luceat.client.gui.glass.Paint;
import me.bewf.luceat.client.gui.glass.Pane;
import me.bewf.luceat.client.gui.glass.Surface;
import me.bewf.luceat.client.gui.glass.Texts;
import me.bewf.luceat.client.gui.presets.PresetPicker;
import me.bewf.luceat.client.gui.preview.PreviewPanel;
import me.bewf.luceat.client.gui.preview.SceneSpec;
import me.bewf.luceat.client.gui.preview.Scenes;
import me.bewf.luceat.client.gui.setting.ChoiceSetting;
import me.bewf.luceat.client.gui.setting.Cost;
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
	private static final String DEBUG = "Debug";
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo.png");
	private static final Identifier LOGO_SMALL = Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo_small.png");
	private static final Object NOTICE = new Object();
	private static final int NOTICE_HEIGHT = 62;
	private static final int ROW_HEIGHT = 46;
	private static final int HEAD_HEIGHT = 30;
	private static final int STRIP_HEIGHT = 38;
	private static final int OVERSCROLL = 110;
	private static final int HEADER_HEIGHT = 50;
	private static String lastSection;
	private static float lastScroll;
	private static final int NAV_HEIGHT = 24;
	private static final int NAV_STEP = 26;
	private static final int DROPDOWN_ROWS = 8;
	private static final int DROPDOWN_ROW = 20;
	private static final Surface.Role BODY = Surface.Role.BODY;
	private static final Surface.Role TITLE = Surface.Role.TITLE;
	private static final Surface.Role HEADING = Surface.Role.HEADING;
	private static final Surface.Role SMALL = Surface.Role.SMALL;

	private static final class Measure implements Surface {
		@Override
		public int scale() {
			return 1;
		}

		@Override
		public void fillPx(int x0, int y0, int x1, int y1, int argb) {
		}

		@Override
		public void clip(int x0, int y0, int x1, int y1) {
		}

		@Override
		public void unclip() {
		}

		@Override
		public void text(Role role, String text, int x, int y, int argb) {
		}

		@Override
		public int width(Role role, String text) {
			return Texts.width(role, text);
		}
	}

	private static final Surface MEASURE = new Measure();

	private static boolean inside(double mx, double my, int[] box) {
		return LuceatLook.inside(mx, my, box[0], box[1], box[2], box[3]);
	}

	private record Tip(String text, int x0, int y0, int x1, int y1) {
	}

	private record Hit(int x0, int y0, int x1, int y1, String key, Setting setting) {
	}

	private final Screen parent;
	private final GuiFit fit = new GuiFit();
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
	private float scrollGoal;
	private int pageMax;
	private int barMax;
	private int barTrackTop;
	private int barTrackBottom;
	private int barThumbTop;
	private int barThumbHeight;
	private int barX;
	private boolean barDrag;
	private int barGrab;
	private float enter = 1.0F;
	private int pageShift;
	private int dropdownScroll;
	private Setting dragging;
	private String dragBefore;
	private Setting hovered;
	private Setting flash;
	private long flashUntil;
	private boolean rebuildPending;
	private Presets.Preset cachedPreset;
	private String cachedJson = "";

	private long debugAt;
	private final Pane chrome = new Pane();
	private final Pane content = new Pane();
	private final Pane popup = new Pane();
	private final Map<String, Motion.Smooth> anims = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Tip> tips = new HashMap<>();
	private Tip tipLast;
	private String lastRowId = "";
	private record GeneralRow(Setting setting, int y, int left, int right) {
	}

	private final List<GeneralRow> generalRows = new ArrayList<>();
	private Setting descLast;
	private boolean descOn;
	private int[] descRect;
	private int aboutLogoX;
	private int aboutLogoY;
	private boolean aboutVisible;
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
	private boolean tiny;

	public LuceatScreen(Screen parent) {
		super(Component.literal("Luceat"));
		this.parent = parent;
		if (lastSection != null && !lastSection.equals(PRESETS)) {
			section = lastSection;
			scroll = lastScroll;
			scrollGoal = lastScroll;
		}
	}

	public LuceatScreen showSpeedTest() {
		this.section = SPEED;
		return this;
	}

	@Override
	protected void init() {
		LuceatBlur.activate(true);
		fit.update(this.width, this.height);
		int w = fit.width();
		int h = fit.height();
		int mx = Math.max(4, Math.round(w * 0.025F));
		int my = Math.max(4, Math.round(h * 0.035F));
		slabX0 = mx;
		slabX1 = w - mx;
		slabY0 = my;
		slabY1 = h - my;
		int slabW = slabX1 - slabX0;
		compact = slabW < 700;
		tiny = slabW < 560;
		sideX0 = slabX0 + 12;
		sideX1 = sideX0 + (tiny ? 30 : compact ? 88 : 100);
		bodyTop = slabY0 + HEADER_HEIGHT + 8;
		bodyBottom = slabY1 - 12;
		contentX0 = sideX1 + 4;
		contentX1 = slabX1 - 12;
		int cw = contentX1 - contentX0;
		showSide = slabW >= 640;
		panelX0 = contentX0;
		panelX1 = showSide ? contentX0 + Math.max(300, Math.round(cw * 0.5F)) : contentX1;
		previewX0 = panelX1 + 8;
		previewX1 = contentX1;
		listLeft = panelX0 + 16;
		listRight = panelX1 - 28;
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
		LuceatBlur.activate(false);
		lastSection = section;
		lastScroll = scrollGoal;
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
		Icons.release();
		me.bewf.luceat.client.gui.glass.Rounded.release();
		GuiFit.release();
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
		return PRESETS.equals(section) || BLOCKS.equals(section) || SPEED.equals(section) || DEBUG.equals(section);
	}

	private boolean settingsPage() {
		return searching() || !specialPage();
	}

	private boolean panelPage() {
		return settingsPage() || PRESETS.equals(section);
	}

	private boolean previewPage() {
		return showSide && panelPage() && !generalPage();
	}

	private boolean panelShown() {
		return panelPage() && !(fullView && showSide);
	}

	private int previewLeft() {
		return fullView ? contentX0 : previewX0;
	}

	private int guiScale() {
		return fit.pixels();
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
	public void extractRenderState(GuiGraphicsExtractor graphics, int screenMouseX, int screenMouseY, float partial) {
		extractBlurredBackground(graphics);
		Glass.frosted = minecraft.options.getMenuBackgroundBlurriness() >= 1;
		int mouseX = (int) Math.floor(fit.toUnits(screenMouseX));
		int mouseY = (int) Math.floor(fit.toUnits(screenMouseY));
		fit.begin(graphics);
		Motion.tick(System.nanoTime());
		int gs = guiScale();
		boolean panelNow = panelShown();
		int ck = Objects.hash(fit.width(), fit.height(), gs, panelNow, showSide, Glass.frosted, generalPage());
		if (!chrome.matches(0, 0, fit.width(), fit.height(), gs) || ck != chromeKey) {
			chromeKey = ck;
			chrome.begin(0, 0, fit.width(), fit.height(), gs);
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
		if (DEBUG.equals(section) && System.currentTimeMillis() - debugAt > 500L) {
			debugAt = System.currentTimeMillis();
			dirty = true;
		}
		hovered = search.focused() ? null : settingAt(mouseX, mouseY);
		if (descOn && descRect != null && !search.focused() && mouseX >= descRect[0] && mouseX < descRect[2] && mouseY >= descRect[1] && mouseY < descRect[3]) {
			hovered = descLast;
		}
		if (hovered != null) {
			lastRowId = hovered.id();
		}
		if (previewPage()) {
			panel.setOverride(null);
			if (PRESETS.equals(section) && !searching()) {
				panel.setSpec(new SceneSpec(Scenes.scene("cherry"), "This is how your current settings look.", null));
			} else if (hovered != null) {
				panel.setSpec(Scenes.forSetting(hovered.id()));
			}
			panel.setInteractive(dragging != null);
			float grow = anim("full", fullView ? 1.0F : 0.0F, 0.1F);
			int imageLeft = Math.round(previewX0 + (contentX0 - previewX0) * grow);
			panel.place(imageLeft, bodyTop, previewX1 - imageLeft, bodyBottom - bodyTop);
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
		if (aboutVisible && generalPage()) {
			drawAboutLogo(graphics);
		}
		drawLogo(graphics);
		Bar.Rects field = bar();
		search.drawGlass(graphics, this.font, field.search()[0] + 22, field.cy() - 12, field.search()[2] - 6, field.cy() + 13, "Search settings");
		if (BLOCKS.equals(section) && !searching()) {
			blocks.setFactor(fit.factor());
			blocks.drawList(graphics, panelX0, bodyTop, panelX1Wide(), bodyBottom, mouseX, mouseY);
			if (showSide) {
				blocks.drawEditor(graphics, panelX1Wide() + 8, bodyTop, contentX1, bodyBottom, mouseX, mouseY);
			}
			blocks.setArea(panelX0, bodyTop, contentX1, bodyBottom);
			blocks.drawOverlays(graphics, panelX0, bodyTop, contentX1, bodyBottom, mouseX, mouseY);
		} else if (SPEED.equals(section) && !searching()) {
			speed().render(graphics, panelX0, bodyTop, contentX1 - panelX0, bodyBottom - bodyTop, mouseX, mouseY, partial);
		}
		if (popupOpen) {
			popup.draw(graphics);
		}
		if (blocks != null && blocks.consumeDirty()) {
			ShadowVolume.reset();
		}
		perf.frame();
		fit.end(graphics);
	}

	private int panelX1Wide() {
		return showSide ? contentX1 - Math.round((contentX1 - panelX0) * 0.36F) - 8 : contentX1;
	}

	private void drawLogo(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, slabX0 + 14, slabY0 + 6, 0.0F, 12.0F, 48, 38, 256, 200, 256, 256);
	}

	private void buildChrome(Surface s, boolean panelNow) {
		Glass.backdrop(s, fit.width(), fit.height());
		Glass.slab(s, slabX0, slabY0, slabX1, slabY1, 16);
		s.lens(slabX0, slabY0, slabX1, slabY0 + HEADER_HEIGHT, 16, 14, Optics.CLEAR, 1.0F);
		if (panelNow) {
			Glass.panel(s, panelX0, bodyTop, panelRight(), bodyBottom, 12);
		}
	}

	private Bar.Rects bar() {
		return Bar.layout(MEASURE, new Bar.View(slabX0, slabY0, slabX1, compact, tiny, version(), presetLabel(), true, 1.0F, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
	}

	private int searchX0() {
		return bar().search()[0];
	}

	private int searchX1() {
		return bar().search()[2];
	}

	private int popupX0() {
		return Math.max(slabX0 + 8, searchX1() - 270);
	}

	private int popupX1() {
		return searchX1();
	}

	private int popupY0() {
		return bar().search()[3] + 6;
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
		tips.clear();
		barMax = 0;
		content.begin(slabX0, slabY0, slabX1 - slabX0, slabY1 - slabY0, gs);
		Surface s = content;
		drawHeader(s);
		drawSidebar(s);
		if (fullView && showSide) {
			// preview only
		} else if (generalPage()) {
			drawGeneral(s);
		} else if (settingsPage()) {
			drawSettings(s);
		} else if (PRESETS.equals(section)) {
			drawPresets(s);
		} else if (DEBUG.equals(section)) {
			drawScrolledPage(s);
		}
		if (previewPage()) {
			boolean timeHover = panel.overTime(mouseX, mouseY);
			panel.drawOverlay(s, perf.running() ? perf.note() : null, timeHover);
			String label = fullView ? "Back" : "Full view";
			int cw = s.width(BODY, label) + 20;
			int cx1 = previewX1 - 14;
			int cx0 = cx1 - cw;
			int rowCy = bodyTop + 19;
			float over = hoverOf("pv:full");
			int tint = Paint.mix(Glass.SOFT, Glass.TEXT, over);
			Icons.draw(s, cx0, rowCy - 6, 12, Icons.EXPAND, tint);
			s.text(BODY, label, cx0 + 20, Bar.textTop(rowCy), tint);
			hit(previewLeft(), bodyTop, previewX1, bodyBottom, "preview", null);
			hit(cx0 - 6, rowCy - 12, cx1 + 6, rowCy + 12, "pv:full", null);
		}
		drawTip(s);
		content.finish();
		popupOpen = search.focused();
		if (popupOpen) {
			popup.begin(popupX0(), popupY0(), popupX1() - popupX0(), popupY1() - popupY0(), gs);
			drawDropdown(popup);
			popup.finish();
		}
	}

	private void drawHeader(Surface s) {
		boolean on = LuceatConfig.get().enabled;
		boolean canUndo = !undo.isEmpty();
		Bar.View view = new Bar.View(slabX0, slabY0, slabX1, compact, tiny, version(), presetLabel(), on, anim("tg:master", on ? 1.0F : 0.0F, 0.05F), canUndo, anim("focus:search", search.focused() ? 1.0F : 0.0F, 0.05F), hoverOf("hd:power"),
			canUndo ? hoverOf("hd:undo") : 0.0F, hoverOf("hd:preset"), hoverOf("hd:close"));
		Bar.Rects r = Bar.draw(s, view);
		hit(r.power()[0], r.power()[1], r.power()[2], r.power()[3], "hd:power", null);
		hit(r.close()[0], r.close()[1], r.close()[2], r.close()[3], "hd:close", null);
		hit(r.undo()[0], r.undo()[1], r.undo()[2], r.undo()[3], "hd:undo", null);
		hit(r.preset()[0], r.preset()[1], r.preset()[2], r.preset()[3], "hd:preset", null);
		hit(r.search()[0], r.search()[1], r.search()[2], r.search()[3], "hd:search", null);
	}

	private List<String> sidebarNames() {
		List<String> names = new ArrayList<>();
		names.add(ALL);
		names.add("-");
		names.addAll(LuceatSettings.sections());
		names.add("-");
		names.add("~Tools");
		names.add(BLOCKS);
		names.add(SPEED);
		names.add(DEBUG);
		return names;
	}

	private static Icons.Icon iconFor(String name) {
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
			case DEBUG -> Icons.BUG;
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
				if (tiny) {
					Glass.divider(s, x0 + 6, x1 - 6, y + 4);
					y += 10;
					continue;
				}
				s.text(BODY, name.substring(1), x0 + 10, y + 4, Glass.FAINT);
				y += 18;
				continue;
			}
			boolean selected = !searching() && name.equals(section);
			float active = anim("nav:" + name, selected ? 1.0F : 0.0F, 0.05F);
			float hover = hoverOf("nav:" + name);
			Glass.navItem(s, x0, y, x1, y + NAV_HEIGHT, active, hover);
			int colour = Paint.mix(Paint.mix(Glass.MUTED, Glass.SOFT, hover), Glass.TEXT, active);
			Icons.draw(s, tiny ? x0 + (x1 - x0 - 9) / 2 : x0 + 9, y + (NAV_HEIGHT - 9) / 2, iconFor(name), Paint.mix(Glass.MUTED, Glass.PALE, Math.max(active, hover * 0.7F)));
			if (!tiny) {
				s.text(BODY, name, x0 + 26, y + 8, colour);
			}
			hit(x0, y, x1, y + NAV_HEIGHT, "nav:" + name, null);
			y += NAV_STEP;
		}
		long bytes = ShadowVolume.bytes() + PatternAtlas.bytes() + ShapeMasks.bytes();
		int footer = bodyBottom - 22;
		if (footer > y + 6 && !tiny) {
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
				y += tiny ? 10 : 18;
				continue;
			}
			if (LuceatLook.inside(mx, my, x0, y, x1, y + NAV_HEIGHT)) {
				search.set("");
				fullView = false;
				if (PRESETS.equals(name)) {
					this.minecraft.setScreen(new PresetPicker(this));
					return true;
				}
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
		scrollGoal = Math.max(0.0F, offset);
		flash = setting;
		flashUntil = System.currentTimeMillis() + 1800L;
		dirty = true;
	}

	private void setSection(String name) {
		if (SPEED.equals(section) && !SPEED.equals(name) && speed != null) {
			speed.close();
		}
		if (!name.equals(section)) {
			anims.put("page", new Motion.Smooth(0.0F));
		}
		section = name;
		scroll = 0.0F;
		scrollGoal = 0.0F;
		pageMax = 0;
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
			if (setting.section().equals(LuceatSettings.GENERAL) && setting.id().equals("Luceat")) {
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
		return ROW_HEIGHT;
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
				baseline.put(setting.id(), setting.snapshot());
			}
			baselineKey = key;
		}
		return baseline;
	}

	private boolean isDefault(Setting setting) {
		return Setting.same(setting.snapshot(), baseline().get(setting.id()));
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
				setting.restore(baseline().get(setting.id()));
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
		return bodyTop;
	}

	private int contentTop() {
		return bodyTop + STRIP_HEIGHT;
	}

	private int footerTop() {
		return bodyBottom;
	}

	private int areaBottom() {
		return bodyBottom - 8;
	}

	private int maxScroll(List<Object> rows) {
		return Math.max(0, totalHeight(rows) + STRIP_HEIGHT + 20 + OVERSCROLL - (areaBottom() - areaTop()));
	}

	private void drawResetIcon(Surface s, int cx, int cy, boolean enabled, float over) {
		int colour = !enabled ? Glass.FAINT : Paint.mix(Glass.SOFT, Glass.TEXT, over);
		Icons.draw(s, cx - 6, cy - 6, 12, Icons.RESET, colour);
	}

	private void drawStrip(Surface s, String title, List<Setting> rows, float stick) {
		int y0 = bodyTop;
		int y1 = bodyTop + STRIP_HEIGHT;
		if (stick > 0.02F) {
			s.lens(panelX0, y0, panelX1, y1, 12, 12, Optics.CLEAR, stick);
		}
		s.text(HEADING, title, listLeft, bodyTop + 11, Glass.TEXT);
		int changed = changedCount(rows);
		boolean enabled = changed > 0;
		String state = changed == 0 ? "All at preset values" : changed + " changed";
		String key = "strip:reset";
		float over = enabled ? hoverOf(key) : 0.0F;
		drawResetIcon(s, listRight - 11, bodyTop + 19, enabled, over);
		Glass.right(s, BODY, state, listRight - 26, bodyTop + 15, changed == 0 ? Glass.MUTED : Glass.PALE);
		hit(listRight - 22, bodyTop + 8, listRight, bodyTop + 30, key, null);
	}

	private boolean stripReset(double mx, double my) {
		if (!LuceatLook.inside(mx, my, listRight - 22, bodyTop + 8, listRight, bodyTop + 30)) {
			return false;
		}
		resetSettings(settingsOf(items()));
		return true;
	}

	private void drawSectionHead(Surface s, String name, int y) {
		int ty = y + 12;
		s.text(HEADING, name, listLeft, ty, fade(Glass.LAVENDER, ty));
		int lineX = listLeft + s.width(HEADING, name) + 10;
		Paint.horizontal(s, lineX, y + 17, listRight - 30, y + 18, Paint.argb(40, 205, 187, 255), Paint.argb(0, 205, 187, 255), 6);
		List<Setting> group = settingsIn(name);
		boolean enabled = changedCount(group) > 0;
		String key = "head:" + name;
		float over = enabled ? hoverOf(key) : 0.0F;
		drawResetIcon(s, listRight - 11, y + 17, enabled, over);
		int top = Math.max(y + 6, contentTop());
		int bottom = Math.min(y + 28, areaBottom());
		if (bottom > top) {
			hit(listRight - 22, top, listRight, bottom, key, null);
		}
	}

	private int fade(int colour, int textY) {
		float f = Math.max(0.0F, Math.min(1.0F, (textY - (bodyTop + 8)) / (float) (STRIP_HEIGHT - 8)));
		return Paint.alpha(colour, f * enter);
	}

	private void easeScroll() {
		float gap = scrollGoal - scroll;
		if (Math.abs(gap) < 0.4F) {
			scroll = scrollGoal;
			return;
		}
		scroll += gap * (1.0F - (float) Math.pow(0.5, Motion.dt() / 0.045F));
		busy = true;
	}

	private void drawScrollbar(Surface s, int x, int top, int bottom, int max) {
		barMax = max;
		barX = x;
		if (max <= 0) {
			return;
		}
		int track = bottom - top;
		int view = areaBottom() - areaTop();
		int thumb = Math.max(26, Math.round(track * (float) view / (view + max)));
		int thumbTop = top + Math.round((track - thumb) * (scroll / max));
		barTrackTop = top;
		barTrackBottom = bottom;
		barThumbTop = thumbTop;
		barThumbHeight = thumb;
		boolean over = hoverKey.equals("scrollbar");
		float grow = anim("bar", barDrag || over ? 1.0F : 0.0F, 0.05F);
		int half = 2 + Math.round(grow);
		int cx = x + 3;
		Paint.round(s, cx - half, top, cx + half, bottom, half, Paint.argb(Math.round(14 + 20 * grow), 205, 187, 255));
		Paint.round(s, cx - half, thumbTop, cx + half, thumbTop + thumb, half, Paint.argb(Math.round(78 + 100 * grow), 205, 187, 255));
		hit(x - 4, top, x + 10, bottom, "scrollbar", null);
	}

	private boolean clickBar(double mx, double my) {
		if (barMax <= 0 || mx < barX - 4 || mx > barX + 10 || my < barTrackTop || my > barTrackBottom) {
			return false;
		}
		if (my >= barThumbTop && my <= barThumbTop + barThumbHeight) {
			barGrab = (int) (my - barThumbTop);
		} else {
			barGrab = barThumbHeight / 2;
			dragBar(my);
		}
		barDrag = true;
		return true;
	}

	private void dragBar(double my) {
		int span = Math.max(1, barTrackBottom - barTrackTop - barThumbHeight);
		float fraction = (float) ((my - barGrab - barTrackTop) / span);
		scrollGoal = Math.max(0.0F, Math.min(1.0F, fraction)) * barMax;
		scroll = scrollGoal;
	}

	private void drawSettings(Surface s) {
		List<Object> rows = items();
		String title = searching() ? "Search results" : section;
		int top = areaTop();
		int bottom = areaBottom();
		int max = maxScroll(rows);
		scrollGoal = Math.max(0.0F, Math.min(scrollGoal, max));
		easeScroll();
		scroll = Math.max(0.0F, Math.min(scroll, max));
		enter = anim("page", 1.0F, 0.05F);
		float stick = anim("strip:stick", scroll > 3.0F ? 1.0F : 0.0F, 0.06F);
		s.clip(panelX0, top, panelX1, bottom);
		int y = contentTop() - Math.round(scroll) + Math.round((1.0F - enter) * 8.0F);
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
					drawRow(s, setting, y, height, contentTop(), bottom, first);
				}
			}
			first = false;
			y += height;
		}
		s.unclip();
		if (rows.isEmpty()) {
			s.text(BODY, "Nothing matches that.", listLeft, contentTop() + 8, Glass.MUTED);
		}
		drawStrip(s, title, settingsOf(rows), stick);
		drawScrollbar(s, panelX1 - 9, bodyTop + 12, bottom - 2, max);
		drawDescription(s);
	}

	private void drawDescription(Surface s) {
		boolean show = hovered != null && !search.focused() && !hoverKey.startsWith("tip:");
		if (show) {
			descLast = hovered;
		}
		float a = anim("desc", show ? 1.0F : 0.0F, 0.05F);
		descRect = null;
		descOn = false;
		if (descLast == null || a < 0.02F) {
			return;
		}
		int x0 = panelX0;
		int x1 = panelRight();
		List<String> lines = Glass.wrap(s, BODY, descLast.describe(), x1 - x0 - 32, 4);
		int h = lines.size() * 12 + 24;
		int y1 = bodyBottom;
		int y0 = y1 - h + Math.round((1.0F - a) * (1.0F - a) * h);
		s.clip(panelX0, bodyTop, panelRight(), bodyBottom);
		s.lens(x0, y0, x1, y1, 12, 12, Optics.DENSE, a);
		int ty = y0 + 12;
		for (String line : lines) {
			s.text(BODY, line, x0 + 16, ty, Paint.alpha(Glass.TEXT, a));
			ty += 12;
		}
		s.unclip();
		if (a > 0.5F) {
			descOn = true;
			descRect = new int[] {x0, y0, x1, y1};
			hit(x0, y0, x1, y1, "desc:card", null);
		}
	}

	private void drawTip(Surface s) {
		Tip tip = hoverKey.startsWith("tip:") ? tips.get(hoverKey) : null;
		if (tip != null) {
			tipLast = tip;
		}
		float a = anim("tip", tip != null ? 1.0F : 0.0F, 0.08F);
		if (a < 0.02F || tipLast == null) {
			return;
		}
		List<String> lines = Glass.wrap(s, BODY, tipLast.text(), 230, 8);
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, s.width(BODY, line));
		}
		int w = widest + 28;
		int h = lines.size() * 12 + 18;
		int x0 = Math.max(slabX0 + 10, Math.min(tipLast.x0(), slabX1 - 10 - w));
		int above = tipLast.y0() - 6 - h;
		int y0 = above < slabY0 + 8 ? tipLast.y1() + 6 : above;
		int x1 = x0 + w;
		int y1 = y0 + h;
		s.veil(x0 - 8, y0 - 8, x1 + 8, y1 + 12);
		Paint.spread(s, x0, y0 + 3, x1, y1 + 3, 10, 10, 8, 2.0F, (x, y) -> Paint.argb(Math.round(70 * a), 4, 2, 12));
		Paint.vertical(s, x0, y0, x1, y1, 10, Paint.argb(Math.round(238 * a), 58, 44, 112), Paint.argb(Math.round(244 * a), 30, 22, 64));
		Paint.outline(s, x0, y0, x1, y1, 10, (x, y) -> Paint.argb(Math.round(150 * a * (y < (y0 + y1) / 2 ? 1.0F : 0.5F)), 222, 208, 255));
		int ty = y0 + 9;
		for (String line : lines) {
			s.text(BODY, line, x0 + 14, ty, Paint.alpha(Glass.TEXT, a));
			ty += 12;
		}
	}

	private int drawTag(Surface s, String key, String tag, String tip, int x, int cy, int colour, int top, int bottom) {
		float over = hoverOf(key);
		int tagColour = Paint.mix(colour, 0xFFFFFFFF, over * 0.35F);
		Paint.round(s, x, cy - 6, x + 1, cy + 6, 0, Paint.argb(70, 205, 187, 255));
		Icons.draw(s, x + 8, cy - 6, 12, Icons.INFO, tagColour);
		s.text(BODY, tag, x + 24, cy - 4, tagColour);
		int w = 24 + s.width(BODY, tag) + 6;
		int y0 = Math.max(cy - 10, top);
		int y1 = Math.min(cy + 10, bottom);
		if (y1 > y0) {
			hit(x, y0, x + w, y1, key, null);
			tips.put(key, new Tip(tip, x, cy - 10, x + w, cy + 10));
		}
		return w;
	}

	private void drawNotice(Surface s, int y) {
		drawCallout(s, listLeft - 4, y + 4, listRight - listLeft + 8, "Advanced settings", "The defaults are fairly good. Only change these if you know what they do.");
	}

	private int calloutHeight(Surface s, int w, String body) {
		return 16 + 14 + Glass.wrap(s, BODY, body, w - 52, 4).size() * 11;
	}

	private int drawCallout(Surface s, int x, int y, int w, String title, String body) {
		List<String> lines = Glass.wrap(s, BODY, body, w - 52, 4);
		int h = 16 + 14 + lines.size() * 11;
		Paint.vertical(s, x, y, x + w, y + h, 10, Paint.argb(40, 244, 234, 210), Paint.argb(22, 244, 234, 210));
		Paint.outline(s, x, y, x + w, y + h, 10, (px, py) -> Paint.argb(py < y + h / 2 ? 150 : 80, 244, 234, 210));
		Icons.draw(s, x + 14, y + 10, 16, Icons.ALERT, Glass.WARN);
		s.text(TITLE, title, x + 40, y + 9, Glass.WARN);
		int ty = y + 25;
		for (String line : lines) {
			s.text(BODY, line, x + 40, ty, Glass.TEXT);
			ty += 11;
		}
		return h;
	}

	private int choiceWidth(ChoiceSetting<?> choice) {
		int widest = 0;
		for (int i = 0; i < choice.count(); i++) {
			widest = Math.max(widest, Texts.width(BODY, choice.name(i)));
		}
		return Math.max(64, widest + 24);
	}

	private int trackLeft() {
		return listLeft + 2;
	}

	private int trackRight() {
		return listRight - 44;
	}

	private String costText(Setting setting) {
		PerfTest.Result measured = perf.result(setting);
		if (measured != null) {
			return String.format(Locale.ROOT, "%.1f%% of a frame", measured.percent());
		}
		return setting.cost() == Cost.NONE ? "" : setting.cost().label();
	}

	private int pipsFor(Setting setting) {
		if (perf.result(setting) != null) {
			return 0;
		}
		return switch (setting.cost()) {
			case NONE -> 0;
			case LOW -> 1;
			case MEDIUM -> 2;
			case HIGH -> 3;
			case VERY_HIGH -> 4;
		};
	}

	private void drawPips(Surface s, int rx, int cy, int filled, int colour, float alpha) {
		for (int i = 0; i < 4; i++) {
			int px = rx - (4 - i) * 6;
			Paint.round(s, px, cy - 2, px + 4, cy + 2, 2, i < filled ? Paint.alpha(colour, alpha) : Paint.argb(Math.round(40 * alpha), 205, 187, 255));
		}
	}

	private int costColour(Setting setting) {
		PerfTest.Result measured = perf.result(setting);
		return measured != null ? measured.cost().colour() : setting.cost().colour();
	}

	private String rowLabel(Setting setting) {
		int limit = listRight - listLeft - Texts.width(BODY, costText(setting)) - (pipsFor(setting) > 0 ? 32 : 0) - 24 - (isDefault(setting) ? 0 : 16);
		return Glass.fit(this.chrome, BODY, setting.label(), Math.max(36, limit));
	}

	private void drawRow(Surface s, Setting setting, int y, int height, int hitTop, int areaBottom, boolean first) {
		String id = setting.id();
		String rowKey = "row:" + id;
		float hover = hoverOf(rowKey);
		float hoverAny = Math.max(hover, anim("h:ctl:" + id, hoverKey.endsWith(":" + id) ? 1.0F : 0.0F, 0.05F));
		boolean flashing = setting == flash && System.currentTimeMillis() < flashUntil;
		boolean blocked = !setting.blocked().isEmpty();
		float lit = Math.max(hoverAny, id.equals(lastRowId) ? 0.55F : 0.0F);
		int vy0 = Math.max(y, hitTop);
		int vy1 = Math.min(y + height, areaBottom);
		if (lit > 0.01F || flashing) {
			int a = Math.round(20 * lit);
			if (flashing) {
				a = Math.max(a, 60);
			}
			Paint.round(s, listLeft - 10, y + 2, listRight + 10, y + height - 2, 10, Paint.argb(a, flashing ? 123 : 235, flashing ? 99 : 228, 255));
		}
		if (!first && lit < 0.5F) {
			Glass.divider(s, listLeft - 2, listRight + 2, y);
		}
		int ty = y + 9;
		int cy = y + 31;
		boolean changed = !isDefault(setting);
		String label = rowLabel(setting);
		int labelColour = blocked ? Glass.FAINT : Paint.mix(Glass.TEXT, 0xFFFFFFFF, hoverAny * 0.4F);
		s.text(BODY, label, listLeft, ty, fade(labelColour, ty));
		if (changed) {
			int rx = listLeft + s.width(BODY, label) + 6;
			String rkey = "rst:" + id;
			float over = hoverOf(rkey);
			drawResetIcon(s, rx + 6, ty + 5, true, over);
			clampedHit(rx - 3, y + 5, rx + 15, y + 23, rkey, setting, hitTop, areaBottom);
		}
		if (!blocked) {
			String costLabel = costText(setting);
			int pips = pipsFor(setting);
			if (!costLabel.isEmpty()) {
				Glass.right(s, BODY, costLabel, listRight, ty, fade(Paint.alpha(costColour(setting), 0.85F), ty));
			}
			if (pips > 0) {
				drawPips(s, listRight - Texts.width(BODY, costLabel) - 8, ty + 4, pips, costColour(setting), 0.9F);
			}
		}
		if (vy1 > vy0) {
			hit(listLeft - 10, vy0, listRight + 10, vy1, rowKey, setting);
		}
		if (setting instanceof ToggleSetting toggle) {
			int x = listLeft;
			int top = cy - 8;
			float on = anim("tg:" + id, toggle.value() && !blocked ? 1.0F : 0.0F, 0.05F);
			float over = hoverOf("tg:" + id);
			Glass.toggle(s, x, top, on, blocked ? 0.0F : Math.max(over, hover));
			String state = blocked ? "Disabled" : toggle.valueText();
			int sx = x + 38;
			s.text(BODY, state, sx, cy - 4, fade(blocked ? Glass.FAINT : on > 0.5F ? Glass.GOOD : Glass.MUTED, cy - 4));
			int end = sx + s.width(BODY, state);
			if (blocked) {
				end += 10 + drawTag(s, "tip:" + id, setting.blockedShort(), setting.blockedDetail(), end + 10, cy, Glass.WARN, hitTop, areaBottom);
			}
			clampedHit(x - 2, top - 4, sx + s.width(BODY, state) + 4, top + 20, "tg:" + id, setting, hitTop, areaBottom);
			drawTestPill(s, setting, cy, hitTop, areaBottom);
		} else if (setting instanceof SliderSetting sl) {
			float value = anim("sl:" + id, sl.fraction(), dragging == setting ? 0.012F : 0.045F);
			Glass.slider(s, trackLeft(), cy, trackRight(), value, hoverAny, dragging == setting ? 1.0F : 0.0F);
			int valueColour = Paint.mix(Glass.SOFT, Glass.TEXT, Math.max(hoverAny, dragging == setting ? 1.0F : 0.0F));
			Glass.right(s, BODY, sl.valueText(), listRight, cy - 4, fade(valueColour, cy - 4));
			clampedHit(trackLeft() - 10, cy - 10, trackRight() + 10, cy + 10, "sl:" + id, setting, hitTop, areaBottom);
		} else if (setting instanceof ChoiceSetting<?> choice) {
			int w = choiceWidth(choice);
			int x = listLeft;
			int top = cy - 10;
			float over = hoverOf("ch:" + id);
			Glass.field(s, x, top, x + w, top + 20, 0.0F);
			if (over > 0.01F) {
				Paint.outline(s, x, top, x + w, top + 20, 8, Paint.argb(Math.round(110 * over), 205, 187, 255));
			}
			Glass.centred(s, BODY, choice.name(choice.index()), x + w / 2, cy - 4, fade(Glass.TEXT, cy - 4));
			clampedHit(x, top, x + w, top + 20, "ch:" + id, setting, hitTop, areaBottom);
			drawTestPill(s, setting, cy, hitTop, areaBottom);
		}
	}

	private void clampedHit(int x0, int y0, int x1, int y1, String key, Setting setting, int areaTop, int areaBottom) {
		int top = Math.max(y0, areaTop);
		int bottom = Math.min(y1, areaBottom);
		if (bottom > top) {
			hit(x0, top, x1, bottom, key, setting);
		}
	}

	private void drawTestPill(Surface s, Setting setting, int cy, int areaTop, int areaBottom) {
		if (!setting.testable()) {
			return;
		}
		boolean running = perf.running() && perf.target() == setting;
		int width = 38;
		int x = listRight - width;
		int top = cy - 8;
		String key = "test:" + setting.id();
		float over = hoverOf(key);
		Paint.round(s, x, top, x + width, top + 16, 8, Paint.argb(Math.round(18 + 18 * over), 255, 255, 255));
		Paint.outline(s, x, top, x + width, top + 16, 8, running ? Paint.argb(200, 178, 140, 255) : Paint.argb(Math.round(40 + 60 * over), 235, 228, 255));
		String label = running ? perf.progress() + "%" : "Test";
		Glass.centred(s, BODY, label, x + width / 2, top + 4, fade(running ? Glass.LAVENDER : Glass.SOFT, top + 4));
		clampedHit(x, top, x + width, top + 16, key, setting, areaTop, areaBottom);
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
		Glass.centred(s, BODY, "Choose preset", x0 + bw / 2, by0 + 7, Glass.INK);
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
			case "Medium" -> Glass.LAVENDER;
			case "Heavy" -> Glass.MAGENTA;
			default -> LuceatLook.BAD;
		};
	}

	private void drawScrolledPage(Surface s) {
		scrollGoal = Math.max(0.0F, Math.min(scrollGoal, pageMax));
		easeScroll();
		scroll = Math.max(0.0F, Math.min(scroll, pageMax));
		enter = anim("page", 1.0F, 0.05F);
		int shift = Math.round(scroll) - Math.round((1.0F - enter) * 8.0F);
		pageShift = shift;
		s.clip(panelX0 - 4, bodyTop, contentX1, footerTop() - 6);
		int end = drawDebug(s, shift);
		s.unclip();
		pageMax = Math.max(0, end + shift - (footerTop() - 12));
		drawScrollbar(s, contentX1 - 6, bodyTop + 2, footerTop() - 8, pageMax);
	}

	private void viewHit(int x0, int y0, int x1, int y1, String key) {
		int top = Math.max(y0, bodyTop);
		int bottom = Math.min(y1, footerTop() - 6);
		if (bottom > top) {
			hit(x0, top, x1, bottom, key, null);
		}
	}

	private boolean generalPage() {
		return !searching() && LuceatSettings.GENERAL.equals(section);
	}

	private int panelRight() {
		return generalPage() ? contentX1 : panelX1;
	}

	private int quietLink(Surface s, int index, String label, Icons.Brand brand, int x, int cy) {
		String key = "link:" + index;
		float over = hoverOf(key);
		int colour = Paint.mix(Glass.MUTED, Glass.TEXT, over);
		int w = 18 + s.width(BODY, label);
		Icons.brand(s, x, cy - 6, 12, brand, colour);
		s.text(BODY, label, x + 18, Bar.textTop(cy), colour);
		if (over > 0.02F) {
			Paint.horizontal(s, x + 18, cy + 8, x + w, cy + 9, Paint.alpha(colour, 0.7F * over), Paint.alpha(colour, 0.7F * over), 4);
		}
		hit(x - 4, cy - 12, x + w + 4, cy + 12, key, null);
		return w;
	}

	private void drawGeneral(Surface s) {
		enter = anim("page", 1.0F, 0.05F);
		scroll = 0.0F;
		scrollGoal = 0.0F;
		int x0 = panelX0 + 24;
		int x1 = contentX1 - 24;
		int width = x1 - x0;
		boolean split = width >= 520;
		int leftW = split ? Math.min(width * 55 / 100, 380) : width;
		int top = bodyTop + 20;
		int logoW = 72;
		aboutLogoX = x0;
		aboutLogoY = top;
		aboutVisible = true;
		int tx = x0 + logoW + 16;
		Bar.drawWordmark(s, tx, top + 2, Glass.TEXT);
		s.text(BODY, "Version " + version() + ", made by bewf", tx, top + 30, Glass.SOFT);
		int lx = tx;
		int ly = top + 52;
		String[] labels = {"Donate", "Other mods", "Report issue"};
		Icons.Brand[] brands = {Icons.KOFI, Icons.MODRINTH, Icons.GITHUB};
		for (int i = 0; i < labels.length; i++) {
			lx += quietLink(s, i, labels[i], brands[i], lx, ly) + 20;
		}
		if (copiedNote != null && System.currentTimeMillis() < copiedUntil) {
			s.text(BODY, copiedNote, tx, ly + 18, Glass.GOOD);
		}
		String[] paragraphs = {
			"Luceat adds dynamic lights to held items, dropped items, other players, placed blocks, and more.",
			"You can customise light colours, brightness, and shadows to create a look you like. You can add lights to any block you want.",
			"Hover a setting on the other pages to see it in the live preview.",
			"Open this menu any time with /luceat or the " + LuceatMenu.keyLabel() + " key."
		};
		List<String> notes = new ArrayList<>();
		String note = RenderBackend.note();
		if (note != null) {
			notes.addAll(Glass.wrap(s, SMALL, note, width, 3));
		}
		List<String> fonts = Glass.wrap(s, SMALL, "Fonts: Redaction, Barlow Condensed, Instrument Serif, Huninn, Jersey 25, Birthstone and UnifrakturCook, used under the SIL Open Font License. The licences are in the mod file.", width, 3);
		int footer = footerTop() - 12 - fonts.size() * 10 - 14 - notes.size() * 10;
		int textTop = top + 84;
		int available = (split ? footer - 6 : footer - 6 - 3 * ROW_HEIGHT) - textTop;
		int y = textTop;
		int used = 0;
		for (String paragraph : paragraphs) {
			List<String> lines = Glass.wrap(s, BODY, paragraph, leftW, 4);
			int need = lines.size() * 11 + 6;
			if (used > 0 && used + need > available) {
				break;
			}
			for (String line : lines) {
				s.text(BODY, line, x0, y, Glass.SOFT);
				y += 11;
			}
			y += 6;
			used += need;
		}
		int savedLeft = listLeft;
		int savedRight = listRight;
		int columnX0 = split ? x0 + leftW + 40 : x0;
		listLeft = columnX0;
		listRight = x1 - 4;
		int ry = split ? top : Math.min(y + 4, footer - 3 * ROW_HEIGHT - 6);
		boolean first = true;
		generalRows.clear();
		for (Setting setting : settingsIn(LuceatSettings.GENERAL)) {
			drawRow(s, setting, ry, ROW_HEIGHT, top - 6, footerTop() - 6, first);
			generalRows.add(new GeneralRow(setting, ry, listLeft, listRight));
			first = false;
			ry += ROW_HEIGHT;
		}
		listLeft = savedLeft;
		listRight = savedRight;
		int fy = footerTop() - 12 - fonts.size() * 10;
		int ny = fy - 14 - notes.size() * 10;
		s.text(SMALL, "Renderer: " + RenderBackend.kind().label(), x0, ny, Glass.LAVENDER);
		int line = ny + 12;
		for (String text : notes) {
			s.text(SMALL, text, x0, line, Glass.SOFT);
			line += 10;
		}
		for (String text : fonts) {
			s.text(SMALL, text, x0, fy, Glass.MUTED);
			fy += 10;
		}
		drawDescription(s);
	}

	private int drawDebugColumn(Surface s, List<DebugReport.Group> groups, int x, int y, int width) {
		int labelWidth = Math.min(122, width / 2);
		for (DebugReport.Group group : groups) {
			s.text(TITLE, group.title(), x, y + 2, Glass.LAVENDER);
			int lineX = x + s.width(TITLE, group.title()) + 10;
			Paint.horizontal(s, lineX, y + 7, x + width, y + 8, Paint.argb(40, 205, 187, 255), Paint.argb(0, 205, 187, 255), 6);
			y += 20;
			for (DebugReport.Row row : group.rows()) {
				s.text(BODY, Glass.fit(s, BODY, row.label(), labelWidth - 6), x, y, Glass.MUTED);
				int used = 0;
				String lastLine = "";
				for (String line : Glass.wrap(s, BODY, row.value(), width - labelWidth, 2)) {
					s.text(BODY, line, x + labelWidth, y + used, row.colour());
					lastLine = line;
					used += 11;
				}
				if (row.tag() != null) {
					int tx = x + labelWidth + s.width(BODY, lastLine) + 8;
					drawTag(s, "tip:dbg:" + row.label(), row.tag(), row.tip(), tx, y + used - 11 + 4, row.colour(), bodyTop, footerTop() - 6);
				}
				y += Math.max(12, used + 1);
			}
			y += 10;
		}
		return y;
	}

	private int drawDebug(Surface s, int shift) {
		int x = panelX0 + 4;
		int gap = 24;
		int columnWidth = (contentX1 - x - 16 - gap) / 2;
		int leftEnd = drawDebugColumn(s, DebugReport.left(), x, bodyTop + 6 - shift, columnWidth);
		int rightEnd = drawDebugColumn(s, DebugReport.right(), x + columnWidth + gap, bodyTop + 6 - shift, columnWidth);
		int y = Math.max(leftEnd, rightEnd);
		y += 4;
		String[] labels = {"Copy report", "Rebuild shader", "Rescan lights", "Resume lights"};
		int bx = x;
		int by = y;
		for (int i = 0; i < labels.length; i++) {
			int bw = s.width(BODY, labels[i]) + 28;
			if (bx + bw > contentX1 - 20 && bx > x) {
				bx = x;
				by += 32;
			}
			String key = "dbg:" + i;
			float over = hoverOf(key);
			Glass.button(s, bx, by, bx + bw, by + 24, i == 0, over, 0.0F);
			Glass.centred(s, BODY, labels[i], bx + bw / 2, by + 7, i == 0 ? Glass.INK : Glass.TEXT);
			viewHit(bx, by, bx + bw, by + 24, key);
			bx += bw + 8;
		}
		int end = by + 24;
		String sample = "The quick brown fox jumps over the lazy dog 0123456789";
		int sy = end + 36;
		s.text(BODY, "Text test: vanilla font, Luceat font, Luceat font at 2x oversample", x, sy, Glass.MUTED);
		s.text(Surface.Role.PLAIN, sample, x, sy + 16, Glass.TEXT);
		s.text(BODY, sample, x, sy + 32, Glass.TEXT);
		s.text(Surface.Role.OVERSAMPLED, sample, x, sy + 48, Glass.TEXT);
		end = sy + 62;
		if (copiedNote != null && System.currentTimeMillis() < copiedUntil) {
			s.text(BODY, copiedNote, x, by + 32, Glass.GOOD);
			end += 20;
		}
		return end + 8;
	}

	private void clickDebug(int index) {
		String note = null;
		if (index == 0) {
			this.minecraft.keyboardHandler.setClipboard(DebugReport.text());
			note = "Report copied. Paste it into a bug report.";
		} else if (index == 1 && this.minecraft.level != null) {
			this.minecraft.levelRenderer.allChanged();
			note = "Rebuilding the world meshes and the shader.";
		} else if (index == 2) {
			me.bewf.luceat.client.light.PlacedLights.clear();
			note = "Scanning for placed lights again.";
		} else if (index == 3) {
			me.bewf.luceat.client.light.Panic.resume();
			note = "Lights are back on.";
		}
		if (note != null) {
			copiedNote = note;
			copiedUntil = System.currentTimeMillis() + 3000L;
		}
		dirty = true;
	}

	private void drawAboutLogo(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, aboutLogoX, aboutLogoY, 0.0F, 12.0F, 72, 56, 256, 200, 256, 256);
	}

	private void clickLink(int index) {
		String url = index == 0 ? Links.DONATE : index == 1 ? Links.AUTHOR : Links.ISSUES;
		if (!Links.open(url)) {
			copiedNote = "Could not open your browser.";
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
		double mx = fit.toUnits(event.x());
		double my = fit.toUnits(event.y());
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		if (search.focused() && clickDropdown(mx, my)) {
			return true;
		}
		Bar.Rects header = bar();
		if (inside(mx, my, header.search())) {
			search.mouseClicked(mx, my);
			search.focus(true);
			if (blocks != null) {
				blocks.unfocus();
			}
			return true;
		}
		search.focus(false);
		if (descOn && descRect != null && mx >= descRect[0] && mx < descRect[2] && my >= descRect[1] && my < descRect[3]) {
			return true;
		}
		if (inside(mx, my, header.close())) {
			onClose();
			return true;
		}
		if (inside(mx, my, header.undo())) {
			undoLast();
			return true;
		}
		if (inside(mx, my, header.preset())) {
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
		if ((settingsPage() || DEBUG.equals(section)) && clickBar(mx, my)) {
			return true;
		}
		if (previewPage() && panel.mouseClicked(mx, my)) {
			return true;
		}
		if (fullView && showSide && panelPage()) {
			return super.mouseClicked(event, doubleClick);
		}
		if (settingsPage()) {
			if (top != null && top.key.startsWith("link:")) {
				clickLink(top.key.charAt(5) - '0');
				return true;
			}
			if (generalPage() && clickGeneral(mx, my)) {
				return true;
			}
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
		if (DEBUG.equals(section) && top != null && top.key.startsWith("dbg:")) {
			clickDebug(top.key.charAt(4) - '0');
			return true;
		}
		if (SPEED.equals(section)) {
			return speed().click(mx, my, event.button()) || super.mouseClicked(event, doubleClick);
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickGeneral(double mx, double my) {
		for (GeneralRow row : generalRows) {
			if (LuceatLook.inside(mx, my, row.left() - 10, row.y(), row.right() + 10, row.y() + ROW_HEIGHT)) {
				int savedLeft = listLeft;
				int savedRight = listRight;
				listLeft = row.left();
				listRight = row.right();
				clickRow(row.setting(), row.y(), ROW_HEIGHT, mx, my);
				listLeft = savedLeft;
				listRight = savedRight;
				return true;
			}
		}
		return false;
	}

	private boolean clickList(double mx, double my) {
		int top = contentTop();
		int bottom = areaBottom();
		if (my < top || my >= bottom) {
			return false;
		}
		List<Object> rows = items();
		int y = contentTop() - Math.round(scroll);
		for (Object item : rows) {
			int height = itemHeight(item);
			if (item instanceof String name) {
				if (LuceatLook.inside(mx, my, listRight - 22, y + 6, listRight, y + 28)) {
					List<Setting> group = settingsIn(name);
					if (changedCount(group) > 0) {
						resetSettings(group);
					}
					return true;
				}
			} else if (item instanceof Setting setting && LuceatLook.inside(mx, my, listLeft - 10, y, listRight + 10, y + height)) {
				clickRow(setting, y, height, mx, my);
				return true;
			}
			y += height;
		}
		return false;
	}

	private void clickRow(Setting setting, int y, int height, double mx, double my) {
		int cy = y + 31;
		if (!isDefault(setting)) {
			int rx = listLeft + Texts.width(BODY, rowLabel(setting)) + 6;
			if (LuceatLook.inside(mx, my, rx - 3, y + 5, rx + 15, y + 23)) {
				resetSettings(List.of(setting));
				return;
			}
		}
		boolean onTest = setting.testable() && LuceatLook.inside(mx, my, listRight - 38, cy - 8, listRight, cy + 8);
		if (setting instanceof ToggleSetting toggle) {
			if (onTest) {
				perf.start(setting);
				return;
			}
			String state = setting.blocked().isEmpty() ? toggle.valueText() : "Disabled";
			if (setting.blocked().isEmpty() && LuceatLook.inside(mx, my, listLeft - 2, cy - 12, listLeft + 42 + Texts.width(BODY, state), cy + 12)) {
				pushUndo();
				toggle.set(!toggle.value());
				applyChanged(setting);
				flushRebuild();
			}
		} else if (setting instanceof SliderSetting slider) {
			if (mx >= trackLeft() - 10 && mx <= listRight + 10) {
				dragBefore = LuceatConfig.snapshot();
				dragging = slider;
				dragSlider(slider, mx);
			}
		} else if (setting instanceof ChoiceSetting<?> choice) {
			if (onTest) {
				perf.start(setting);
				return;
			}
			int w = choiceWidth(choice);
			if (LuceatLook.inside(mx, my, listLeft, cy - 10, listLeft + w, cy + 10)) {
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
		if (barDrag) {
			dragBar(fit.toUnits(event.y()));
			return true;
		}
		if (SPEED.equals(section) && speed().drag(fit.toUnits(event.y()))) {
			return true;
		}
		if (dragging instanceof SliderSetting slider) {
			dragSlider(slider, fit.toUnits(event.x()));
			return true;
		}
		if (search.mouseDragged(fit.toUnits(event.x()))) {
			return true;
		}
		if (BLOCKS.equals(section) && blocks.mouseDragged(fit.toUnits(event.x()), fit.toUnits(event.y()))) {
			return true;
		}
		if (panel != null && panel.mouseDragged(fit.toUnits(event.x()), fit.toUnits(event.y()))) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dirty = true;
		boolean wasBar = barDrag || (SPEED.equals(section) && speed().release());
		barDrag = false;
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
		return was || wasBar || searchUp || blockUp || panelUp || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double screenX, double screenY, double scrollX, double scrollY) {
		dirty = true;
		double x = fit.toUnits(screenX);
		double y = fit.toUnits(screenY);
		if (search.focused()) {
			dropdownScroll -= (int) Math.signum(scrollY);
			return true;
		}
		if (BLOCKS.equals(section) && !searching()) {
			return blocks.mouseScrolled(x, scrollY) || super.mouseScrolled(screenX, screenY, scrollX, scrollY);
		}
		if (SPEED.equals(section) && !searching()) {
			return speed().scroll(x, y, scrollY) || super.mouseScrolled(screenX, screenY, scrollX, scrollY);
		}
		if (previewPage() && x >= previewLeft()) {
			panel.mouseScrolled(x, y, scrollY);
			return true;
		}
		if (settingsPage() && !fullView && x >= panelX0 && x < panelX1) {
			scrollGoal = Math.max(0.0F, Math.min(scrollGoal - (float) scrollY * 36.0F, maxScroll(items())));
			return true;
		}
		if (DEBUG.equals(section) && x >= panelX0) {
			scrollGoal = Math.max(0.0F, Math.min(scrollGoal - (float) scrollY * 36.0F, pageMax));
			return true;
		}
		return super.mouseScrolled(screenX, screenY, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		dirty = true;
		if (BLOCKS.equals(section) && !search.focused()) {
			return blocks.charTyped(event) || super.charTyped(event);
		}
		if (!search.focused() && !SPEED.equals(section) && event.codepoint() >= 32 && event.codepoint() != 127) {
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
			scrollGoal = 0.0F;
			dropdownScroll = 0;
			return true;
		}
		return super.keyPressed(event);
	}
}
