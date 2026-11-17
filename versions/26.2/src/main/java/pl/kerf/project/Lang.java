package pl.kerf.project;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import pl.kerf.project.module.Modules;

/**
 * Język menu i HUD-u moda (Settings -> Język, domyślnie angielski). Teksty w kodzie są po polsku; przy angielskim
 * zamieniamy je na tłumaczenia z assets/aio_pvp/i18n/en.json (klucz = dokładny polski tekst). Brak tłumaczenia =
 * zostaje polski, więc nic się nie psuje, najwyżej jedno słowo zostaje nieprzetłumaczone.
 */
public final class Lang {
	private static final Map<String, String> EN = load();

	private Lang() {
	}

	public static boolean english() {
		return Modules.GUI_THEME.language.is(0);
	}

	/** Tekst w wybranym języku. */
	public static String t(String polish) {
		if (polish == null || !english()) {
			return polish;
		}

		return EN.getOrDefault(polish, polish);
	}

	/** Czy dla tego tekstu jest tłumaczenie (do testu: wszystkie teksty GUI mają je mieć). */
	public static boolean has(String polish) {
		return EN.containsKey(polish);
	}

	private static Map<String, String> load() {
		Map<String, String> map = new HashMap<>();
		try (InputStream in = Lang.class.getResourceAsStream("/assets/aio_pvp/i18n/en.json")) {
			if (in != null) {
				JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsString()));
			}
		} catch (Exception e) {
			// Uszkodzony plik tłumaczeń nie może zablokować gry: zostaje polski.
			AioPvp.LOGGER.error("Nie udało się wczytać tłumaczeń", e);
		}

		return map;
	}
}
