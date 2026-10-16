package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.setting.ChoiceSetting;
import me.bewf.luceat.client.gui.setting.LuceatSettings;
import me.bewf.luceat.client.gui.setting.Setting;
import me.bewf.luceat.client.gui.setting.SliderSetting;
import me.bewf.luceat.client.gui.setting.ToggleSetting;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import org.lwjgl.glfw.GLFW;

public final class LuceatScreen extends Screen {
	private static final String ABOUT = "About";
	private static final int ROW_HEIGHT = 34;
	private static final int ROW_GAP = 4;
	private static final int SIDEBAR_WIDTH = 108;
	private static final int PREVIEW_WIDTH = 252;
	private static final int HEADER_HEIGHT = 32;

	private final Screen parent;
	private final List<Setting> settings = LuceatSettings.create();
	private final PreviewScene scene = new PreviewScene();
	private final PerfTest perf = new PerfTest();
	private String section = LuceatSettings.LIGHTING;
	private String query = "";
	private float scroll;
	private float time = 0.0F;
	private Setting dragging;
	private boolean draggingTime;
	private boolean draggingLight;
	private boolean rebuildPending;

	private int left;
	private int top;
	private int right;
	private int bottom;
	private int listLeft;
	private int listRight;
	private int previewLeft;
	private boolean showPreview;
	private int previewCell;

