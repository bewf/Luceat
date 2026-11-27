package me.bewf.luceat.client.gui.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CardSetting<E extends Enum<E>> extends Setting {
	public static final int COLUMNS = 3;
	public static final int CARD_HEIGHT = 66;
	public static final int GAP = 8;
	public static final int HEAD = 30;

	private final E[] values;
	private final String[] names;
	private final String[] notes;
	private final Supplier<E> getter;
	private final Consumer<E> setter;

	public CardSetting(String section, String label, String help, E[] values, String[] names, String[] notes, Supplier<E> getter, Consumer<E> setter) {
		super(section, label, help, Cost.NONE, false);
		this.values = values;
		this.names = names;
		this.notes = notes;
		this.getter = getter;
		this.setter = setter;
	}

	public int count() {
		return values.length;
	}

	public int index() {
		return getter.get().ordinal();
	}

	public E value(int index) {
		return values[index];
	}

	public String name(int index) {
		return names[index];
	}

	public void choose(int index) {
		setter.accept(values[index]);
	}

	public int rows() {
		return (values.length + COLUMNS - 1) / COLUMNS;
	}

	@Override
	public int rowHeight() {
		return HEAD + rows() * (CARD_HEIGHT + GAP) + 6;
	}

	@Override
	public String describe() {
		return notes[index()];
	}

	@Override
	public String valueText() {
		return names[index()];
	}

	@Override
	public Object snapshot() {
		return index();
	}

	@Override
	public void restore(Object snapshot) {
		choose((Integer) snapshot);
	}
}
