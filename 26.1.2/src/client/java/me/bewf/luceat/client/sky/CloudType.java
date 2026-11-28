package me.bewf.luceat.client.sky;

public enum CloudType {
	FEW("Few", "A few small clouds with a lot of open sky.", 0.6F, 30.0F, -6.0F, 1.0F, 1.0F, 0.05F, 0, 0.6F),
	CUMULUS("Cumulus", "Puffy clouds with gaps between them.", 0.36F, 62.0F, -20.0F, 1.0F, 1.0F, 0.0F, 0, 0.62F),
	STRATUS("Stratus", "A low, flat sheet of cloud.", 0.0F, 12.0F, -55.0F, 0.85F, 0.88F, 0.6F, 0, 0.3F),
	CIRRUS("Cirrus", "High, thin, wispy clouds.", 0.46F, 7.0F, 95.0F, 0.5F, 1.0F, 0.0F, 4, 0.25F),
	OVERCAST("Overcast", "A thick grey ceiling with no gaps.", 0.0F, 26.0F, 4.0F, 1.0F, 0.78F, 0.85F, 0, 0.35F),
	STORM("Storm", "Heavy dark clouds.", 0.0F, 55.0F, -38.0F, 1.0F, 0.5F, 0.85F, 0, 0.5F),
	ALTOCUMULUS("Altocumulus", "Patches of small rounded clouds with sky between them.", 0.5F, 16.0F, 45.0F, 0.8F, 0.97F, 0.0F, 1, 0.5F),
	CIRROCUMULUS("Cirrocumulus", "High, thin rows of tiny ripples.", 0.3F, 6.0F, 105.0F, 0.7F, 1.0F, 0.0F, 2, 0.25F),
	NIMBOSTRATUS("Nimbostratus", "A thick dark grey sheet that brings steady rain.", 0.0F, 38.0F, -42.0F, 1.0F, 0.6F, 0.95F, 0, 0.3F),
	CUMULONIMBUS("Cumulonimbus", "Tall towering clouds with a flat spread top.", 0.5F, 170.0F, -38.0F, 1.0F, 0.92F, 0.0F, 3, 0.55F);

	private final String label;
	private final String note;
	private final float cut;
	private final float depth;
	private final float lift;
	private final float alpha;
	private final float dark;
	private final float flat;
	private final int form;
	private final float erode;

	CloudType(String label, String note, float cut, float depth, float lift, float alpha, float dark, float flat, int form, float erode) {
		this.label = label;
		this.note = note;
		this.cut = cut;
		this.depth = depth;
		this.lift = lift;
		this.alpha = alpha;
		this.dark = dark;
		this.flat = flat;
		this.form = form;
		this.erode = erode;
	}

	public String label() {
		return label;
	}

	public String note() {
		return note;
	}

	public float cut() {
		return cut;
	}

	public float depth() {
		return depth;
	}

	public float lift() {
		return lift;
	}

	public float alpha() {
		return alpha;
	}

	public float dark() {
		return dark;
	}

	public float flat() {
		return flat;
	}

	public int form() {
		return form;
	}

	public float erode() {
		return erode;
	}
}
