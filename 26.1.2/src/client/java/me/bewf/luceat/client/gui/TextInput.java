package me.bewf.luceat.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.GLFW;

public final class TextInput {
	private static final int HISTORY = 60;

	private final int max;
	private String text = "";
	private boolean focused;
	private int caret;
	private int anchor;
	private boolean selecting;
	private final List<String> undo = new ArrayList<>();
	private final List<String> redo = new ArrayList<>();
	private Font lastFont;
	private boolean glass;
	private int lastX0;
	private int lastX1;
	private int lastY0;
	private int lastY1;
	private int lastOffset;

	public TextInput(int max) {
		this.max = max;
	}

	public String text() {
		return text;
	}

	public void set(String value) {
		text = value.length() > max ? value.substring(0, max) : value;
		caret = text.length();
		anchor = caret;
		undo.clear();
		redo.clear();
	}

	public boolean focused() {
		return focused;
	}

	public void focus(boolean value) {
		if (focused && !value) {
			selecting = false;
		}
		focused = value;
		if (value) {
			caret = Math.min(caret, text.length());
			anchor = Math.min(anchor, text.length());
		}
	}

	private boolean hasSelection() {
		return caret != anchor;
	}

	private int selStart() {
		return Math.min(caret, anchor);
	}

	private int selEnd() {
		return Math.max(caret, anchor);
	}

	private void remember() {
		if (undo.isEmpty() || !undo.get(undo.size() - 1).equals(text)) {
			undo.add(text);
			if (undo.size() > HISTORY) {
				undo.remove(0);
			}
		}
		redo.clear();
	}

	private void replaceSelection(String value) {
		remember();
		int start = selStart();
		int end = selEnd();
		String clean = value.replace('\n', ' ').replace('\r', ' ');
		int room = max - (text.length() - (end - start));
		if (room < clean.length()) {
			clean = clean.substring(0, Math.max(0, room));
		}
		text = text.substring(0, start) + clean + text.substring(end);
		caret = start + clean.length();
		anchor = caret;
	}

	private void restoreText(String value) {
		text = value;
		caret = text.length();
		anchor = caret;
	}

	public boolean charTyped(CharacterEvent event) {
		if (!focused || event.codepoint() < 32 || event.codepoint() == 127) {
			return false;
		}
		if (text.length() - (selEnd() - selStart()) >= max) {
			return true;
		}
		replaceSelection(event.codepointAsString());
		return true;
	}

