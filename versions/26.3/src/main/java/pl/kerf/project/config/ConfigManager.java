package pl.kerf.project.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import pl.kerf.project.AioPvp;
import pl.kerf.project.module.Module;
import pl.kerf.project.module.Modules;

/** Zapis i odczyt ustawień z config/aio_pvp.json. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(AioPvp.MOD_ID + ".json");

	private ConfigManager() {
	}

	public static void load() {
		if (!Files.exists(PATH)) {
			return;
		}

		try {
			JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
			migrate(root);
			for (Module module : Modules.ALL) {
				JsonElement element = root.get(module.id);
				if (element != null && element.isJsonObject()) {
					module.read(element.getAsJsonObject());
				}
			}

			// Ustawienie "zapamiętuj ryzykowne moduły" jest wczytywane razem z resztą, więc dopiero teraz wiadomo, czy je przywrócić.
			for (Module module : Modules.ALL) {
				if (module instanceof pl.kerf.project.module.RiskyModule risky) {
					risky.applyRemembered();
				}
			}
		} catch (IOException | RuntimeException e) {
			// Uszkodzony plik nie może zablokować startu gry: zostają wartości domyślne,
			// a sam plik idzie do kopii, zanim pierwszy zapis go nadpisze.
			AioPvp.LOGGER.error("Nie udało się wczytać {}, używam ustawień domyślnych (kopia: .bak)", PATH, e);
			try {
				Files.copy(PATH, PATH.resolveSibling(PATH.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException backupError) {
				AioPvp.LOGGER.error("Nie udało się zrobić kopii uszkodzonego configu", backupError);
			}
		}
	}

	/**
	 * Starsze wersje miały osobny moduł "custom_crosshair". Teraz celownik z pikseli jest częścią Crosshair Indicator,
	 * więc przenosimy narysowany celownik (jako "bez celu") i jego skalę, żeby nic nie zginęło.
	 */
	private static void migrate(JsonObject root) {
		migrateShield(root);
		migratePartyVote(root);
		if (!root.has("custom_crosshair") || !root.get("custom_crosshair").isJsonObject()) {
			return;
		}

		JsonObject old = root.getAsJsonObject("custom_crosshair");
		JsonObject crosshair = root.has("crosshair") && root.get("crosshair").isJsonObject() ? root.getAsJsonObject("crosshair") : new JsonObject();
		if (!crosshair.has("pixels") && old.has("pixels")) {
			crosshair.add("pixels", old.get("pixels"));
			if (old.has("scale")) {
				crosshair.add("scale", old.get("scale"));
			}

			if (old.has("enabled") && old.get("enabled").getAsBoolean()) {
				crosshair.addProperty("custom", true);
				crosshair.addProperty("enabled", true);
			}
		}

		root.add("crosshair", crosshair);
		root.remove("custom_crosshair");
	}

	/**
	 * Starsze wersje miały trzy moduły tarczy: "shield" (pozycja), "shield_anim_fix" i "shield_delay".
	 * Teraz to jeden moduł "shield": stare przełączniki stają się jego opcjami, a sam moduł jest włączony.
	 */
	/** Wygląd okienka głosowania przeniesiony z modułu "party_vote" do "party" (1.0.47): stare kolory i zaokrąglenie przechodzą, jeśli w party ich jeszcze nie ma. */
	private static void migratePartyVote(JsonObject root) {
		if (!root.has("party_vote") || !root.get("party_vote").isJsonObject()) {
			return;
		}

		JsonObject vote = root.getAsJsonObject("party_vote");
		JsonObject party = root.has("party") && root.get("party").isJsonObject() ? root.getAsJsonObject("party") : new JsonObject();
		String[][] keys = {{"background", "vote_background"}, {"yes_color", "vote_yes_color"}, {"no_color", "vote_no_color"}, {"rounding", "vote_rounding"}};
		boolean changed = false;
		for (String[] key : keys) {
			if (vote.has(key[0]) && !party.has(key[1])) {
				party.add(key[1], vote.get(key[0]));
				changed = true;
			}
		}

		if (changed) {
			root.add("party", party);
		}
	}

	private static void migrateShield(JsonObject root) {
		if (!root.has("shield_anim_fix") && !root.has("shield_delay")) {
			return;
		}

		JsonObject shield = root.has("shield") && root.get("shield").isJsonObject() ? root.getAsJsonObject("shield") : new JsonObject();
		if (shield.has("enabled")) {
			shield.add("position", shield.get("enabled"));
		}

		copyEnabled(root, "shield_anim_fix", shield, "anim_fix");
		copyEnabled(root, "shield_delay", shield, "delay_fix");
		shield.addProperty("enabled", true);
		root.add("shield", shield);
		root.remove("shield_anim_fix");
		root.remove("shield_delay");
	}

	private static void copyEnabled(JsonObject root, String oldId, JsonObject target, String key) {
		JsonElement old = root.get(oldId);
		if (old != null && old.isJsonObject() && old.getAsJsonObject().has("enabled")) {
			target.add(key, old.getAsJsonObject().get("enabled"));
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		// Numer formatu pliku: przyszłe migracje mają się opierać na nim, nie na wykrywaniu starych kluczy.
		root.addProperty("version", 1);
		for (Module module : Modules.ALL) {
			root.add(module.id, module.write());
		}

		try {
			Files.createDirectories(PATH.getParent());
			// Najpierw plik tymczasowy, potem podmiana: przerwany zapis nie zostawia uciętego configu.
			Path tmp = PATH.resolveSibling(PATH.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			AioPvp.LOGGER.error("Nie udało się zapisać {}", PATH, e);
		}
	}
}
