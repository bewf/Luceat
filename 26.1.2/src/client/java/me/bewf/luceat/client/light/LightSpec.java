package me.bewf.luceat.client.light;

public record LightSpec(int level, float red, float green, float blue, boolean noShadow) {
	public LightSpec(int level, float red, float green, float blue) {
		this(level, red, green, blue, false);
	}

	public boolean fire() {
		return LightSource.warm(red, blue, level);
	}
}
