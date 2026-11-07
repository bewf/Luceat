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
	private record Cmd(Role role, Component text, int x, int y, int argb, int cx0, int cy0, int cx1, int cy1, boolean clipped, int width, int order) {
	}

	private record Veil(int x0, int y0, int x1, int y1, int order) {
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
	private final List<Veil> veils = new ArrayList<>();
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
		veils.clear();
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

	public void blitAt(GuiGraphicsExtractor graphics, int x, int y) {
		if (ready) {
			graphics.blit(id, x, y, x + w, y + h, 0.0F, 1.0F, 0.0F, 1.0F);
		}
	}

	public void blitPart(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
		if (ready) {
			graphics.blit(id, x0, y0, x1, y1, u0, u1, v0, v1);
		}
	}

	public void drawTexts(GuiGraphicsExtractor graphics) {
		net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
		for (Cmd cmd : texts) {
			int up = padTop(cmd.role);
			int down = padBottom(cmd.role);
			if (hide != null && cmd.x < hide[2] && cmd.x + cmd.width > hide[0] && cmd.y - up < hide[3] && cmd.y + down > hide[1]) {
				continue;
			}
			int bx0 = cmd.clipped ? Math.max(cmd.cx0, cmd.x - 3) : cmd.x - 3;
			int by0 = cmd.clipped ? Math.max(cmd.cy0, cmd.y - up) : cmd.y - up;
			int bx1 = cmd.clipped ? Math.min(cmd.cx1, cmd.x + cmd.width + 5) : cmd.x + cmd.width + 5;
			int by1 = cmd.clipped ? Math.min(cmd.cy1, cmd.y + down) : cmd.y + down;
			List<int[]> pieces = new ArrayList<>();
			pieces.add(new int[] {bx0, by0, bx1, by1});
			for (Veil veil : veils) {
				if (veil.order <= cmd.order || pieces.isEmpty()) {
					continue;
				}
				List<int[]> next = new ArrayList<>();
				for (int[] p : pieces) {
					if (veil.x1 <= p[0] || veil.x0 >= p[2] || veil.y1 <= p[1] || veil.y0 >= p[3]) {
						next.add(p);
						continue;
					}
					if (veil.y0 > p[1]) {
						next.add(new int[] {p[0], p[1], p[2], veil.y0});
					}
					if (veil.y1 < p[3]) {
						next.add(new int[] {p[0], veil.y1, p[2], p[3]});
					}
					int my0 = Math.max(p[1], veil.y0);
					int my1 = Math.min(p[3], veil.y1);
					if (veil.x0 > p[0]) {
						next.add(new int[] {p[0], my0, veil.x0, my1});
					}
					if (veil.x1 < p[2]) {
						next.add(new int[] {veil.x1, my0, p[2], my1});
					}
				}
				pieces = next;
			}
			for (int[] p : pieces) {
				if (p[2] <= p[0] || p[3] <= p[1]) {
					continue;
				}
				graphics.enableScissor(p[0], p[1], p[2], p[3]);
				graphics.text(font, cmd.text.getVisualOrderText(), cmd.x, cmd.y, cmd.argb, false);
				graphics.disableScissor();
			}
		}
	}

	private static int padTop(Role role) {
		return switch (role) {
			case WORDMARK -> 14;
			case HEADING -> 8;
			case TITLE, QUOTE, POTATO, BALANCED, PHENOMENAL, HARDCORE -> 6;
			default -> 4;
		};
	}

	private static int padBottom(Role role) {
		return switch (role) {
			case WORDMARK -> 30;
			case HEADING -> 20;
			case TITLE, QUOTE, POTATO, BALANCED, PHENOMENAL, HARDCORE -> 16;
			default -> 14;
		};
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
	public void frost(int lx0, int ly0, int lx1, int ly1, int inner, int edge, int edgeWidth) {
		int ax0 = Math.max(0, Math.max(lx0 * scale - ox * scale, clipX0));
		int ay0 = Math.max(0, Math.max(ly0 * scale - oy * scale, clipY0));
		int ax1 = Math.min(pw, Math.min(lx1 * scale - ox * scale, clipX1));
		int ay1 = Math.min(ph, Math.min(ly1 * scale - oy * scale, clipY1));
		if (ax1 - ax0 < 8 || ay1 - ay0 < 8) {
			return;
		}
		veils.add(new Veil(lx0, ly0, lx1, ly1, texts.size() + veils.size()));
		int f = Math.max(2, scale * 2);
		int cw = (ax1 - ax0 + f - 1) / f;
		int ch = (ay1 - ay0 + f - 1) / f;
		float[][] small = new float[4][cw * ch];
		float[][] sharp = new float[4][cw * ch];
		for (int cy = 0; cy < ch; cy++) {
			for (int cx = 0; cx < cw; cx++) {
				float sa = 0.0F;
				float sr = 0.0F;
				float sg = 0.0F;
				float sb = 0.0F;
				int count = 0;
				for (int dy = 0; dy < f; dy++) {
					int y = ay0 + cy * f + dy;
					if (y >= ay1) {
						break;
					}
					for (int dx = 0; dx < f; dx++) {
						int x = ax0 + cx * f + dx;
						if (x >= ax1) {
							break;
						}
						int p = pixels[y * pw + x];
						float a = (p >>> 24) / 255.0F;
						sa += a;
						sr += ((p >> 16) & 255) * a;
						sg += ((p >> 8) & 255) * a;
						sb += (p & 255) * a;
						count++;
					}
				}
				float inv = count == 0 ? 0.0F : 1.0F / count;
				int i = cy * cw + cx;
				sharp[0][i] = sa * inv;
				sharp[1][i] = sr * inv;
				sharp[2][i] = sg * inv;
				sharp[3][i] = sb * inv;
			}
		}
		int innerR = Math.max(1, inner * scale / f);
		int edgeR = Math.max(innerR + 1, edge * scale / f);
		for (int c = 0; c < 4; c++) {
			small[c] = blur(sharp[c], cw, ch, innerR);
		}
		float[][] wide = new float[4][];
		for (int c = 0; c < 4; c++) {
			wide[c] = blur(sharp[c], cw, ch, edgeR);
		}
		float edgePx = Math.max(1.0F, edgeWidth * scale);
		for (int y = ay0; y < ay1; y++) {
			for (int x = ax0; x < ax1; x++) {
				float fx = (x - ax0 + 0.5F) / f - 0.5F;
				float fy = (y - ay0 + 0.5F) / f - 0.5F;
				float t = 1.0F - Math.min(1.0F, Math.min(Math.min(x - ax0, ax1 - 1 - x), Math.min(y - ay0, ay1 - 1 - y)) / edgePx);
				t = t * t;
				float a = lerp(sample(small[0], cw, ch, fx, fy), sample(wide[0], cw, ch, fx, fy), t);
				if (a <= 0.003F) {
					pixels[y * pw + x] = 0;
					continue;
				}
				float r = lerp(sample(small[1], cw, ch, fx, fy), sample(wide[1], cw, ch, fx, fy), t) / a;
				float g = lerp(sample(small[2], cw, ch, fx, fy), sample(wide[2], cw, ch, fx, fy), t) / a;
				float b = lerp(sample(small[3], cw, ch, fx, fy), sample(wide[3], cw, ch, fx, fy), t) / a;
				int ia = Math.min(255, Math.round(a * 255.0F));
				pixels[y * pw + x] = ia << 24 | Math.min(255, Math.round(r)) << 16 | Math.min(255, Math.round(g)) << 8 | Math.min(255, Math.round(b));
			}
		}
		minRow = Math.min(minRow, ay0);
		maxRow = Math.max(maxRow, ay1 - 1);
	}

	@Override
	public void lens(int lx0, int ly0, int lx1, int ly1, int rTop, int rBottom, Optics.Look look, float strength) {
		int x0 = lx0 * scale - ox * scale;
		int y0 = ly0 * scale - oy * scale;
		int x1 = lx1 * scale - ox * scale;
		int y1 = ly1 * scale - oy * scale;
		veils.add(new Veil(lx0, ly0, lx1, ly1, texts.size() + veils.size()));
		Optics.lens(pixels, pw, ph, x0, y0, x1, y1, rTop * scale, rBottom * scale, scale, look, strength, clipX0, clipY0, clipX1, clipY1);
		minRow = Math.min(minRow, Math.max(Math.max(0, y0), clipY0));
		maxRow = Math.max(maxRow, Math.min(Math.min(ph - 1, y1 - 1), clipY1 - 1));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float sample(float[] data, int w, int h, float x, float y) {
		int x0 = Math.max(0, Math.min(w - 1, (int) Math.floor(x)));
		int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(y)));
		int x1 = Math.min(w - 1, x0 + 1);
		int y1 = Math.min(h - 1, y0 + 1);
		float tx = Math.max(0.0F, Math.min(1.0F, x - x0));
		float ty = Math.max(0.0F, Math.min(1.0F, y - y0));
		float top = lerp(data[y0 * w + x0], data[y0 * w + x1], tx);
		float bottom = lerp(data[y1 * w + x0], data[y1 * w + x1], tx);
		return lerp(top, bottom, ty);
	}

	private static float[] blur(float[] source, int w, int h, int radius) {
		float[] current = source;
		for (int pass = 0; pass < 2; pass++) {
			float[] across = new float[w * h];
			for (int y = 0; y < h; y++) {
				float sum = 0.0F;
				for (int x = -radius; x <= radius; x++) {
					sum += current[y * w + Math.max(0, Math.min(w - 1, x))];
				}
				for (int x = 0; x < w; x++) {
					across[y * w + x] = sum / (radius * 2 + 1);
					sum += current[y * w + Math.min(w - 1, x + radius + 1)] - current[y * w + Math.max(0, x - radius)];
				}
			}
			float[] down = new float[w * h];
			for (int x = 0; x < w; x++) {
				float sum = 0.0F;
				for (int y = -radius; y <= radius; y++) {
					sum += across[Math.max(0, Math.min(h - 1, y)) * w + x];
				}
				for (int y = 0; y < h; y++) {
					down[y * w + x] = sum / (radius * 2 + 1);
					sum += across[Math.min(h - 1, y + radius + 1) * w + x] - across[Math.max(0, y - radius) * w + x];
				}
			}
			current = down;
		}
		return current;
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
		if (clipped && (x >= clipRect[2] || x + width <= clipRect[0] || y - padTop(role) >= clipRect[3] || y + padBottom(role) <= clipRect[1])) {
			return;
		}
		texts.add(new Cmd(role, component, x, y + drop(role), argb, clipRect[0], clipRect[1], clipRect[2], clipRect[3], clipped, width, texts.size() + veils.size()));
	}

	private static int drop(Role role) {
		return switch (role) {
			case WORDMARK -> 11;
			case HEADING -> 4;
			case TITLE -> 1;
			default -> 0;
		};
	}

	@Override
	public void veil(int lx0, int ly0, int lx1, int ly1) {
		veils.add(new Veil(lx0, ly0, lx1, ly1, texts.size() + veils.size()));
	}

	@Override
	public int width(Role role, String text) {
		return Texts.width(role, text);
	}
}
