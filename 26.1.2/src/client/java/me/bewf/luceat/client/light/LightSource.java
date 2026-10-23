package me.bewf.luceat.client.light;

public record LightSource(double x, double y, double z, float red, float green, float blue, float radius, boolean placed, boolean fire, long id, boolean noShadow) {
	public LightSource(double x, double y, double z, float red, float green, float blue, float radius, boolean placed, boolean fire, long id) {
		this(x, y, z, red, green, blue, radius, placed, fire, id, false);
	}

	public LightSource(double x, double y, double z, float red, float green, float blue, float radius) {
		this(x, y, z, red, green, blue, radius, false, false, 0L, false);
	}

	public LightSource(double x, double y, double z, float red, float green, float blue, float radius, boolean placed) {
		this(x, y, z, red, green, blue, radius, placed, false, 0L, false);
	}

	public static boolean warm(float red, float blue, float radius) {
		return radius >= 5.0F && red > blue + 0.35F;
	}
}
