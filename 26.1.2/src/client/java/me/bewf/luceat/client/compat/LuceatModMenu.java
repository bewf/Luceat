package me.bewf.luceat.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.bewf.luceat.client.gui.LuceatScreen;

public final class LuceatModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LuceatScreen::new;
	}
}
