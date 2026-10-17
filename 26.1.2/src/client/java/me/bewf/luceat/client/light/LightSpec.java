package me.bewf.luceat.client.light;

public record LightSpec(int level, float red, float green, float blue) {
	public boolean fire() {
		return LightSource.warm(red, blue, level);
	}
}
