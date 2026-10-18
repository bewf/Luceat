package me.bewf.luceat.client.gui.setting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ToggleSetting extends Setting {
	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;

	public ToggleSetting(String section, String label, String help, Cost cost, boolean rebuildsShadows, BooleanSupplier getter, Consumer<Boolean> setter) {
		super(section, label, help, cost, rebuildsShadows);
		this.getter = getter;
		this.setter = setter;
	}

	public boolean value() {
		return getter.getAsBoolean();
	}

	public void set(boolean value) {
		setter.accept(value);
	}

	@Override
	public String valueText() {
		return value() ? "On" : "Off";
	}

	@Override
	public boolean testable() {
		return cost() != Cost.NONE;
	}

	@Override
	public Object snapshot() {
		return value();
	}

	@Override
	public void useTestState(boolean high) {
		set(high);
	}

	@Override
	public void restore(Object snapshot) {
		set((Boolean) snapshot);
	}
}
