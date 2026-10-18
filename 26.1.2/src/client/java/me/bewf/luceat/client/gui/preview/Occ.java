package me.bewf.luceat.client.gui.preview;

public final class Occ {
	public static final int NONE = 0;
	public static final int OPAQUE = 1;
	public static final int PATTERN = 2;
	public static final int SHAPE = 3;

	public static final Occ EMPTY = new Occ(NONE, null);
	public static final Occ SOLID = new Occ(OPAQUE, null);

	public final int kind;
	public final float[] data;

	public Occ(int kind, float[] data) {
		this.kind = kind;
		this.data = data;
	}
}
