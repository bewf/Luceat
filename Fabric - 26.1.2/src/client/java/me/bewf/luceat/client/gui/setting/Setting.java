package me.bewf.luceat.client.gui.setting;

public abstract class Setting {
	private final String section;
	private final String label;
	private final String help;
	private final Cost cost;
	private final boolean rebuildsShadows;

	protected Setting(String section, String label, String help, Cost cost, boolean rebuildsShadows) {
		this.section = section;
		this.label = label;
		this.help = help;
		this.cost = cost;
		this.rebuildsShadows = rebuildsShadows;
	}

	public String section() {
		return section;
	}

	public String label() {
		return label;
	}

	public String help() {
		return help;
	}

	public Cost cost() {
		return cost;
	}

	public boolean rebuildsShadows() {
		return rebuildsShadows;
	}

	public boolean matches(String query) {
		String lower = query.toLowerCase();
		return label.toLowerCase().contains(lower) || help.toLowerCase().contains(lower) || section.toLowerCase().contains(lower);
	}

	public abstract String valueText();

	public boolean testable() {
		return false;
	}

	public Object snapshot() {
		return null;
	}

	public void useOtherState() {
	}

	public void restore(Object snapshot) {
	}
}
