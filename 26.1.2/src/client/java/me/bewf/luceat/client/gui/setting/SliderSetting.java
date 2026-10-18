package me.bewf.luceat.client.gui.setting;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public final class SliderSetting extends Setting {
	private final float min;
	private final float max;
	private final float step;
	private final String unit;
	private final DoubleSupplier getter;
	private final Consumer<Float> setter;

	public SliderSetting(String section, String label, String help, Cost cost, boolean rebuildsShadows, float min, float max, float step, String unit, DoubleSupplier getter, Consumer<Float> setter) {
		super(section, label, help, cost, rebuildsShadows);
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
		this.getter = getter;
		this.setter = setter;
	}

	public float min() {
		return min;
	}

	public float max() {
		return max;
	}

	public float value() {
		return (float) getter.getAsDouble();
	}

	public float fraction() {
		return (value() - min) / (max - min);
	}

	public void setFraction(float fraction) {
		float raw = min + Math.max(0.0F, Math.min(1.0F, fraction)) * (max - min);
		float snapped = Math.round(raw / step) * step;
		setter.accept(Math.max(min, Math.min(max, snapped)));
	}

	@Override
	public Object snapshot() {
		return value();
	}

	@Override
	public void restore(Object snapshot) {
		setter.accept((Float) snapshot);
	}

	@Override
	public String valueText() {
		float value = value();
		if (step >= 1.0F) {
			return String.format(Locale.ROOT, "%d%s", Math.round(value), unit);
		}
		return String.format(Locale.ROOT, "%.2f%s", value, unit);
	}
}
