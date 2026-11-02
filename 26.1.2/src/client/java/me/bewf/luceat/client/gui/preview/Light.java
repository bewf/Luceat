package me.bewf.luceat.client.gui.preview;

public record Light(double x, double y, double z, float radius, float red, float green, float blue, Source source, boolean fire, boolean wet, boolean textured) {
	public enum Source {
		ALWAYS, HELD, ENCHANT, DROPPED, FRAME, NATURAL, PLACED, PLAYER, MOB
	}

	public static Light of(Source source, double x, double y, double z, float radius, float red, float green, float blue) {
		return new Light(x, y, z, radius, red, green, blue, source, false, false, false);
	}

	public boolean placed() {
		return source == Source.PLACED;
	}

	public Light asFire() {
		return new Light(x, y, z, radius, red, green, blue, source, true, wet, textured);
	}

	public Light asWet() {
		return new Light(x, y, z, radius, red, green, blue, source, fire, true, textured);
	}

	public Light asTextured() {
		return new Light(x, y, z, radius, red, green, blue, source, fire, wet, true);
	}

	public Light at(double nx, double ny, double nz) {
		return new Light(nx, ny, nz, radius, red, green, blue, source, fire, wet, textured);
	}

	public Light withRadius(float value) {
		return new Light(x, y, z, value, red, green, blue, source, fire, wet, textured);
	}

	public Light withColour(float r, float g, float b) {
		return new Light(x, y, z, radius, r, g, b, source, fire, wet, textured);
	}
}
