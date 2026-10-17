package me.bewf.luceat.client.light.block;

public final class BlockRule {
	public String id;
	public boolean enabled = true;
	public int level = -1;
	public int colour = -1;
	public boolean added;

	public BlockRule() {
	}

	public BlockRule(String id) {
		this.id = id;
	}

	public boolean edited() {
		return !enabled || level >= 0 || colour >= 0;
	}

	public boolean keep() {
		return added || edited();
	}

	public float[] rgb() {
		if (colour < 0) {
			return null;
		}
		return new float[]{((colour >> 16) & 255) / 255.0F, ((colour >> 8) & 255) / 255.0F, (colour & 255) / 255.0F};
	}
}
