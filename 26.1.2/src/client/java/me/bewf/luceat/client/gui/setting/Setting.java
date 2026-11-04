package me.bewf.luceat.client.gui.setting;

public abstract class Setting {
	private final String section;
	private final String label;
	private String help;
	private final Cost cost;
	private final boolean rebuildsShadows;
	private String name;
	private String onText;
	private String offText;
	private java.util.function.Supplier<String> blocker = () -> "";
	private java.util.function.Supplier<String> shortReason = () -> "";
	private java.util.function.Supplier<String> detail = () -> "";

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

	public String id() {
		return label;
	}

	public String label() {
		return name != null ? name : label;
	}

	@SuppressWarnings("unchecked")
	public <T extends Setting> T text(String name, String on, String off) {
		this.name = name;
		this.onText = on;
		this.offText = off;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public <T extends Setting> T blockedBy(java.util.function.Supplier<String> blocker) {
		this.blocker = blocker;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public <T extends Setting> T blockedBy(java.util.function.Supplier<String> blocker, java.util.function.Supplier<String> shortReason, java.util.function.Supplier<String> detail) {
		this.blocker = blocker;
		this.shortReason = shortReason;
		this.detail = detail;
		return (T) this;
	}

	public String blockedShort() {
		String text = shortReason.get();
		return text.isEmpty() ? blocked() : text;
	}

	public String blockedDetail() {
		String text = detail.get();
		return text.isEmpty() ? blocked() : text;
	}

	public String blocked() {
		return blocker.get();
	}

	protected String onText() {
		return onText;
	}

	protected String offText() {
		return offText;
	}

	public String describe() {
		String reason = blocked();
		return reason.isEmpty() ? help : unavailable(reason);
	}

	protected static String unavailable(String reason) {
		return "Unavailable: " + reason + (reason.endsWith(".") ? "" : ".");
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
		return label().toLowerCase().contains(lower) || label.toLowerCase().contains(lower) || help.toLowerCase().contains(lower) || section.toLowerCase().contains(lower);
	}

	public abstract String valueText();

	public boolean testable() {
		return false;
	}

	public Object snapshot() {
		return null;
	}

	public static boolean same(Object a, Object b) {
		if (a instanceof Float x && b instanceof Float y) {
			return Math.abs(x - y) < 0.0005F;
		}
		return java.util.Objects.equals(a, b);
	}

	public void useTestState(boolean high) {
	}

	public void restore(Object snapshot) {
	}
}
