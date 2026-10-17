package me.bewf.luceat.client.compat.oneconfig;

import me.bewf.luceat.client.config.LuceatConfig;

import org.polyfrost.oneconfig.api.toggle.v1.ModToggle;

public class LuceatModToggle implements ModToggle {
	@Override
	public boolean isEnabled() {
		return LuceatConfig.get().enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		LuceatConfig.get().enabled = enabled;
		LuceatConfig.save();
	}
}