	private int wordLeft(int from) {
		int i = Math.max(0, from);
		while (i > 0 && text.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && text.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private int wordRight(int from) {
		int i = Math.min(text.length(), from);
		while (i < text.length() && text.charAt(i) == ' ') {
			i++;
		}
		while (i < text.length() && text.charAt(i) != ' ') {
			i++;
		}
		return i;
	}

	private void moveCaret(int to, boolean extend) {
		caret = Math.max(0, Math.min(text.length(), to));
		if (!extend) {
			anchor = caret;
		}
	}

	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		int key = event.key();
		boolean ctrl = event.hasControlDown();
		boolean shift = event.hasShiftDown();
		if (event.isEscape()) {
			focused = false;
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_A) {
			anchor = 0;
			caret = text.length();
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_C) {
			if (hasSelection()) {
				Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selStart(), selEnd()));
			}
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_X) {
			if (hasSelection()) {
				Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selStart(), selEnd()));
				replaceSelection("");
			}
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_V || event.isPaste()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null && !clip.isEmpty()) {
				replaceSelection(clip);
			}
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_Z && !shift) {
			if (!undo.isEmpty()) {
				redo.add(text);
				restoreText(undo.remove(undo.size() - 1));
			}
			return true;
		}
		if (ctrl && (key == GLFW.GLFW_KEY_Y || key == GLFW.GLFW_KEY_Z && shift)) {
			if (!redo.isEmpty()) {
				undo.add(text);
				restoreText(redo.remove(redo.size() - 1));
			}
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (hasSelection()) {
					replaceSelection("");
				} else if (caret > 0) {
					anchor = ctrl ? wordLeft(caret) : caret - 1;
					replaceSelection("");
				}
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (hasSelection()) {
					replaceSelection("");
				} else if (caret < text.length()) {
					anchor = ctrl ? wordRight(caret) : caret + 1;
					replaceSelection("");
				}
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				if (hasSelection() && !shift) {
					moveCaret(selStart(), false);
				} else {
					moveCaret(ctrl ? wordLeft(caret) : caret - 1, shift);
				}
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (hasSelection() && !shift) {
					moveCaret(selEnd(), false);
				} else {
					moveCaret(ctrl ? wordRight(caret) : caret + 1, shift);
				}
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				moveCaret(0, shift);
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				moveCaret(text.length(), shift);
				return true;
			}
			default -> {
			}
		}
		return event.isConfirmation();
	}

	private int indexAt(double mx) {
		if (lastFont == null) {
			return text.length();
		}
		double local = mx - (lastX0 + (glass ? 8 : 7) + lastOffset);
		int best = 0;
		for (int i = 0; i <= text.length(); i++) {
			double width = measure(lastFont, text.substring(0, i));
			if (width > local) {
				if (i > 0) {
					double prev = measure(lastFont, text.substring(0, i - 1));
					return local - prev < width - local ? i - 1 : i;
				}
				return 0;
			}
			best = i;
		}
		return best;
	}

	public boolean mouseClicked(double mx, double my) {
		boolean inside = mx >= lastX0 && mx < lastX1 && my >= lastY0 && my < lastY1;
		if (!inside) {
			return false;
		}
		focused = true;
		selecting = true;
		moveCaret(indexAt(mx), false);
		return true;
	}

	public boolean mouseDragged(double mx) {
		if (!selecting) {
			return false;
		}
		moveCaret(indexAt(mx), true);
		return true;
	}

	public boolean mouseReleased() {
		boolean was = selecting;
		selecting = false;
		return was;
	}

	private int measure(Font font, String value) {
		return glass ? LuceatFonts.width(font, LuceatFonts.body(value)) : font.width(value);
	}

	public void drawGlass(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int x1, int y1, String placeholder) {
		glass = true;
		lastFont = font;
		lastX0 = x0;
		lastX1 = x1;
		lastY0 = y0;
		lastY1 = y1;
		int textY = y0 + (y1 - y0 - 9) / 2;
		graphics.enableScissor(x0 + 4, y0, x1 - 4, y1);
		if (text.isEmpty() && !focused) {
			lastOffset = 0;
			LuceatFonts.draw(graphics, font, LuceatFonts.body(placeholder), x0 + 8, textY, 0xFFB1ABCF, false);
		} else {
			int caretX = measure(font, text.substring(0, Math.min(caret, text.length())));
			int visible = x1 - x0 - 16;
			int offset = Math.min(0, visible - caretX - 2);
			lastOffset = offset;
			int baseX = x0 + 8 + offset;
			if (focused && hasSelection()) {
				int a = baseX + measure(font, text.substring(0, selStart()));
				int b = baseX + measure(font, text.substring(0, selEnd()));
				graphics.fill(a, y0 + 4, b, y1 - 4, 0x805B46C8);
			}
			LuceatFonts.draw(graphics, font, LuceatFonts.body(text), baseX, textY, 0xFFF1EEFA, false);
			if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
				graphics.fill(baseX + caretX, y0 + 4, baseX + caretX + 1, y1 - 4, 0xFFE4D6FF);
			}
		}
		graphics.disableScissor();
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int x1, int y1, String placeholder) {
		LuceatLook.roundOutlined(graphics, x0, y0, x1, y1, 0x6605030C, focused ? LuceatLook.ACCENT : LuceatLook.BORDER);
		drawGlass(graphics, font, x0, y0, x1, y1, placeholder);
	}
}
