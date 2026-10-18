package me.bewf.luceat.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.GLFW;

public final class TextInput {
	private final int max;
	private String text = "";
	private boolean focused;

	public TextInput(int max) {
		this.max = max;
	}

	public String text() {
		return text;
	}

	public void set(String value) {
		text = value.length() > max ? value.substring(0, max) : value;
	}

	public boolean focused() {
		return focused;
	}

	public void focus(boolean value) {
		focused = value;
	}

	public boolean charTyped(CharacterEvent event) {
		if (!focused || event.codepoint() < 32 || event.codepoint() == 127 || text.length() >= max) {
			return false;
		}
		text += event.codepointAsString();
		return true;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
			if (!text.isEmpty()) {
				text = event.hasControlDown() ? "" : text.substring(0, text.length() - 1);
			}
			return true;
		}
		if (event.isPaste()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) {
				text = (text + clip.replace('\n', ' ')).substring(0, Math.min(max, text.length() + clip.length()));
			}
			return true;
		}
		if (event.isEscape()) {
			focused = false;
			return true;
		}
		return event.isConfirmation();
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int x1, int y1, String placeholder) {
		LuceatLook.roundOutlined(graphics, x0, y0, x1, y1, LuceatLook.CARD, focused ? LuceatLook.ACCENT : LuceatLook.BORDER);
		String shown = text.isEmpty() && !focused ? placeholder : text + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		graphics.enableScissor(x0 + 3, y0, x1 - 3, y1);
		int width = font.width(shown);
		int offset = Math.min(0, (x1 - x0 - 14) - width);
		graphics.text(font, shown, x0 + 7 + offset, y0 + (y1 - y0 - 8) / 2, text.isEmpty() && !focused ? LuceatLook.MUTED : LuceatLook.TEXT, false);
		graphics.disableScissor();
	}
}
