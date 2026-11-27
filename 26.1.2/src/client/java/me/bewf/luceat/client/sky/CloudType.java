package me.bewf.luceat.client.sky;

public enum CloudType {
	FEW("Few", "A few small clouds with a lot of open sky.", 0.58F, 1.0F, 1.0F, 1.0F, 0.0F),
	CUMULUS("Cumulus", "Puffy clouds with gaps between them.", 0.0F, 1.15F, 1.0F, 1.0F, 0.0F),
	STRATUS("Stratus", "A low, flat sheet of cloud.", 0.0F, 0.5F, 0.85F, 0.88F, 0.6F),
	CIRRUS("Cirrus", "High, thin, wispy clouds.", 0.42F, 0.22F, 0.5F, 1.0F, 0.0F),
	OVERCAST("Overcast", "A thick grey ceiling with no gaps.", 0.0F, 0.9F, 1.0F, 0.78F, 0.85F),
	STORM("Storm", "Heavy dark clouds.", 0.0F, 1.6F, 1.0F, 0.5F, 0.9F);

	private final String label;
	private final String note;
	private final float cut;
	private final float thick;
	private final float alpha;
	private final float dark;
	private final float flat;

	CloudType(String label, String note, float cut, float thick, float alpha, float dark, float flat) {
		this.label = label;
		this.note = note;
		this.cut = cut;
		this.thick = thick;
		this.alpha = alpha;
		this.dark = dark;
		this.flat = flat;
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

	public float thick() {
		return thick;
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
}
