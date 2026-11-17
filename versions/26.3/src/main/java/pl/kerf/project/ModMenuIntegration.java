package pl.kerf.project;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import pl.kerf.project.gui.SettingsScreen;

/** Przycisk konfiguracji w Mod Menu: kliknięcie moda na liście otwiera jego ustawienia. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
