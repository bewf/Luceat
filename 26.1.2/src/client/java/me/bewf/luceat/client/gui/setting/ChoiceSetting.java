package me.bewf.luceat.client.gui.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ChoiceSetting<E extends Enum<E>> extends Setting {
	private final E[] values;
	private final String[] names;
	private final Supplier<E> getter;
	private final Consumer<E> setter;

	public ChoiceSetting(String section, String label, String help, Cost cost, boolean rebuildsShadows, E[] values, String[] names, Supplier<E> getter, Consumer<E> setter) {
		super(section, label, help, cost, rebuildsShadows);
		this.values = values;
		this.names = names;
		this.getter = getter;
		this.setter = setter;
	}

	public int count() {
		return values.length;
	}

	public int index() {
		return getter.get().ordinal();
	}

	public String name(int index) {
		return names[index];
	}

	public void choose(int index) {
		setter.accept(values[index]);
	}

	public void next() {
		choose((index() + 1) % values.length);
	}

	@Override
	public String valueText() {
		return names[index()];
	}

	@Override
	public boolean testable() {
		return true;
	}

	@Override
	public Object snapshot() {
		return index();
	}

	@Override
	public void useOtherState() {
		choose(index() == 0 ? values.length - 1 : 0);
	}

	@Override
	public void restore(Object snapshot) {
		choose((Integer) snapshot);
	}
}