	public LuceatScreen(Screen parent) {
		super(Component.literal("Luceat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int windowWidth = Math.min(this.width - 20, 840);
		int windowHeight = Math.min(this.height - 20, 480);
		left = (this.width - windowWidth) / 2;
		top = (this.height - windowHeight) / 2;
		right = left + windowWidth;
		bottom = top + windowHeight;
		showPreview = windowWidth >= 640;
		listLeft = left + SIDEBAR_WIDTH + 8;
		previewLeft = right - PREVIEW_WIDTH - 8;
		listRight = showPreview ? previewLeft - 8 : right - 8;
		previewCell = Math.max(2, (PREVIEW_WIDTH - 16) / (PreviewScene.WIDTH_BLOCKS * 4));
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
		if (rebuildPending) {
			ShadowVolume.reset();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		graphics.fill(0, 0, this.width, this.height, LuceatLook.BACKDROP);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		LuceatLook.roundOutlined(graphics, left, top, right, bottom, LuceatLook.WINDOW, LuceatLook.BORDER);
		drawHeader(graphics, mouseX, mouseY);
		drawSidebar(graphics, mouseX, mouseY);
		if (ABOUT.equals(section) && query.isEmpty()) {
			drawAbout(graphics);
		} else {
			drawList(graphics, mouseX, mouseY);
		}
		if (showPreview) {
			drawPreview(graphics, mouseX, mouseY);
		}
		perf.frame();
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(left + 1, top + 1, right - 1, top + HEADER_HEIGHT, LuceatLook.HEADER);
		graphics.fill(left + 1, top + HEADER_HEIGHT, right - 1, top + HEADER_HEIGHT + 1, LuceatLook.BORDER);
		graphics.fill(left + 1, top + 1, left + 4, top + HEADER_HEIGHT, LuceatLook.ACCENT);
		graphics.text(this.font, "Luceat", left + 14, top + 11, LuceatLook.ACCENT, false);
		String version = FabricLoader.getInstance().getModContainer("luceat").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		graphics.text(this.font, version, left + 14 + this.font.width("Luceat") + 6, top + 11, LuceatLook.MUTED, false);

		int closeX = right - 26;
		boolean closeHover = LuceatLook.inside(mouseX, mouseY, closeX, top + 6, closeX + 20, top + 26);
		LuceatLook.round(graphics, closeX, top + 6, closeX + 20, top + 26, closeHover ? 0xFF4A2030 : LuceatLook.CARD);
		graphics.text(this.font, "x", closeX + 7, top + 12, closeHover ? LuceatLook.BAD : LuceatLook.SOFT, false);

		int searchWidth = 170;
		int searchLeft = closeX - 10 - searchWidth;
		LuceatLook.roundOutlined(graphics, searchLeft, top + 6, searchLeft + searchWidth, top + 26, LuceatLook.CARD, query.isEmpty() ? LuceatLook.BORDER : LuceatLook.ACCENT_DIM);
		String shown = query.isEmpty() ? "Search settings" : query + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		graphics.enableScissor(searchLeft + 4, top + 6, searchLeft + searchWidth - 4, top + 26);
		graphics.text(this.font, shown, searchLeft + 8, top + 12, query.isEmpty() ? LuceatLook.MUTED : LuceatLook.TEXT, false);
		graphics.disableScissor();
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR_WIDTH;
		int y = top + HEADER_HEIGHT + 10;
		List<String> names = new ArrayList<>(LuceatSettings.sections());
		names.add(ABOUT);
		for (String name : names) {
			boolean selected = query.isEmpty() && name.equals(section);
			boolean hover = LuceatLook.inside(mouseX, mouseY, x0, y, x1, y + 24);
			if (selected) {
				LuceatLook.round(graphics, x0, y, x1, y + 24, LuceatLook.ACCENT_SOFT);
				graphics.fill(x0, y + 4, x0 + 2, y + 20, LuceatLook.ACCENT);
			} else if (hover) {
				LuceatLook.round(graphics, x0, y, x1, y + 24, LuceatLook.CARD);
			}
			graphics.text(this.font, name, x0 + 10, y + 8, selected ? LuceatLook.TEXT : LuceatLook.SOFT, false);
			y += 28;
		}
		long bytes = ShadowVolume.bytes() + PatternAtlas.bytes();
		int footer = bottom - 34;
		graphics.text(this.font, "Video memory", x0 + 4, footer, LuceatLook.MUTED, false);
		graphics.text(this.font, String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0), x0 + 4, footer + 11, bytes == 0 ? LuceatLook.MUTED : LuceatLook.TEXT, false);
	}

	private List<Setting> visible() {
		List<Setting> result = new ArrayList<>();
		for (Setting setting : settings) {
			if (query.isEmpty() ? setting.section().equals(section) : setting.matches(query)) {
				result.add(setting);
			}
		}
		return result;
	}

	private int listTop() {
		return top + HEADER_HEIGHT + 10;
	}

	private int listBottom() {
		return bottom - 10;
	}

	private int maxScroll(int count) {
		return Math.max(0, count * (ROW_HEIGHT + ROW_GAP) - (listBottom() - listTop()));
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Setting> rows = visible();
		scroll = Math.max(0.0F, Math.min(scroll, maxScroll(rows.size())));
		graphics.enableScissor(listLeft - 2, listTop(), listRight + 2, listBottom());
		Setting hovered = null;
		int y = listTop() - Math.round(scroll);
		for (Setting setting : rows) {
			boolean inView = y + ROW_HEIGHT > listTop() && y < listBottom();
			boolean hover = inView && LuceatLook.inside(mouseX, mouseY, listLeft, y, listRight, y + ROW_HEIGHT) && mouseY >= listTop() && mouseY < listBottom();
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
			graphics.text(this.font, "Nothing matches that.", listLeft + 8, listTop() + 8, LuceatLook.MUTED, false);
		}
		int max = maxScroll(rows.size());
		if (max > 0) {
			int trackTop = listTop();
			int trackHeight = listBottom() - listTop();
			int thumb = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
			int thumbTop = trackTop + Math.round((trackHeight - thumb) * (scroll / max));
			graphics.fill(listRight + 3, thumbTop, listRight + 5, thumbTop + thumb, LuceatLook.TRACK);
		}
		if (hovered != null && dragging == null) {
			showTooltip(graphics, hovered, mouseX, mouseY);
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, Setting setting, int y, boolean hover, int mouseX, int mouseY) {
		LuceatLook.round(graphics, listLeft, y, listRight, y + ROW_HEIGHT, hover ? LuceatLook.CARD_HOVER : LuceatLook.CARD);
		graphics.text(this.font, setting.label(), listLeft + 10, y + 7, LuceatLook.TEXT, false);
		String costText = "Cost: " + setting.cost().label();
		graphics.text(this.font, costText, listLeft + 10, y + 19, setting.cost().colour(), false);
		Float measured = perf.result(setting);
		if (measured != null) {
			graphics.text(this.font, "tested " + PerfTest.describe(measured), listLeft + 14 + this.font.width(costText), y + 19, LuceatLook.MUTED, false);
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
		lines.addAll(this.font.split(Component.literal(setting.label()).withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF)), 220));
		lines.addAll(this.font.split(Component.literal(setting.help()).withStyle(Style.EMPTY.withColor(0xDDDDE6)), 220));
		lines.addAll(this.font.split(Component.literal("Cost: " + setting.cost().label()).withStyle(Style.EMPTY.withColor(setting.cost().colour() & 0xFFFFFF)), 220));
		Float measured = perf.result(setting);
		if (measured != null) {
			lines.addAll(this.font.split(Component.literal("Last test: " + PerfTest.describe(measured)).withStyle(Style.EMPTY.withColor(0x9A9AAA)), 220));
		}
		graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	private void drawAbout(GuiGraphicsExtractor graphics) {
		int x = listLeft + 4;
		int y = listTop() + 4;
		String[] lines = {
			"Luceat lights the world from your hands, dropped items and other players,",
			"with shadows, coloured light and no chunk rebuilds.",
			"",
			"Open this menu any time with /luceat.",
			"Hover a setting to see what it does and how much it costs.",
			"Press Test on a setting to measure its cost on your computer.",
			"Turn VSync off first or the test cannot see small changes.",
			"",
			"Coming later:",
			"  Light from placed blocks, with colour",
			"  Flicker for fire and torches",
			"  Shiny reflections",
			"  Shader pack support",
		};
		for (String line : lines) {
			boolean heading = line.endsWith(":");
			graphics.text(this.font, line, x, y, heading ? LuceatLook.ACCENT : LuceatLook.SOFT, false);
			y += 12;
		}
	}

	private int sceneLeft() {
		return previewLeft + 8;
	}

	private int sceneTop() {
		return listTop() + 22;
	}

	private int sceneWidth() {
		return PreviewScene.WIDTH_BLOCKS * 4 * previewCell;
	}

	private int sceneHeight() {
		return PreviewScene.DEPTH_BLOCKS * 4 * previewCell;
	}

	private int timeTrackTop() {
		return sceneTop() + sceneHeight() + 34;
	}

	private void drawPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		LuceatLook.round(graphics, previewLeft, listTop(), right - 8, listBottom(), LuceatLook.SIDEBAR);
		graphics.text(this.font, "Live preview", previewLeft + 8, listTop() + 7, LuceatLook.TEXT, false);
		scene.render(graphics, sceneLeft(), sceneTop(), previewCell, time);
		graphics.outline(sceneLeft() - 1, sceneTop() - 1, sceneWidth() + 2, sceneHeight() + 2, LuceatLook.BORDER);
		int y = sceneTop() + sceneHeight() + 6;
		graphics.text(this.font, "Drag in the picture to move the light.", sceneLeft(), y, LuceatLook.MUTED, false);
		y += 14;
		graphics.text(this.font, "Time of day", sceneLeft(), y, LuceatLook.SOFT, false);
		String clock = time < 0.34F ? "Night" : time < 0.67F ? "Evening" : "Day";
		graphics.text(this.font, clock, sceneLeft() + sceneWidth() - this.font.width(clock), y, LuceatLook.SOFT, false);
		int trackY = y + 14;
		graphics.fill(sceneLeft(), trackY, sceneLeft() + sceneWidth(), trackY + 2, LuceatLook.TRACK);
		int filled = Math.round(sceneWidth() * time);
		graphics.fill(sceneLeft(), trackY, sceneLeft() + filled, trackY + 2, LuceatLook.ACCENT);
		LuceatLook.round(graphics, sceneLeft() + filled - 4, trackY - 4, sceneLeft() + filled + 4, trackY + 6, 0xFFE6C9FF);
		y = trackY + 16;
		legend(graphics, sceneLeft(), y, 0xFF6C6C72, "Stone");
		legend(graphics, sceneLeft() + 48, y, 0xFF3A803A, "Leaves");
		legend(graphics, sceneLeft() + 100, y, 0xFFD24650, "Glass");
		legend(graphics, sceneLeft() + 148, y, 0xFFFFD27A, "Light");
		if (!perf.note().isEmpty()) {
			List<FormattedCharSequence> lines = this.font.split(Component.literal(perf.note()).withStyle(Style.EMPTY.withColor(LuceatLook.WARN & 0xFFFFFF)), PREVIEW_WIDTH - 16);
			int ny = y + 16;
			for (FormattedCharSequence line : lines) {
				graphics.text(this.font, line, sceneLeft(), ny, 0xFFFFFFFF, false);
				ny += 10;
			}
		}
	}

	private void legend(GuiGraphicsExtractor graphics, int x, int y, int colour, String text) {
		graphics.fill(x, y, x + 6, y + 6, colour);
		graphics.text(this.font, text, x + 9, y - 1, LuceatLook.MUTED, false);
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

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		int closeX = right - 26;
		if (LuceatLook.inside(mx, my, closeX, top + 6, closeX + 20, top + 26)) {
			onClose();
			return true;
		}
		if (clickSidebar(mx, my)) {
			return true;
		}
		if (showPreview && clickPreview(mx, my)) {
			return true;
		}
		if (!(ABOUT.equals(section) && query.isEmpty()) && clickList(mx, my)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickSidebar(double mx, double my) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR_WIDTH;
		int y = top + HEADER_HEIGHT + 10;
		List<String> names = new ArrayList<>(LuceatSettings.sections());
		names.add(ABOUT);
		for (String name : names) {
			if (LuceatLook.inside(mx, my, x0, y, x1, y + 24)) {
				section = name;
				query = "";
				scroll = 0.0F;
				return true;
			}
			y += 28;
		}
		return false;
	}

	private boolean clickPreview(double mx, double my) {
		if (LuceatLook.inside(mx, my, sceneLeft(), sceneTop(), sceneLeft() + sceneWidth(), sceneTop() + sceneHeight())) {
			draggingLight = true;
			moveLight(mx, my);
			return true;
		}
		int trackY = timeTrackTop();
		if (LuceatLook.inside(mx, my, sceneLeft() - 4, trackY - 6, sceneLeft() + sceneWidth() + 4, trackY + 12)) {
			draggingTime = true;
			moveTime(mx);
			return true;
		}
		return false;
	}

	private boolean clickList(double mx, double my) {
		if (my < listTop() || my >= listBottom()) {
			return false;
		}
		List<Setting> rows = visible();
		int y = listTop() - Math.round(scroll);
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
		int controlRight = listRight - 10;
		if (setting instanceof ToggleSetting toggle) {
			int x = controlRight - 30;
			int pillRight = x - 8;
			if (setting.testable() && LuceatLook.inside(mx, my, pillRight - 34, y + 9, pillRight, y + 25)) {
				perf.start(setting);
				return;
			}
			toggle.set(!toggle.value());
			applyChanged(setting);
			flushRebuild();
		} else if (setting instanceof SliderSetting slider) {
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

	private void moveLight(double mx, double my) {
		scene.moveLight((float) ((mx - sceneLeft()) / (4.0 * previewCell)), (float) ((my - sceneTop()) / (4.0 * previewCell)));
	}

	private void moveTime(double mx) {
		time = (float) Math.max(0.0, Math.min(1.0, (mx - sceneLeft()) / sceneWidth()));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging instanceof SliderSetting slider) {
			dragSlider(slider, event.x());
			return true;
		}
		if (draggingLight) {
			moveLight(event.x(), event.y());
			return true;
		}
		if (draggingTime) {
			moveTime(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = dragging != null || draggingLight || draggingTime;
		dragging = null;
		draggingLight = false;
		draggingTime = false;
		flushRebuild();
		return was || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= listLeft && x < listRight + 6) {
			scroll -= (float) scrollY * 24.0F;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (query.length() < 32 && event.codepoint() >= 32 && event.codepoint() != 127) {
			query += event.codepointAsString();
			scroll = 0.0F;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			scroll = 0.0F;
			return true;
		}
		if (event.isEscape() && !query.isEmpty()) {
			query = "";
			return true;
		}
		return super.keyPressed(event);
	}
}
