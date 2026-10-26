package me.bewf.luceat.client.light.block;

public final class BlockRule {
	public String id;
	public boolean enabled = true;
	public int level = -1;
	public int colour = -1;
	public boolean added;
	public float radius = 1.0F;
	public int flicker = -1;
	public int shadows = -1;
	public int water = -1;
	public float shine = -1.0F;
	public float heldHeight = -1.0F;
	public boolean whenPlaced = true;
	public boolean whenHeld = true;
	public boolean whenDropped = true;
	public boolean whenFramed = true;

	public BlockRule() {
	}

	public BlockRule(String id) {
		this.id = id;
	}

	public boolean edited() {
		return !enabled || level >= 0 || colour >= 0 || extras();
	}

	public boolean extras() {
		return Math.abs(radius - 1.0F) > 0.001F || flicker >= 0 || shadows >= 0 || water >= 0 || shine >= 0.0F || heldHeight >= 0.0F || !whenPlaced || !whenHeld || !whenDropped || !whenFramed;
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
