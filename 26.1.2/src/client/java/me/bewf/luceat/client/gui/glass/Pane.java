package me.bewf.luceat.client.gui.glass;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import me.bewf.luceat.Luceat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class Pane implements Surface {
	private record Cmd(Role role, Component text, int x, int y, int argb, int cx0, int cy0, int cx1, int cy1, boolean clipped, int width) {
	}

	private static int serial;

	private final Identifier id = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "glass/pane" + (serial++));
	private DynamicTexture texture;
	private int[] pixels = new int[0];
	private int ox;
	private int oy;
	private int w;
	private int h;
	private int scale = 1;
	private int pw;
	private int ph;
	private int clipX0;
	private int clipY0;
	private int clipX1;
	private int clipY1;
	private boolean clipped;
	private boolean ready;
	private final List<Cmd> texts = new ArrayList<>();
	private int[] hide;
	private int minRow;
	private int maxRow;
	private int prevMin;
	private int prevMax;

	public boolean ready() {
		return ready;
	}

	public boolean matches(int x, int y, int width, int height, int guiScale) {
		return ready && ox == x && oy == y && w == width && h == height && scale == guiScale;
	}

	public void begin(int x, int y, int width, int height, int guiScale) {
		boolean resized = width * guiScale != pw || height * guiScale != ph;
		ox = x;
		oy = y;
		w = width;
		h = height;
		scale = guiScale;
		pw = width * guiScale;
		ph = height * guiScale;
		if (resized || texture == null) {
			pixels = new int[pw * ph];
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(id);
			}
			texture = new DynamicTexture("luceat pane", pw, ph, false);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		} else {
			java.util.Arrays.fill(pixels, 0);
		}
		prevMin = resized ? 0 : minRow;
		prevMax = resized ? ph - 1 : maxRow;
		minRow = ph;
		maxRow = -1;
		texts.clear();
		clipped = false;
		clipX0 = 0;
		clipY0 = 0;
		clipX1 = pw;
		clipY1 = ph;
		ready = true;
	}

	public void finish() {
		NativeImage image = texture.getPixels();
		int from = Math.max(0, Math.min(prevMin, minRow));
		int to = Math.min(ph - 1, Math.max(prevMax, maxRow));
		for (int y = from; y <= to; y++) {
			int row = y * pw;
			for (int x = 0; x < pw; x++) {
				image.setPixel(x, y, pixels[row + x]);
			}
		}
		texture.upload();
	}

	public void hideText(int x0, int y0, int x1, int y1) {
		hide = new int[] {x0, y0, x1, y1};
	}

	public void showAllText() {
		hide = null;
	}

	public void draw(GuiGraphicsExtractor graphics) {
		if (!ready) {
			return;
		}
		graphics.blit(id, ox, oy, ox + w, oy + h, 0.0F, 1.0F, 0.0F, 1.0F);
		drawTexts(graphics);
	}

	public void drawTexts(GuiGraphicsExtractor graphics) {
		net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
		for (Cmd cmd : texts) {
			if (hide != null && cmd.x < hide[2] && cmd.x + cmd.width > hide[0] && cmd.y < hide[3] && cmd.y + 10 > hide[1]) {
				continue;
			}
			if (cmd.clipped) {
				graphics.enableScissor(cmd.cx0, cmd.cy0, cmd.cx1, cmd.cy1);
			}
			graphics.text(font, cmd.text.getVisualOrderText(), cmd.x, cmd.y, cmd.argb, false);
			if (cmd.clipped) {
				graphics.disableScissor();
			}
		}
	}

	public void release() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(id);
			texture = null;
		}
		ready = false;
		pixels = new int[0];
		pw = 0;
		ph = 0;
	}

	@Override
	public int scale() {
		return scale;
	}

	@Override
	public void fillPx(int x0, int y0, int x1, int y1, int argb) {
		int a = argb >>> 24;
		if (a == 0) {
			return;
		}
		int ax0 = Math.max(x0 - ox * scale, Math.max(0, clipX0));
		int ay0 = Math.max(y0 - oy * scale, Math.max(0, clipY0));
		int ax1 = Math.min(x1 - ox * scale, Math.min(pw, clipX1));
		int ay1 = Math.min(y1 - oy * scale, Math.min(ph, clipY1));
		if (ax1 <= ax0 || ay1 <= ay0) {
			return;
		}
		minRow = Math.min(minRow, ay0);
		maxRow = Math.max(maxRow, ay1 - 1);
		int sr = (argb >> 16) & 255;
		int sg = (argb >> 8) & 255;
		int sb = argb & 255;
		for (int y = ay0; y < ay1; y++) {
			int row = y * pw;
			if (a == 255) {
				java.util.Arrays.fill(pixels, row + ax0, row + ax1, argb);
				continue;
			}
			for (int x = ax0; x < ax1; x++) {
				int d = pixels[row + x];
				int da = d >>> 24;
				if (da == 0) {
					pixels[row + x] = argb;
					continue;
				}
				int ia = 255 - a;
				int oa = a + (da * ia + 127) / 255;
				int dr = (d >> 16) & 255;
				int dg = (d >> 8) & 255;
				int db = d & 255;
				int wd = da * ia;
				int ws = a * 255;
				int total = ws + wd;
				int r = (sr * ws + dr * wd + total / 2) / total;
				int g = (sg * ws + dg * wd + total / 2) / total;
				int b = (sb * ws + db * wd + total / 2) / total;
				pixels[row + x] = oa << 24 | r << 16 | g << 8 | b;
			}
		}
	}

	@Override
	public void clip(int x0, int y0, int x1, int y1) {
		clipX0 = x0 * scale - ox * scale;
		clipY0 = y0 * scale - oy * scale;
		clipX1 = x1 * scale - ox * scale;
		clipY1 = y1 * scale - oy * scale;
		clipped = true;
		clipRect[0] = x0;
		clipRect[1] = y0;
		clipRect[2] = x1;
		clipRect[3] = y1;
	}

	private final int[] clipRect = new int[4];

	@Override
	public void unclip() {
		clipX0 = 0;
		clipY0 = 0;
		clipX1 = pw;
		clipY1 = ph;
		clipped = false;
	}

	@Override
	public void text(Role role, String text, int x, int y, int argb) {
		Component component = Texts.of(role, text);
		int width = Texts.width(role, text);
		if (clipped && (x >= clipRect[2] || x + width <= clipRect[0] || y >= clipRect[3] || y + 10 <= clipRect[1])) {
			return;
		}
		texts.add(new Cmd(role, component, x, y, argb, clipRect[0], clipRect[1], clipRect[2], clipRect[3], clipped, width));
	}

	@Override
	public int width(Role role, String text) {
		return Texts.width(role, text);
	}
}
