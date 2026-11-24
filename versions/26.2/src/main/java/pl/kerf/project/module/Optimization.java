package pl.kerf.project.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import pl.kerf.project.Lang;
import pl.kerf.project.config.Setting;

/**
 * Optymalizacja: opcje poprawiające FPS. Każda opcja blokuje się sama, gdy inny zainstalowany mod już robi to samo
 * (dwa mody na tej samej funkcji tylko sobie przeszkadzają); w menu pod opcją stoi wtedy powód.
 * Opcje działają tylko przy włączonym module Optimization.
 */
public final class Optimization extends Module {
	/** Nie rysuje graczy, mobów i przedmiotów schowanych za pełnymi blokami. */
	public final Setting.Bool entityCulling = this.add(new Setting.Bool("entity_culling", "Entity Culling", false)
		.lockedBy(() -> loaded("entityculling")));
	/** Nie buduje i nie rysuje fragmentów świata, które są dalej niż koniec mgły (pod wodą, w lawie, w Netherze). */
	public final Setting.Bool fogOcclusion = this.add(new Setting.Bool("fog_occlusion", "Fog Occlusion", false)
		.lockedBy(() -> loaded("sodium")));
	/** Kontekst OpenGL bez sprawdzania błędów: odrobinę szybszy, ale utrudnia szukanie przyczyn crasha. Wymaga restartu gry. */
	public final Setting.Bool noErrorContext = this.add(new Setting.Bool("no_error_context", "No Error Context (restart)", false)
		.lockedBy(() -> loaded("sodium")));

	public Optimization() {
		super("optimization", "Optimization", "Opcje poprawiające FPS. Opcja, którą robi już inny mod (np. Sodium), jest zablokowana, a pod nią widać dlaczego.", Category.MISC, true);
		this.preview = false;
	}

	/** Powód blokady, gdy zainstalowany jest mod o podanym id, inaczej null. */
	public static String loaded(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(ModContainer::getMetadata)
			.map(meta -> Lang.t("Wyłączone: ") + meta.getName() + Lang.t(" już to robi."))
			.orElse(null);
	}

	/** Czy dana opcja jest realnie aktywna: moduł włączony i opcja włączona (i niezablokowana). */
	public boolean active(Setting.Bool option) {
		return this.on() && option.on();
	}

	/**
	 * Czy okno ma dostać kontekst bez błędów. Okno powstaje, zanim moduły wczytają ustawienia, więc czytamy plik
	 * konfiguracji bezpośrednio. Zmiana działa po restarcie gry.
	 */
	public static boolean noErrorContextRequested() {
		if (loaded("sodium") != null) {
			return false;
		}

		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("aio_pvp.json");
			JsonObject module = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("optimization");
			return module != null && (!module.has("enabled") || module.get("enabled").getAsBoolean())
				&& module.has("no_error_context") && module.get("no_error_context").getAsBoolean();
		} catch (Exception e) {
			return false;
		}
	}
}
