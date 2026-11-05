package me.bewf.luceat.client.gui.glass;

import static me.bewf.luceat.client.gui.glass.Paint.argb;

public final class Bar {
	public static final int HEIGHT = 58;

	public record View(int x0, int y0, int x1, boolean compact, boolean tiny, String version, String preset, boolean on, float onAnim, boolean canUndo, float focus, float hoverPower, float hoverUndo, float hoverPreset, float hoverClose) {
	}

	public record Rects(int cy, int[] power, int[] undo, int[] preset, int[] close, int[] search) {
	}

	private Bar() {
	}

	public static int centre(int y0) {
		return y0 + HEIGHT / 2;
	}

	public static int textTop(int cy) {
		return cy - 4;
	}

	public static Rects layout(Surface s, View v) {
		int cy = centre(v.y0());
		int[] close = {v.x1() - 44, cy - 16, v.x1() - 12, cy + 16};
		int dividerX = close[0] - 6;
		int undoW = v.tiny() ? 14 : 12 + 5 + s.width(Surface.Role.BODY, "Undo");
		int undoX1 = dividerX - 18;
		int undoX0 = undoX1 - undoW;
		int[] undo = {undoX0 - 8, cy - 14, undoX1 + 8, cy + 14};
		int presetMax = v.compact() ? 64 : 92;
		String label = v.tiny() ? "" : Glass.fit(s, Surface.Role.BODY, v.preset(), presetMax);
		int presetW = v.tiny() ? 14 : s.width(Surface.Role.BODY, label) + 6 + 10;
		int presetX1 = undoX0 - 22;
		int presetX0 = presetX1 - presetW;
		int[] preset = {presetX0 - 8, cy - 14, presetX1 + 8, cy + 14};
		int searchX1 = presetX0 - 22;
		int searchX0 = v.tiny() ? Math.max(v.x0() + 104, searchX1 - 150) : searchX1 - (v.compact() ? 120 : 176);
		int[] search = {searchX0, cy - 13, searchX1, cy + 13};
		int toggleX = leftEnd(s, v) + 2;
		int powerW = v.tiny() ? 30 : 38 + s.width(Surface.Role.BODY, "Off");
		int[] power = {toggleX - 4, cy - 14, toggleX + powerW + 6, cy + 14};
		return new Rects(cy, power, undo, preset, close, search);
	}

	private static int leftEnd(Surface s, View v) {
		int textX = v.x0() + 84;
		int x = textX;
		if (!v.tiny()) {
			for (int i = 0; i < 6; i++) {
				x += s.width(Surface.Role.WORDMARK, "LUCEAT".substring(i, i + 1)) + 4;
			}
			x += 8;
		}
		if (!v.compact()) {
			x = Math.max(x + s.width(Surface.Role.SMALL, v.version()), textX + s.width(Surface.Role.SMALL, "Dynamic Lighting Overhaul")) + 18;
		} else if (!v.tiny()) {
			x += 8;
		}
		return x;
	}

	public static Rects draw(Surface s, View v) {
		Rects r = layout(s, v);
		int cy = r.cy();
		int textX = v.x0() + 84;
		int x = textX;
		if (!v.tiny()) {
			String word = "LUCEAT";
			for (int i = 0; i < word.length(); i++) {
				String c = word.substring(i, i + 1);
				s.text(Surface.Role.WORDMARK, c, x, v.compact() ? v.y0() + 15 : v.y0() + 9, Glass.TEXT);
				x += s.width(Surface.Role.WORDMARK, c) + 4;
			}
			x += 8;
		}
		if (!v.compact()) {
			s.text(Surface.Role.SMALL, v.version(), x, v.y0() + 18, Glass.FAINT);
			s.text(Surface.Role.SMALL, "Dynamic Lighting Overhaul", textX, v.y0() + 37, Glass.FAINT);
		}
		int toggleX = r.power()[0] + 4;
		Glass.toggle(s, toggleX, cy - 8, v.onAnim(), v.hoverPower());
		if (!v.tiny()) {
			s.text(Surface.Role.BODY, v.on() ? "On" : "Off", toggleX + 38, textTop(cy), v.on() ? Glass.GOOD : Glass.MUTED);
		}

		int[] f = r.search();
		Glass.field(s, f[0], f[1], f[2], f[3], v.focus());
		Icons.draw(s, f[0] + 10, cy - 6, 12, Icons.SEARCH, Paint.mix(Glass.MUTED, Glass.PALE, v.focus()));

		int[] p = r.preset();
		int pc = Paint.mix(Glass.SOFT, Glass.TEXT, v.hoverPreset());
		if (v.tiny()) {
			Icons.draw(s, p[0] + 8, cy - 7, 14, Icons.STAR, pc);
		} else {
			String label = Glass.fit(s, Surface.Role.BODY, v.preset(), v.compact() ? 64 : 92);
			s.text(Surface.Role.BODY, label, p[0] + 8, textTop(cy), pc);
			Icons.draw(s, p[2] - 8 - 10, cy - 5, 10, Icons.CHEVRON, Paint.mix(Glass.MUTED, Glass.SOFT, v.hoverPreset()));
		}

		int[] u = r.undo();
		int uc = v.canUndo() ? Paint.mix(Glass.SOFT, Glass.TEXT, v.hoverUndo()) : Glass.FAINT;
		if (v.tiny()) {
			Icons.draw(s, u[0] + 8, cy - 7, 14, Icons.UNDO, uc);
		} else {
			Icons.draw(s, u[0] + 8, cy - 6, 12, Icons.UNDO, uc);
			s.text(Surface.Role.BODY, "Undo", u[0] + 8 + 12 + 5, textTop(cy), uc);
		}

		int dx = r.close()[0] - 6;
		Paint.vertical(s, dx, cy - 11, dx + 1, cy, 0, argb(0, 205, 187, 255), argb(46, 205, 187, 255));
		Paint.vertical(s, dx, cy, dx + 1, cy + 11, 0, argb(46, 205, 187, 255), argb(0, 205, 187, 255));

		int[] c = r.close();
		Icons.draw(s, (c[0] + c[2]) / 2 - 8, cy - 8, 16, Icons.CLOSE, Paint.mix(Glass.SOFT, Glass.TEXT, v.hoverClose()));
		return r;
	}
}
