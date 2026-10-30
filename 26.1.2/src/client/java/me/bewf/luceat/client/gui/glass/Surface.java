package me.bewf.luceat.client.gui.glass;

public interface Surface {
	int scale();

	void fillPx(int x0, int y0, int x1, int y1, int argb);

	void clip(int x0, int y0, int x1, int y1);

	void unclip();

	void text(Role role, String text, int x, int y, int argb);

	int width(Role role, String text);

	default void fill(int x0, int y0, int x1, int y1, int argb) {
		int s = scale();
		fillPx(x0 * s, y0 * s, x1 * s, y1 * s, argb);
	}

	enum Role {
		BODY,
		TITLE,
		HEADING,
		QUOTE,
		POTATO,
		BALANCED,
		PHENOMENAL,
		HARDCORE
	}
}
