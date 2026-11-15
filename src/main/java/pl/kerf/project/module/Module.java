package pl.kerf.project.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import pl.kerf.project.config.Setting;

/** Funkcja moda z własnym przełącznikiem i listą ustawień. */
public class Module {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting<?>> settings = new ArrayList<>();
	/** Liczba kolumn na ekranie ustawień modułu. */
	public int columns = 2;
	/** Czy ekran ustawień pokazuje panel podglądu na żywo. */
	public boolean preview = true;
	/** Opcjonalny skrót przełączający funkcję (kod klawisza GLFW); -1 = brak. Domyślnie żadna funkcja nie ma skrótu. */
	public int key = -1;
	private final boolean defEnabled;
	private boolean enabled;

	public Module(String id, String name, String description, Category category, boolean defEnabled) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.defEnabled = defEnabled;
		this.enabled = defEnabled;
	}

	protected <S extends Setting<?>> S add(S setting) {
		this.settings.add(setting);
		return setting;
	}

	public boolean on() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void toggle() {
		this.enabled = !this.enabled;
	}

	public void reset() {
		this.enabled = this.defEnabled;
		this.key = -1;
		for (Setting<?> setting : this.settings) {
			setting.reset();
		}
	}

	public JsonObject write() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", this.enabled);
		json.addProperty("key", this.key);
		for (Setting<?> setting : this.settings) {
			json.add(setting.key, setting.write());
		}

		return json;
	}

	public void read(JsonObject json) {
		if (json.has("key")) {
			this.key = json.get("key").getAsInt();
		}

		if (json.has("enabled")) {
			this.enabled = json.get("enabled").getAsBoolean();
		}

		for (Setting<?> setting : this.settings) {
			JsonElement element = json.get(setting.key);
			if (element != null) {
				try {
					setting.read(element);
				} catch (RuntimeException e) {
					setting.reset();
				}
			}
		}
	}

	public enum Category {
		HUD("HUD"),
		COMBAT("Combat"),
		VISUALS("Visuals"),
		ITEMS("Items"),
		MISC("Misc");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}
}
