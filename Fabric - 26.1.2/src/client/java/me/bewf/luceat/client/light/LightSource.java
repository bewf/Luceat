package me.bewf.luceat.client.light;

public record LightSource(double x, double y, double z, float red, float green, float blue, float radius, boolean placed) {
	public LightSource(double x, double y, double z, float red, float green, float blue, float radius) {
		this(x, y, z, red, green, blue, radius, false);
	}
}
