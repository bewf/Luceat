package me.bewf.luceat.client.light;

public record LightSource(double x, double y, double z, float red, float green, float blue, float radius, boolean placed, boolean fire, long id) {
	public LightSource(double x, double y, double z, float red, float green, float blue, float radius) {
		this(x, y, z, red, green, blue, radius, false, false, 0L);
	}

	public LightSource(double x, double y, double z, float red, float green, float blue, float radius, boolean placed) {
		this(x, y, z, red, green, blue, radius, placed, false, 0L);
	}

	public static boolean warm(float red, float blue, float radius) {
		return radius >= 5.0F && red > blue + 0.35F;
	}
}
